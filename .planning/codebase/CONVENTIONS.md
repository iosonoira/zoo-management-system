---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# Coding Conventions

**Analysis Date:** 2026-09-19

**Scope:** `zms-be/` only. The only module with code is `zms-be/animal-service/`; `feeding-service/`, `health-service/`, `notification-service/` are empty directories. Treat `animal-service` as the reference implementation for every new service. The binding rulebook is `zms-be/CLAUDE.md` (Italian) — read it before touching backend code.

## Naming Patterns

**Files / Classes (one public type per file, file name = type name):**

| Artifact | Pattern | Example |
|---|---|---|
| Use case interface | `{Verb}{Entity}UseCase` | `domain/port/in/RegisterAnimalUseCase.java` |
| Command record | `{Verb}{Entity}Command` | `domain/port/in/RegisterAnimalCommand.java` |
| Application service | `{Verb}{Entity}Service` | `application/TransferAnimalService.java` |
| Outbound port | `{Entity}Repository` | `domain/port/out/AnimalRepository.java` |
| JPA entity | `{Entity}Entity` | `infrastructure/persistence/AnimalEntity.java` |
| Persistence adapter | `{Entity}PanacheRepository` | `infrastructure/persistence/AnimalPanacheRepository.java` |
| Entity<->domain mapper | `{Entity}EntityMapper` (static, hand-written) | `infrastructure/persistence/AnimalEntityMapper.java` |
| REST resource | `{Entity}Resource` | `infrastructure/rest/AnimalResource.java` |
| Request DTO | `{Verb}{Entity}Request` | `infrastructure/rest/dto/RegisterAnimalRequest.java` |
| Response DTO | `{Entity}Response` | `infrastructure/rest/dto/AnimalResponse.java` |
| DTO mapper (MapStruct) | `{Entity}DtoMapper` | `infrastructure/rest/mapper/AnimalDtoMapper.java` |
| Exception mapper | `{Domain}ExceptionMapper` | `infrastructure/rest/ZooExceptionMapper.java`, `SecurityExceptionMapper.java` |
| Domain exception | `{Entity}NotFoundException`, `Invalid{Thing}Exception` | `domain/exception/InvalidStatusTransitionException.java` |

All paths above are relative to `zms-be/animal-service/src/main/java/it/zoo/animal/`.

**Packages:** `it.zoo.{service}.{layer}...` — e.g. `it.zoo.animal.domain.port.in`. A new service uses `it.zoo.health`, `it.zoo.feeding`, etc.

**Methods:** camelCase verbs. Use case methods are named after the operation: `register(cmd)`, `getById(id)`, `listAll()`, `updateStatus(id, status, performedBy)`, `transfer(id, targetEnclosureId, performedBy)`. Domain predicates use `can...`: `Animal.canTransitionTo(...)`, `Animal.canBeTransferred()`.

**Variables/fields:** camelCase; injected collaborators are `private final` (`repository`, `mapper`, `identity`). Constants are `UPPER_SNAKE_CASE` (`ZooRoles.ADMIN`).

**Enums:** `UPPER_SNAKE_CASE` values, stored in `domain/enums/` (never nested in models): `AnimalStatus` (`HEALTHY`, `UNDER_OBSERVATION`, `IN_TREATMENT`, `DECEASED`), `Habitat`.

**Role strings:** kebab-case realm roles, referenced only via constants in `infrastructure/security/ZooRoles.java` (`zoo-admin`, `zoo-vet`, `zoo-keeper`).

## Code Style

**Formatting:**

- No formatter/linter configured (no Checkstyle, Spotless, `.editorconfig` under `zms-be/`).
- 4-space indentation; continuation lines indented 8 spaces (`.orElseThrow(...)`, builder chains).
- Opening brace on same line. Trivial getters/setters on a single line: `public UUID getId() { return id; }` (`domain/model/Animal.java`).
- Blank line after class declaration before the first field.
- Java 21, `UTF-8` source encoding (`zms-be/pom.xml`).

**Linting:** Not detected. Compliance with layer rules is enforced only by `zms-be/CLAUDE.md` review.

## Architecture Rules That Affect Code Style

Hexagonal: `infrastructure -> application -> domain`.

- `domain/`: ZERO framework annotations or Jakarta/Quarkus imports. Plain classes with no-arg + all-args constructors, getters/setters, and pure domain logic.
- `application/`: only `@ApplicationScoped` (class) and `@Transactional` (method). One class per use case, implementing exactly one `*UseCase` interface. Knows only `port/in` and `port/out`.
- `infrastructure/`: JPA, JAX-RS, Bean Validation, MapStruct, security.

**Constructor injection only — never `@Inject` on fields in main code:**

```java
@ApplicationScoped
public class TransferAnimalService implements TransferAnimalUseCase {

    private final AnimalRepository repository;

    public TransferAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }
```

**`@Transactional` on write methods only, never on the class; read-only methods carry no annotation:**

