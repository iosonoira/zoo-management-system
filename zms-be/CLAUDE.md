# CLAUDE.md — Zoo Management System

Binding project rules for any AI agent working on this codebase.  
Read this file in full before touching any file.

---

## Project overview

**Zoo Management System** (`zms-be`) is a zoo management system made of 4 Quarkus microservices in a Maven mono-repo.

```
zoo-management-system/
├── zms-be/
│   ├── pom.xml                  ← parent POM (manages ALL versions)
│   ├── animal-service/          ← Core: animal registry
│   ├── health-service/          ← Medical records
│   ├── feeding-service/         ← Feeding plans
│   ├── notification-service/    ← Kafka consumer for notifications
│   └── infrastructure/
│       ├── docker-compose.yml
│       └── keycloak/
└── docs/
    ├── STATE.md                 ← Implemented / Decided, not built / Open
    ├── decisions.md             ← decision log (append-only)
    └── events.md                ← event contracts and delivery semantics
```

The state of each service lives in [`docs/STATE.md`](../docs/STATE.md), not in this file.

**Stack**: Java 21, Quarkus 3.20.0, Maven multi-module, PostgreSQL, Kafka, Keycloak.  
**Environment**: Windows 11, PowerShell. Java via JBang. Maven in `C:\tools\apache-maven`.

---

## Architecture: Hexagonal (Ports & Adapters)

### Package structure: `animal-service` as the reference

```
src/main/java/it/zoo/animal/
├── domain/
│   ├── model/          ← Plain POJOs, ZERO framework annotations
│   ├── enums/          ← Domain enums, ZERO framework annotations
│   ├── port/
│   │   ├── in/         ← Use Case interfaces + Command records
│   │   └── out/        ← Repository / Event Port interfaces
│   └── exception/      ← extends RuntimeException, ZERO framework annotations
│
├── application/        ← Use Case implementations
│   └── {Name}Service.java
│
└── infrastructure/
    ├── persistence/    ← JPA adapter (entity, panache repository)
    ├── rest/           ← REST adapter (resource, DTO, mapper, exception handler)
    └── event/          ← Kafka adapter (producer, consumer)
```

### Dependency rule: NEVER VIOLATE

```
infrastructure → application → domain
```

- `domain` depends on nothing
- `application` depends only on `domain`
- `infrastructure` depends on `application` and `domain`
- **No layer depends on a layer outside itself**

---

## Rules per layer

### domain/: absolute rules

- **ZERO framework annotations**: no `@Entity`, `@Inject`, `@ApplicationScoped`, `@NotNull`, no Jakarta, no Quarkus
- Models are Java classes with constructors, getters, setters and pure domain logic
- Enums live in `domain/enums/`, **never** nested inside models
- Exceptions extend `RuntimeException` directly, with no annotations
- Command `record`s live in `domain/port/in/`, not in `application/`
- State transition logic (e.g. `canTransitionTo`) belongs to the model, not to the services

### application/: rules

- One operation = one class (e.g. `RegisterAnimalService`, not an `AnimalService` with 5 methods)
- Each service implements **exactly one** Use Case interface
- Allowed annotations: `@ApplicationScoped`, `@Transactional`
- `@Transactional` **only on the method** that writes to the DB, **never** on the class
- Injection **only through the constructor**, never `@Inject` on a field
- Services know only the `port/in` and `port/out` interfaces: never JPA classes, never JAX-RS, never Kafka
- Validation is explicit logic (if + throw), **not** Bean Validation annotations

### infrastructure/: rules

- JPA `@Entity` classes live in `infrastructure/persistence/`, **never** in the domain
- The Panache pattern is **Repository** (`implements PanacheRepository<E>`), **not** Active Record (`extends PanacheEntity`)
- Request/response DTOs live in `infrastructure/rest/`, never in the domain or the application
- Bean Validation (`@NotNull`, `@NotBlank`, `@Valid`) is allowed **only** on DTOs in `infrastructure/rest/`
- MapStruct mappers live in `infrastructure/rest/mapper/`
- Kafka adapters (producer/consumer) live in `infrastructure/event/`
- Events are published **only through the `AnimalEventPublisher` port (outbox pattern)**, never through a Kafka emitter called directly from `application/`. A consumer must be **idempotent** because delivery is at-least-once
- `OutboxAnimalEventPublisher` is `@Transactional(MANDATORY)`: an event is written only inside the transaction of the change that caused it
- Authorization uses `@RolesAllowed` **per method** on the resource, with role names from `infrastructure/security/ZooRoles`
- The acting user is read from `SecurityIdentity` in the resource and passed to the use case as a `String` (`performedBy`). The token never leaves `infrastructure/`
- Every error, 401 and 403 included, returns the same body `{"message": ...}`. Without dedicated mappers the catch-all mapper would turn 401/403 into 500

