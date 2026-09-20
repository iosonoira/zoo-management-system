# health-service Design

**Date:** 2026-09-20
**Phase:** 7 (first service after `animal-service`)
**Scope:** A new Quarkus module `zms-be/health-service` covering veterinary medical
records and treatments, with its own database, its own Keycloak client, and no
runtime dependency on `animal-service`.

---

## Context

`animal-service` is complete through phase 6: hexagonal domain, 5 use cases, Panache
persistence with Flyway, 5 REST endpoints, OIDC + `@RolesAllowed` authorization, actor
auditing, optimistic locking, pagination. `mvnw verify` passes with 38 unit tests and
25 integration tests.

`zms-be/pom.xml` declares a single module, `animal-service`. The folders
`health-service/`, `feeding-service/` and `notification-service/` exist on disk but are
empty and untracked: no POM, no source, no migrations.

`zms-be/CLAUDE.md` lists the remaining work as "Kafka producer for animal events" plus
the three missing services. This phase deliberately takes `health-service` first and
leaves Kafka untouched, because `notification-service` is by design a Kafka consumer and
cannot be built before a producer exists, while `health-service` is a self-contained
bounded context that needs no messaging to be useful.

`health-service` is also the service that gives the `zoo-vet` realm role a real surface.
Today a vet can only flip an animal's status; clinical history has nowhere to live.

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Which service first | `health-service` | Self-contained bounded context; no Kafka prerequisite; gives `zoo-vet` a real surface beyond status changes |
| Domain scope | `MedicalRecord` + `Treatment` | Two aggregates with genuine business rules. One entity alone would leave the domain layer anemic and give the hexagonal structure nothing to protect; adding vaccinations would pull in scheduling that belongs to `notification-service` |
| Animal reference | `animalId` stored as an opaque `UUID`, never validated | Zero runtime coupling: the two services start, test and fail independently, which is the point of database-per-service. The frontend joins the two reads |
| Local database | Dedicated `postgres-health` container | Real isolation, matches how the services would be deployed. Costs one extra container locally |
| Role constants | `ZooRoles` copied into `it.zoo.health.infrastructure.security` | Three string constants duplicated is cheaper than a shared Maven module that recouples every service at build time |
| Pagination | `{items, page, size, total}` from the first commit | `GET /animals` already returns this shape and the frontend mock still returns a bare array. The new service should not repeat that drift |
| Audit + locking | `createdBy`/`updatedBy` and `version` in the initial migration | On `animal-service` these arrived as V2 and V3 retrofits. A new service has no reason to repeat the catch-up |
| Date type | `java.time.LocalDate` | Matches `Animal.arrivalDate`; clinical dates are day-granular and timezone-free, which keeps tests deterministic |

### Rejected alternatives

- **Synchronous REST client to `animal-service`** to verify the animal exists: stronger
  data integrity, but couples the services at runtime — `health-service` would refuse
  writes whenever `animal-service` is down, and every integration test would need a
  WireMock stub.
- **Local read model of animals fed by Kafka events**: the correct long-term answer, but
  it requires the animal event producer that this phase explicitly does not build.
- **Shared `zms-common` module** for `ZooRoles` and the error response record: removes
  duplication, but every service then shares a release cycle with every other one.
- **`feeding-service` first**: equally self-contained, but feeding plans bring recurrence
  and scheduling, a larger design surface for no extra architectural lesson.
- **Vaccinations with due dates**: realistic, but the reminder logic overlaps
  `notification-service` and would be built twice.

---

## Architecture

