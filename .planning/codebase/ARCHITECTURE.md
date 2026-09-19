---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
<!-- refreshed: 2026-09-19 -->

# Architecture

**Analysis Date:** 2026-09-19

**Scope:** `zms-be/` only. The Maven reactor (`zms-be/pom.xml`) declares a single module, `animal-service`. `feeding-service`, `health-service` and `notification-service` exist only as **empty placeholder folders** (untracked by git, no code, no POM) (they are listed as "Prossime fasi" in `zms-be/CLAUDE.md`). `zms-be/infrastructure/` is local dev infra (Docker Compose + Keycloak realm), not a Java module.

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│              HTTP clients (zms-fe on :4200, curl)            │
│              Bearer JWT issued by Keycloak realm "zoo"       │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│           INFRASTRUCTURE (driving adapters) — rest/          │
├──────────────────┬──────────────────┬───────────────────────┤
│  AnimalResource  │ ZooExceptionMapper│ SecurityExceptionMapper│
│  + DTO records   │  (domain → HTTP)  │  (401/403 JSON)        │
│  + AnimalDtoMapper (MapStruct)       │  OpenApiConfig         │
│ `infrastructure/rest/`                                       │
└────────┬────────────────────────────────────────────────────┘
         │ calls port/in interfaces (UseCase)
         ▼
┌─────────────────────────────────────────────────────────────┐
│     APPLICATION — one @ApplicationScoped Service per use case│
│  Register / Get / List / UpdateAnimalStatus / Transfer       │
│  `application/`                                              │
└────────┬────────────────────────────────────────────────────┘
         │ uses domain model + port/out interface
         ▼
┌─────────────────────────────────────────────────────────────┐
│  DOMAIN — framework-free: model/, enums/, exception/, port/  │
│  `domain/`                                                   │
└────────▲────────────────────────────────────────────────────┘
         │ implements port/out
┌────────┴────────────────────────────────────────────────────┐
│  INFRASTRUCTURE (driven adapter) — persistence/              │
│  AnimalPanacheRepository (EntityManager) + AnimalEntity      │
│  + AnimalEntityMapper (static manual mapper)                 │
└────────┬────────────────────────────────────────────────────┘
         ▼