---

## Established code conventions

### Injection
```java
// CORRECT: constructor
public class RegisterAnimalService implements RegisterAnimalUseCase {
    private final AnimalRepository repository;

    public RegisterAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }
}

// WRONG: field injection
@Inject
AnimalRepository repository;
```

### @Transactional
```java
// CORRECT: on the method only
@ApplicationScoped
public class RegisterAnimalService implements RegisterAnimalUseCase {
    @Override
    @Transactional
    public Animal register(RegisterAnimalCommand cmd) { ... }
}

// WRONG: on the class
@ApplicationScoped
@Transactional
public class RegisterAnimalService implements RegisterAnimalUseCase { ... }
```

### Validation in the domain
```java
// CORRECT: explicit logic
if (cmd.name() == null || cmd.name().isBlank()) {
    throw new InvalidAnimalDataException("Animal name must not be blank");
}

// WRONG: Bean Validation annotations in the domain or the application
@NotBlank
private String name;
```

### Read-only operations (no @Transactional)
```java
// CORRECT: no annotation for pure queries
@Override
public Animal getById(UUID id) {
    return repository.findById(id)
            .orElseThrow(() -> new AnimalNotFoundException(id));
}
```

---

## Naming rules

| Artifact | Pattern | Example |
|---|---|---|
| Use Case interface | `{Verb}{Entity}UseCase` | `RegisterAnimalUseCase` |
| Command record | `{Verb}{Entity}Command` | `RegisterAnimalCommand` |
| Application service | `{Verb}{Entity}Service` | `RegisterAnimalService` |
| Repository port (out) | `{Entity}Repository` | `AnimalRepository` |
| JPA entity | `{Entity}Entity` | `AnimalEntity` |
| Panache repo adapter | `{Entity}PanacheRepository` | `AnimalPanacheRepository` |
| REST resource | `{Entity}Resource` | `AnimalResource` |
| Request DTO | `{Verb}{Entity}Request` | `RegisterAnimalRequest` |
| Response DTO | `{Entity}Response` | `AnimalResponse` |
| MapStruct mapper | `{Entity}Mapper` | `AnimalMapper` |
| Exception handler | `{Domain}ExceptionMapper` | `ZooExceptionMapper` |

---

## Testing rules

### Domain layer tests
- JUnit 5 only: **zero Quarkus**, zero Mockito
- Package: `it.zoo.animal.domain`
- They test the pure logic of the model (e.g. `canTransitionTo`)

### Application layer tests
- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
- **Zero `@QuarkusTest`**: the CDI context must not start
- `@Mock` on the port/out (repository), `@InjectMocks` on the service
- Package: `it.zoo.animal.application`

### Infrastructure layer tests
- `@QuarkusTest` with Dev Services (Testcontainers): in the `%test` profile no datasource URL or Kafka address is configured, so Quarkus Dev Services starts them. Docker must be running
- Kafka ITs use `@QuarkusTestResource(KafkaCompanionResource.class)` (`AnimalEventOutboxIT`, `AnimalEventConsumerIT`)
- OIDC is off in `%test` (`quarkus.oidc.enabled=false` is the default, turned on only in `%dev` and `%prod`), so no Keycloak Dev Service starts. Secured endpoints are tested with `@TestSecurity`
- For REST: built-in RestAssured
- Package: `it.zoo.animal.infrastructure`

### Test naming
```
should{ExpectedBehaviour}[When{Condition}]
```
Examples: `shouldRegisterAnimalWithHealthyStatus`, `shouldThrowWhenNameIsBlank`

---

## Maven / Build rules