```java
@Override
@Transactional
public Animal transfer(UUID animalId, UUID targetEnclosureId, String performedBy) { ... }
```

**Records for immutable data carriers:** commands (`RegisterAnimalCommand`), request/response DTOs, nested `ZooExceptionMapper.ErrorResponse`.

**Persistence adapter:** `AnimalPanacheRepository` implements the domain port using an injected `EntityManager` (`em.merge`, `em.find`, JPQL) and converts via static `AnimalEntityMapper.toDomain/toEntity/toDomainList`. Note: `zms-be/CLAUDE.md` states the adapter should `implements PanacheRepository<E>`; the actual code uses plain `EntityManager`. Follow the existing code unless told otherwise. Never use Active Record (`extends PanacheEntity`). Never leak `AnimalEntity` outside `infrastructure/persistence/`.

**Static utility classes:** `final`-style with private constructor (`ZooRoles`, `AnimalEntityMapper`).

## Import Organization

**Order (observed):**

1. Project imports `it.zoo...` and third-party (`io.quarkus...`, `jakarta...`, `org...`) in one alphabetical block
2. Blank line
3. `java.*` imports
4. Blank line
5. `import static ...` (tests)

Example: `application/TransferAnimalService.java`, `infrastructure/rest/AnimalResource.java`. Wildcards are used occasionally (`it.zoo.animal.domain.port.in.*`, `jakarta.ws.rs.*`, `jakarta.persistence.*`, `static org.junit.jupiter.api.Assertions.*`). Domain files sometimes omit the blank line before `java.*` (`domain/model/Animal.java`).

**Path Aliases:** Not applicable (Java).

## Error Handling

**Patterns:**

- Domain exceptions extend `RuntimeException` directly, no annotations, message built in the constructor:

```java
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(AnimalStatus from, AnimalStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
```

- Application-layer validation is explicit `if` + `throw`, never Bean Validation:

```java
if (performedBy == null || performedBy.isBlank()) {
    throw new InvalidAnimalDataException("Actor must not be blank");
}
```

- Lookups use `Optional` + `orElseThrow`: `repository.findById(id).orElseThrow(() -> new AnimalNotFoundException(id));`
- State rules live on the model (`animal.canTransitionTo(target)`, `animal.canBeTransferred()`); services call them and throw.
- HTTP mapping is centralized in `infrastructure/rest/ZooExceptionMapper.java` (`ExceptionMapper<RuntimeException>`): `AnimalNotFoundException` -> 404, `InvalidAnimalDataException` -> 400, `InvalidStatusTransitionException` -> 422, anything else -> 500 `"Internal server error"`. When adding a domain exception, add an `instanceof` branch here.
- Security errors: `infrastructure/rest/SecurityExceptionMapper.java` maps to 401 `"Authentication required"` / 403 `"Insufficient role"`.
- Every error body is `{"message": "..."}` via `ZooExceptionMapper.ErrorResponse`.
- Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`) only on DTOs in `infrastructure/rest/dto/` and resource parameters.

## Logging

**Framework:** None used in code. No `Logger`, `Log`, or `System.out` in `zms-be/animal-service/src/main`. JBoss LogManager is configured only as the test logging manager in `animal-service/pom.xml`.

**Patterns:** Not established. If needed, use Quarkus `io.quarkus.logging.Log` in `infrastructure/` only — never in `domain/`.

## Comments

**When to Comment:** Code is essentially comment-free; clarity comes from naming. Rationale and rules are documented in `zms-be/CLAUDE.md`, not inline.

**JavaDoc:** Not used.

## Function Design

**Size:** Short — service methods under ~20 lines: validate, load, check domain rule, mutate, save.

**Parameters:** Multi-field create input goes through a Command record (`RegisterAnimalCommand`); simple operations take primitives/UUIDs directly. Write use cases always take a trailing `String performedBy` (actor), extracted in the resource from `SecurityIdentity` via `currentActor()` — the token never leaves `infrastructure`.

**Return Values:** Use cases return domain objects (`Animal`, `List<Animal>`); resources map to DTOs via `AnimalDtoMapper`. `POST` returns `Response` with 201; other endpoints return the DTO directly. Repository `findById` returns `Optional`.

## Module Design

**Exports:** Public classes, package-per-layer. REST resources depend on `*UseCase` interfaces, never on `*Service` classes.

**Barrel Files:** Not applicable.

**Build:** Versions live only in `zms-be/pom.xml` (Quarkus BOM). Child POMs must not pin versions (exception present: `mapstruct.version` 1.5.5.Final in `animal-service/pom.xml`). Use per-service `mvnw` / `mvnw.cmd`.

**Config:** `application.properties` uses Quarkus profiles (`%dev.`, `%test.`). OIDC disabled by default, enabled in `%dev` against Keycloak on port 8081. Schema managed by Flyway (`src/main/resources/db/migration/V{n}__{description}.sql`), `hibernate-orm.database.generation=none`.

---

*Convention analysis: 2026-09-19*