```
zms-be/
├── pom.xml                                    ← MODIFIED: add <module>health-service</module>
└── health-service/
    ├── pom.xml                                ← NEW: mirrors animal-service, no versions
    ├── env.example                            ← NEW
    ├── mvnw, mvnw.cmd, .mvn/                  ← NEW: wrapper, copied from animal-service
    └── src/
        ├── main/java/it/zoo/health/
        │   ├── domain/
        │   │   ├── model/
        │   │   │   ├── MedicalRecord.java
        │   │   │   ├── Treatment.java          ← canTransitionTo()
        │   │   │   ├── MedicalRecordDetail.java
        │   │   │   └── MedicalRecordPage.java
        │   │   ├── enums/TreatmentStatus.java
        │   │   ├── port/in/
        │   │   │   ├── CreateMedicalRecordUseCase.java
        │   │   │   ├── CreateMedicalRecordCommand.java
        │   │   │   ├── GetMedicalRecordUseCase.java
        │   │   │   ├── ListMedicalRecordsUseCase.java
        │   │   │   ├── PrescribeTreatmentUseCase.java
        │   │   │   ├── PrescribeTreatmentCommand.java
        │   │   │   └── UpdateTreatmentStatusUseCase.java
        │   │   ├── port/out/
        │   │   │   ├── MedicalRecordRepository.java
        │   │   │   └── TreatmentRepository.java
        │   │   └── exception/
        │   │       ├── MedicalRecordNotFoundException.java
        │   │       ├── TreatmentNotFoundException.java
        │   │       ├── InvalidMedicalDataException.java
        │   │       ├── InvalidTreatmentStatusTransitionException.java
        │   │       ├── ConcurrentMedicalRecordUpdateException.java
        │   │       └── ConcurrentTreatmentUpdateException.java
        │   ├── application/
        │   │   ├── CreateMedicalRecordService.java
        │   │   ├── GetMedicalRecordService.java
        │   │   ├── ListMedicalRecordsService.java
        │   │   ├── PrescribeTreatmentService.java
        │   │   └── UpdateTreatmentStatusService.java
        │   └── infrastructure/
        │       ├── security/ZooRoles.java
        │       ├── persistence/
        │       │   ├── MedicalRecordEntity.java
        │       │   ├── TreatmentEntity.java
        │       │   ├── MedicalRecordEntityMapper.java
        │       │   ├── TreatmentEntityMapper.java
        │       │   ├── MedicalRecordPanacheRepository.java
        │       │   └── TreatmentPanacheRepository.java
        │       └── rest/
        │           ├── MedicalRecordResource.java
        │           ├── TreatmentResource.java
        │           ├── MedicalRecordNotFoundExceptionMapper.java
        │           ├── TreatmentNotFoundExceptionMapper.java
        │           ├── InvalidMedicalDataExceptionMapper.java
        │           ├── InvalidTreatmentStatusTransitionExceptionMapper.java
        │           ├── ConcurrentMedicalRecordUpdateExceptionMapper.java
        │           ├── ConcurrentTreatmentUpdateExceptionMapper.java
        │           ├── UnexpectedExceptionMapper.java
        │           ├── SecurityExceptionMapper.java
        │           ├── OpenApiConfig.java
        │           ├── dto/
        │           └── mapper/
        └── main/resources/
            ├── application.properties
            └── db/migration/V1__create_medical_records_and_treatments.sql
```

The dependency rule `infrastructure → application → domain` is unchanged and applies
identically. `domain/` carries no framework annotation.

---

## Domain model

### MedicalRecord

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Generated by the application service |
| `animalId` | `UUID` | Opaque. Required, never dereferenced |
| `reason` | `String` | Required, max 200 |
| `diagnosis` | `String` | Required, max 1000 |
| `examinedOn` | `LocalDate` | Required, must not be in the future |
| `veterinarian` | `String` | Required, max 100 |
| `createdBy` / `updatedBy` | `String` | JWT principal name |
| `version` | `long` | Optimistic locking |

### Treatment

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | Generated by the application service |
| `medicalRecordId` | `UUID` | Required; the record must exist in this service |
| `description` | `String` | Required, max 500 |
| `status` | `TreatmentStatus` | Starts at `PRESCRIBED` |
| `startedOn` | `LocalDate` | Set when the status becomes `ACTIVE` |
| `endedOn` | `LocalDate` | Set when the status becomes `COMPLETED` or `CANCELLED` |
| `createdBy` / `updatedBy` | `String` | JWT principal name |
| `version` | `long` | Optimistic locking |