┌─────────────────────────────────────────────────────────────┐
│  PostgreSQL 16 `animal_db` (Flyway: db/migration/V*.sql)     │
│  `zms-be/infrastructure/docker-compose.yml`                  │
└─────────────────────────────────────────────────────────────┘
```

All Java paths below are relative to `zms-be/animal-service/src/main/java/it/zoo/animal/` unless fully qualified.

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| AnimalResource | JAX-RS endpoints `/animals`, RBAC via `@RolesAllowed`, builds Commands, extracts actor from `SecurityIdentity` | `infrastructure/rest/AnimalResource.java` |
| Request/Response DTOs | Java records with Bean Validation (`@NotBlank`, `@NotNull`) | `infrastructure/rest/dto/*.java` |
| AnimalDtoMapper | MapStruct (`componentModel = "cdi"`) domain → `AnimalResponse` | `infrastructure/rest/mapper/AnimalDtoMapper.java` |
| ZooExceptionMapper | Maps domain exceptions to 404/400/422, else 500; defines `ErrorResponse(message)` record | `infrastructure/rest/ZooExceptionMapper.java` |
| SecurityExceptionMapper | Maps Quarkus security exceptions to 401/403 JSON | `infrastructure/rest/SecurityExceptionMapper.java` |
| OpenApiConfig | `@OpenAPIDefinition` + `bearerAuth` JWT scheme | `infrastructure/rest/OpenApiConfig.java` |
| ZooRoles | Role constants `zoo-admin`, `zoo-vet`, `zoo-keeper` | `infrastructure/security/ZooRoles.java` |
| Application services | Use case orchestration, input validation, `@Transactional` on writes | `application/*Service.java` |
| Animal | Domain POJO with business rules `canTransitionTo`, `canBeTransferred` | `domain/model/Animal.java` |
| Inbound ports | `*UseCase` interfaces + `RegisterAnimalCommand` record | `domain/port/in/` |
| AnimalRepository | Outbound persistence port | `domain/port/out/AnimalRepository.java` |
| Domain exceptions | `AnimalNotFoundException`, `InvalidAnimalDataException`, `InvalidStatusTransitionException` | `domain/exception/` |
| AnimalPanacheRepository | Implements `AnimalRepository` using injected `EntityManager` (merge/find/JPQL) | `infrastructure/persistence/AnimalPanacheRepository.java` |
| AnimalEntity / AnimalEntityMapper | JPA entity on `animals` table + static domain↔entity mapping | `infrastructure/persistence/` |

## Pattern Overview

**Overall:** Hexagonal architecture (Ports & Adapters) inside a Quarkus 3.20 microservice; planned microservice decomposition (one service per bounded context, one DB per service).

**Key Characteristics:**

- Strict dependency rule `infrastructure → application → domain` (documented as "NON VIOLARE MAI" in `zms-be/CLAUDE.md`).
- `domain/` has zero framework annotations (only `java.*` imports) — see `domain/model/Animal.java`.
- One use case = one `{Verb}{Entity}UseCase` interface + one `{Verb}{Entity}Service` implementation.
- Constructor injection everywhere (no field `@Inject`).
- Separate models per layer: `Animal` (domain), `AnimalEntity` (JPA), `*Request`/`AnimalResponse` (REST).

## Layers

**Domain:**

- Purpose: Business model, rules, and port contracts
- Location: `domain/` (`model/`, `enums/`, `exception/`, `port/in/`, `port/out/`)
- Contains: POJOs, enums (`AnimalStatus`, `Habitat`), `RuntimeException` subclasses, interfaces, Command records
- Depends on: JDK only
- Used by: application, infrastructure

**Application:**

- Purpose: Implement use cases; validate command inputs; manage transactions
- Location: `application/`
- Contains: `GetAnimalService`, `ListAnimalsService`, `RegisterAnimalService`, `TransferAnimalService`, `UpdateAnimalStatusService`
- Depends on: domain (+ `jakarta.enterprise`/`jakarta.transaction` annotations)
- Used by: `infrastructure/rest/AnimalResource.java` via port/in interfaces

**Infrastructure:**

- Purpose: Adapters to HTTP, security, and PostgreSQL
- Location: `infrastructure/rest/`, `infrastructure/persistence/`, `infrastructure/security/`
- Contains: JAX-RS resource, DTOs, mappers, exception mappers, JPA entity, repository adapter
- Depends on: domain, application ports, Quarkus extensions
- Used by: Quarkus runtime (CDI, RESTEasy Reactive)

## Data Flow

### Primary Request Path (POST /animals — register)

1. JAX-RS dispatch; OIDC bearer token validated, `@RolesAllowed(ZooRoles.ADMIN)` enforced (`infrastructure/rest/AnimalResource.java:36`)
2. `@Valid RegisterAnimalRequest` validated by Hibernate Validator (`infrastructure/rest/dto/RegisterAnimalRequest.java`)
3. Resource builds `RegisterAnimalCommand` with `currentActor()` = JWT principal name (`AnimalResource.java:38-43`, `:79`)
4. `RegisterAnimalService.register` re-validates fields, creates `Animal` with `UUID.randomUUID()` and status `HEALTHY`, sets `createdBy`/`updatedBy` (`application/RegisterAnimalService.java`)
5. `AnimalPanacheRepository.save` → `AnimalEntityMapper.toEntity` → `em.merge` → back to domain (`infrastructure/persistence/AnimalPanacheRepository.java`)
6. `AnimalDtoMapper.toResponse` → `201 Created` with JSON body

### State-change Flow (PUT /animals/{id}/status, /transfer)

1. Resource checks role (`VET|ADMIN` for status, `KEEPER|ADMIN` for transfer) and calls use case with actor
2. Service loads via `repository.findById(...).orElseThrow(AnimalNotFoundException::new)`
3. Service asks the domain object (`animal.canTransitionTo(target)` / `animal.canBeTransferred()`); violation → `InvalidStatusTransitionException` (422) or `InvalidAnimalDataException` (400)
4. Mutate via setters, set `updatedBy`, `repository.save` inside `@Transactional`

### Read Flow (GET /animals, GET /animals/{id})

1. Roles `ADMIN|VET|KEEPER`; services are non-transactional (read-only convention in `zms-be/CLAUDE.md`)
2. `findAll()` uses JPQL `SELECT a FROM AnimalEntity a` (no pagination)

**State Management:**

- Stateless services; all state in PostgreSQL `animals` table. Schema owned by Flyway (`hibernate-orm.database.generation=none`).

## Key Abstractions

**Use Case port + Service:**

- Purpose: One inbound operation
- Examples: `domain/port/in/TransferAnimalUseCase.java` ↔ `application/TransferAnimalService.java`
- Pattern: Interface in domain, `@ApplicationScoped` impl in application, injected by interface into the resource

**Command record:**

- Purpose: Carry multi-field input into a use case, including `performedBy`
- Examples: `domain/port/in/RegisterAnimalCommand.java`
- Pattern: Java record; simpler use cases take plain parameters `(UUID id, ..., String performedBy)`

**Repository port/adapter:**

- Purpose: Persistence abstraction
- Examples: `domain/port/out/AnimalRepository.java` ↔ `infrastructure/persistence/AnimalPanacheRepository.java`
- Pattern: Adapter maps entity↔domain on every call; domain never sees `AnimalEntity`

**Audit actor:**

- Purpose: Track who changed what (`created_by`, `updated_by` columns, `V2__add_audit_columns.sql`)
- Pattern: Resource resolves actor from `SecurityIdentity`; services reject blank actor

## Entry Points

**Quarkus application (animal-service):**

- Location: `zms-be/animal-service` (no custom `main`; Quarkus bootstrap). Run with `mvnw quarkus:dev`.
- Triggers: HTTP on default port 8080
- Responsibilities: REST API `/animals`, OpenAPI (`quarkus-smallrye-openapi`), Flyway migrate-at-start in dev/test

**Local infrastructure:**

- Location: `zms-be/infrastructure/docker-compose.yml`
- Triggers: `docker compose up`
- Responsibilities: `postgres-animal` (postgres:16-alpine, :5432, db `animal_db`), `keycloak` 26.0 (:8081, imports `keycloak/realm-export.json`)

## Architectural Constraints

- **Threading:** Blocking JAX-RS endpoints on Quarkus worker threads (JPA/JDBC, not reactive).
- **Global state:** None beyond CDI `@ApplicationScoped` singletons; `ZooRoles` is a constants class.
- **Circular imports:** None detected. Minor cross-reference: `SecurityExceptionMapper` reuses `ZooExceptionMapper.ErrorResponse`.
- **Profiles:** OIDC is disabled by default (`quarkus.oidc.enabled=false`) and enabled only in `%dev`; `%test` uses `quarkus-test-security`. Datasource is configured only for `%dev`/`%test` — no prod profile config exists (`src/main/resources/application.properties`).
- **CORS:** `%dev` only, origin `http://localhost:4200`, methods GET/POST/PUT/OPTIONS.
- **Database per service:** each service owns its own Postgres database (`animal_db`).

## Anti-Patterns

### Framework types leaking into domain

**What happens:** Tempting to put `@Entity`/`@NotNull` on `domain/model/Animal.java`.
**Why it's wrong:** Breaks the hexagonal dependency rule mandated in `zms-be/CLAUDE.md`.
**Do this instead:** Keep JPA on `infrastructure/persistence/AnimalEntity.java`, validation annotations on `infrastructure/rest/dto/*Request.java`, and business checks in services / domain methods.

### Business rules in the resource

**What happens:** Checking status transitions or resolving entities in `AnimalResource`.
**Why it's wrong:** Resource is an adapter; rules become untestable without HTTP.
**Do this instead:** Put rules on `Animal` (`canTransitionTo`) and orchestrate in `application/*Service.java`, as in `UpdateAnimalStatusService`.

### Returning JPA entities from repositories

**What happens:** Exposing `AnimalEntity` through `AnimalRepository`.
**Why it's wrong:** Couples domain/application to persistence.
**Do this instead:** Map with `AnimalEntityMapper.toDomain` inside `AnimalPanacheRepository`.

## Error Handling

**Strategy:** Domain throws unchecked exceptions; a single JAX-RS `ExceptionMapper<RuntimeException>` translates to HTTP with body `{"message": "..."}`.

**Patterns:**

- `AnimalNotFoundException` → 404, `InvalidAnimalDataException` → 400, `InvalidStatusTransitionException` → 422, other `RuntimeException` → 500 "Internal server error" (`infrastructure/rest/ZooExceptionMapper.java`)
- `ForbiddenException` → 403 "Insufficient role"; `UnauthorizedException`/`AuthenticationFailedException` → 401 (`infrastructure/rest/SecurityExceptionMapper.java`)
- New domain exceptions must be added as an `instanceof` branch in `ZooExceptionMapper`

## Cross-Cutting Concerns

**Logging:** No explicit logging in code; Quarkus default logging only.
**Validation:** Two tiers — Bean Validation on request DTOs (`@Valid`), plus defensive checks in application services throwing `InvalidAnimalDataException`.
**Authentication:** Keycloak OIDC bearer tokens (`quarkus-oidc`, `application-type=service`, roles from `realm_access/roles`); RBAC via `@RolesAllowed` with `ZooRoles` constants.
**Messaging:** Not implemented. `infrastructure/event/` (Kafka) is planned in `zms-be/CLAUDE.md` but absent.

---

*Architecture analysis: 2026-09-19*