- **Do not change versions in a child POM**: everything is managed by the parent BOM (`quarkus-bom`)
- Use `mvnw` / `mvnw.cmd` for Maven commands (each service includes the wrapper)
- Do not add dependencies without checking the parent POM first
- Dev command, from the service folder: `./mvnw quarkus:dev` (bash) or `.\mvnw.cmd quarkus:dev` (PowerShell)
- **Local credentials**: no secret is tracked. Each of these folders needs a git-ignored `.env`:
  - `zms-be/infrastructure/.env`, read by Docker Compose
  - `zms-be/animal-service/.env`, `zms-be/health-service/.env` and `zms-be/notification-service/.env`, read by Quarkus in dev mode

  Only `notification-service/env.example` exists. The `env.example` files of `infrastructure`, `animal-service` and `health-service` were removed in `15ba49f`. The variable list is in the root `README.md` ("Live mode"). Compose stops at startup if a required variable is missing.

  These pairs must match:

  | In `infrastructure/.env` | Must equal |
  |---|---|
  | `POSTGRES_USER` / `POSTGRES_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `animal-service/.env` |
  | `OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` in `animal-service/.env` |
  | `POSTGRES_HEALTH_USER` / `POSTGRES_HEALTH_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `health-service/.env` |
  | `HEALTH_OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` in `health-service/.env` |
  | `POSTGRES_NOTIFICATION_USER` / `POSTGRES_NOTIFICATION_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `notification-service/.env` |

  At import, the Keycloak realm substitutes `${ANIMAL_SERVICE_CLIENT_SECRET}`, `${HEALTH_SERVICE_CLIENT_SECRET}` and `${ZOO_TEST_USER_PASSWORD}`. Compose fills them from `OIDC_CLIENT_SECRET`, `HEALTH_OIDC_CLIENT_SECRET` and `ZOO_TEST_USER_PASSWORD`.
- **Dev Services**: `%dev.quarkus.devservices.enabled=false` is set in all three services, and only for `%dev`. In dev mode no Dev Service starts (Postgres, Kafka, Keycloak), and the services use the containers from `docker-compose.yml`. In `%test`, Dev Services stay on (see "Infrastructure layer tests")

---

## Project state

What is implemented, decided but not built, and open lives in [`docs/STATE.md`](../docs/STATE.md). Decisions and their reasons live in [`docs/decisions.md`](../docs/decisions.md), and event contracts in [`docs/events.md`](../docs/events.md). Do not keep state in this file.

---

## Documentation maintenance

- At the end of every phase, update `docs/STATE.md`: move items between sections, cite a class or file for every Implemented line, and update the "Last updated" line.
- When a contract changes (endpoint, role, request or response shape, event, topic, payload field, error status), update the service's `README.md` in the same change. If an event changed, also update `docs/events.md`.
- Append to `docs/decisions.md` only decisions the maintainer gives. Never write a rationale yourself: if none is given, write `Rationale: not recorded` and ask. Never edit or delete old entries; a reversal is a new entry.
- Never mix **Implemented**, **Decided, not built** and **Open**. Something is Implemented only if it is in the code on `main`.
- Every statement about current behaviour must come from the code and cite the class or file. If the code does not handle a scenario, write `Not handled`.
- Documentation is in English. Commands must work in both bash and PowerShell, or be given in both forms.

---

## Never do this

- Do not put `@Entity`, `@Column`, `@Id` or any JPA annotation in `domain/`
- Do not put `@Path`, `@GET`, `@POST` or JAX-RS annotations in `application/`
- Do not use the Active Record pattern (`extends PanacheEntity`): use the Repository pattern
- Do not use `AnimalEntity` directly in `application/`, only `Animal` (the domain object)
- Do not put business logic in the infrastructure layer
- Do not add Kafka, REST clients or other adapters in `application/`
- Do not change `domain/` to fit the infrastructure (the opposite applies)
- Do not put `@Transactional` at class level
- Do not use field injection (`@Inject` on a field)
- Do not create a single `AnimalService` with all CRUD methods: one class per Use Case

---

## Wiki Knowledge Base
Path: ~/second-brain

When saving sessions or looking for prior knowledge:
1. Read ~/second-brain/wiki/hot.md first (recent context)
2. If that is not enough, read ~/second-brain/wiki/index.md
3. Save session notes in ~/second-brain/wiki/