### TreatmentStatus transitions

`Treatment.canTransitionTo(TreatmentStatus target)` owns this table, mirroring
`Animal.canTransitionTo`:

| From | Allowed targets |
|---|---|
| `PRESCRIBED` | `ACTIVE`, `CANCELLED` |
| `ACTIVE` | `COMPLETED`, `CANCELLED` |
| `COMPLETED` | none — terminal |
| `CANCELLED` | none — terminal |

A transition to the same status is rejected. A rejected transition raises
`InvalidTreatmentStatusTransitionException` → HTTP 422.

`UpdateTreatmentStatusService` sets the dates as a side effect of a legal transition:
`ACTIVE` sets `startedOn` to today if unset; `COMPLETED` and `CANCELLED` set `endedOn` to
today. The dates are never accepted from the client.

### MedicalRecordPage and MedicalRecordDetail

`MedicalRecordPage(List<MedicalRecord> items, int page, int size, long total)` matches
`AnimalPage`.

`MedicalRecordDetail(MedicalRecord record, List<Treatment> treatments)` is what
`GET /medical-records/{id}` returns. Without it, treatments would be write-only: nothing
in the API would ever read one back, since there is no `GET /treatments` endpoint. The
list endpoint deliberately returns bare records with no treatments, to avoid an N+1
query on every page.

---

## Use cases

One interface in `domain/port/in/`, one `@ApplicationScoped` implementation in
`application/`, constructor injection, `@Transactional` on write methods only.

| Use case | Signature | Transactional |
|---|---|---|
| `CreateMedicalRecordUseCase` | `MedicalRecord create(CreateMedicalRecordCommand cmd)` | yes |
| `GetMedicalRecordUseCase` | `MedicalRecordDetail getById(UUID id)` | no |
| `ListMedicalRecordsUseCase` | `MedicalRecordPage list(UUID animalId, int page, int size)` | no |
| `PrescribeTreatmentUseCase` | `Treatment prescribe(PrescribeTreatmentCommand cmd)` | yes |
| `UpdateTreatmentStatusUseCase` | `Treatment updateStatus(UUID id, TreatmentStatus target, String performedBy)` | yes |

`ListMedicalRecordsUseCase.MAX_PAGE_SIZE = 100`, as on `ListAnimalsUseCase`. A null
`animalId` lists every record; a non-null one filters. Both commands carry `performedBy`.

Validation in the services is explicit `if` + `throw`, never Bean Validation:
blank strings, null required fields, `examinedOn` in the future, a blank actor, and a
`medicalRecordId` that resolves to nothing all raise a domain exception.

---

## REST API and authorization

Base path `/medical-records` and `/treatments`. Bearer JWT from realm `zoo`.

| Method | Path | Roles | Success | Errors |
|---|---|---|---|---|
| `POST` | `/medical-records` | vet, admin | 201 | 400 |
| `GET` | `/medical-records/{id}` | vet, admin, keeper | 200 | 404 |
| `GET` | `/medical-records?animalId=&page=&size=` | vet, admin, keeper | 200 | 400 |
| `POST` | `/medical-records/{id}/treatments` | vet, admin | 201 | 400, 404 |
| `PUT` | `/treatments/{id}/status` | vet, admin | 200 | 400, 404, 409, 422 |

A keeper reads clinical history but never writes it. `POST /medical-records/{id}/treatments`
lives on `MedicalRecordResource` because the record is the parent; `PUT /treatments/{id}/status`
lives on `TreatmentResource` because the treatment is addressed directly.

`GET /medical-records` returns `MedicalRecordPageResponse(items, page, size, total)`,
the same shape as `AnimalPageResponse`. `page` defaults to 0, `size` to 20, and a `size`
above `MAX_PAGE_SIZE` or a negative `page` is a 400.

Error handling copies the shape `animal-service` arrived at: **one `@Provider`
`ExceptionMapper` class per exception type**, not a single mapper branching on
`instanceof`. Each returns `ErrorResponse(String message)` as
`application/json`. `UnexpectedExceptionMapper implements ExceptionMapper<Exception>`
is the last resort — it maps `Exception` rather than `RuntimeException` on purpose, so
the framework still answers 400/404 for a malformed path parameter or an unparseable
body instead of 500. `SecurityExceptionMapper` returns 401 and 403 with the same body
so that security failures do not surface as 500.

Optimistic lock conflicts follow the same route as on `animal-service`: the repository
adapter catches `OptimisticLockException` and rethrows the domain exception
(`ConcurrentMedicalRecordUpdateException` / `ConcurrentTreatmentUpdateException`), which
its own mapper turns into 409. The JPA exception never reaches the REST layer.
The actor is read from `SecurityIdentity` inside the resource and passed down as a
`String` — the token never leaves `infrastructure`.

---

## Persistence

Single migration `V1__create_medical_records_and_treatments.sql`, including audit
columns and `version` from the start:

- `medical_records` — `id UUID PK`, `animal_id UUID NOT NULL`, `reason VARCHAR(200) NOT NULL`,
  `diagnosis VARCHAR(1000) NOT NULL`, `examined_on DATE NOT NULL`, `veterinarian VARCHAR(100) NOT NULL`,
  `created_by VARCHAR(255)`, `updated_by VARCHAR(255)`, `version BIGINT NOT NULL DEFAULT 0`,
  plus an index on `animal_id` because every list query filters on it.
- `treatments` — `id UUID PK`, `medical_record_id UUID NOT NULL REFERENCES medical_records(id)`,
  `description VARCHAR(500) NOT NULL`, `status VARCHAR(20) NOT NULL`, `started_on DATE`,
  `ended_on DATE`, `created_by VARCHAR(255)`, `updated_by VARCHAR(255)`,
  `version BIGINT NOT NULL DEFAULT 0`, plus an index on `medical_record_id`.

The foreign key is intentional and safe: both tables live in `health_db`. There is no
foreign key of any kind toward `animals`, which lives in another database.

Repository adapters implement the `port/out` interfaces with an injected
`EntityManager`, and map entity↔domain on every call through static mappers.
`hibernate-orm.database.generation=none`; Flyway owns the schema.

---

## Local infrastructure

**docker-compose** gains one service and one volume:

```yaml
postgres-health:
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: ${POSTGRES_HEALTH_DB:-health_db}
    POSTGRES_USER: ${POSTGRES_HEALTH_USER:-zoo}
    POSTGRES_PASSWORD: ${POSTGRES_HEALTH_PASSWORD:?copy env.example to .env first}
  ports:
    - "5433:5432"
  volumes:
    - health_data:/var/lib/postgresql/data
```

The existing `keycloak` service gains one environment entry,
`HEALTH_SERVICE_CLIENT_SECRET: ${HEALTH_OIDC_CLIENT_SECRET:?copy env.example to .env first}`.

**Keycloak realm** gains a confidential client `health-service` in
`zms-be/infrastructure/keycloak/realm-export.json`, with its secret as
`${HEALTH_SERVICE_CLIENT_SECRET}`. Roles, users and the `zms-fe` client are unchanged:
the three realm roles already cover this service.

**Secrets** follow the existing rule — nothing tracked. `zms-be/infrastructure/env.example`
gains `POSTGRES_HEALTH_PASSWORD` and `HEALTH_OIDC_CLIENT_SECRET`; a new
`zms-be/health-service/env.example` carries `DB_PASSWORD` and `OIDC_CLIENT_SECRET` for
that module. Because Quarkus reads `.env` from the module directory, `health-service`
reuses the same variable names as `animal-service` without collision;
`POSTGRES_HEALTH_PASSWORD` and the module's `DB_PASSWORD` must match, exactly as the
animal pair does today.

**Ports:** `animal-service` 8080, Keycloak 8081, `health-service` **8082**,
`postgres-animal` 5432, `postgres-health` 5433.

---

## Configuration

`health-service/src/main/resources/application.properties` mirrors `animal-service`
with the health values, and adds the port:

- `%dev.quarkus.http.port=8082` — scoped to `%dev` on purpose: a global
  `quarkus.http.port` would also move the test harness off its default port
- `quarkus.oidc.enabled=false` globally; `true` under `%dev` and `%prod`
- `%dev` OIDC against `http://localhost:8081/realms/zoo`, client `health-service`,
  secret `${OIDC_CLIENT_SECRET}`, `application-type=service`,
  `roles.role-claim-path=realm_access/roles`
- `%dev` datasource `jdbc:postgresql://localhost:5433/health_db`,
  `hibernate-orm.database.generation=none`, `flyway.migrate-at-start=true`,
  `devservices.enabled=false`
- `%prod` reads `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID` (default `health-service`),
  `OIDC_CLIENT_SECRET`, `DB_JDBC_URL`, `DB_USERNAME`, `DB_PASSWORD`, and CORS from
  `CORS_ENABLED` / `CORS_ORIGINS`
- `%test` uses Dev Services PostgreSQL with Flyway at start, no OIDC
- `%dev` CORS allows `http://localhost:4200`, methods `GET,POST,PUT,OPTIONS`,
  headers `Content-Type,Authorization`

The module POM declares no versions: the parent BOM governs. Dependencies match
`animal-service` — `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`,
`quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-oidc`, `quarkus-smallrye-openapi`,
`mapstruct`, and the test set.

---

## Testing

Mirrors the existing test strategy exactly.

**Domain — JUnit 5 only, no Quarkus, no Mockito.** `TreatmentStatusTransitionTest`
covers every cell of the transition table, both terminal states, and the
same-status rejection.

**Application — JUnit 5 + Mockito, no `@QuarkusTest`.** All five services, with the
`port/out` interfaces mocked: happy path, missing record, blank actor, blank required
fields, a future `examinedOn`, an illegal transition, and the date side effects of
`ACTIVE` / `COMPLETED` / `CANCELLED`.

**Infrastructure — `@QuarkusTest` with Testcontainers.**
`MedicalRecordEntityMapperTest` and `TreatmentEntityMapperTest` for round trips;
`MedicalRecordResourceIT` for the five endpoints including pagination bounds and the
404/422 bodies; `HealthSecurityIT` for the authorization matrix, using `@TestSecurity`
with each of the three roles plus the anonymous case.

Test naming stays `should{Behaviour}[When{Condition}]`.

Done means `mvnw verify` passes from `zms-be/` for both modules.

---

## Out of scope

- Kafka, event publishing, and `infrastructure/event/` — a later phase, and the
  prerequisite for `notification-service`
- Any frontend work; the Glasshouse redesign on `feature/glasshouse-redesign` is unrelated
- Vaccinations, due dates, reminders
- Verifying that `animalId` refers to a real animal, in any form
- `feeding-service` and `notification-service`
- Deployment and container image publishing. The one CI change in scope is turning the
  existing workflow's hardcoded `working-directory: zms-be/animal-service` into a matrix
  over both modules — without it the workflow would stay blind to `health-service`

---

## Definition of done

1. `zms-be/pom.xml` lists `health-service`; `mvnw verify` passes from `zms-be/` for both modules.
2. The domain layer compiles with no framework import.
3. All five endpoints answer with the documented status codes and RBAC.
4. `GET /medical-records` returns `{items, page, size, total}`.
5. `docker compose up` starts `postgres-animal`, `postgres-health` and `keycloak`, and
   `health-service` reaches `health_db` on 5433 and Keycloak on 8081.
6. No secret is tracked; both `env.example` files list every variable the module needs.
