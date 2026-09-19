---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# Codebase Structure

**Analysis Date:** 2026-09-19

**Scope:** `zms-be/` only.

## Directory Layout

```
zms-be/
├── pom.xml                      # Parent POM (it.zoo:zoo-management-system, packaging pom, Quarkus BOM 3.20.0, Java 21)
├── CLAUDE.md                    # Backend architecture rules, naming, testing conventions (Italian)
├── docs/
│   └── zoo-roadmap.html         # Roadmap document
├── infrastructure/              # Local dev infra (NOT a Maven module)
│   ├── docker-compose.yml       # postgres-animal (:5432) + keycloak 26.0 (:8081)
│   └── keycloak/
│       └── realm-export.json    # Realm "zoo" imported at Keycloak start
└── animal-service/              # Only Maven module present
    ├── pom.xml                  # Quarkus extensions, MapStruct, test deps, surefire/failsafe
    ├── mvnw, mvnw.cmd           # Maven wrapper
    ├── README.md
    └── src/
        ├── main/
        │   ├── docker/          # Quarkus-generated Dockerfiles (jvm, legacy-jar, native, native-micro)
        │   ├── java/it/zoo/animal/
        │   │   ├── domain/
        │   │   │   ├── model/           # Animal.java (pure POJO)
        │   │   │   ├── enums/           # AnimalStatus.java, Habitat.java
        │   │   │   ├── exception/       # *Exception.java (RuntimeException)
        │   │   │   └── port/
        │   │   │       ├── in/          # *UseCase.java, RegisterAnimalCommand.java
        │   │   │       └── out/         # AnimalRepository.java
        │   │   ├── application/         # *Service.java (use case impls)
        │   │   └── infrastructure/
        │   │       ├── persistence/     # AnimalEntity, AnimalEntityMapper, AnimalPanacheRepository
        │   │       ├── rest/            # AnimalResource, OpenApiConfig, *ExceptionMapper
        │   │       │   ├── dto/         # *Request.java, AnimalResponse.java (records)
        │   │       │   └── mapper/      # AnimalDtoMapper.java (MapStruct)
        │   │       └── security/        # ZooRoles.java
        │   └── resources/
        │       ├── application.properties
        │       └── db/migration/        # V1__create_animals_table.sql, V2__add_audit_columns.sql
        └── test/java/it/zoo/animal/     # Mirrors main packages
            ├── domain/                  # AnimalStatusTransitionTest.java
            ├── application/             # *ServiceTest.java (Mockito unit tests)
            └── infrastructure/
                ├── persistence/         # AnimalEntityMapperTest.java
                └── rest/                # AnimalResourceIT.java, AnimalSecurityIT.java
```

Not present (planned in `zms-be/CLAUDE.md`): `feeding-service/`, `health-service/`, `notification-service/`, `infrastructure/event/` Kafka adapters.

## Directory Purposes

**`zms-be/animal-service/src/main/java/it/zoo/animal/domain/`:**

- Purpose: Framework-free business core
- Contains: POJO model, enums, exceptions, port interfaces, command records
- Key files: `domain/model/Animal.java`, `domain/port/out/AnimalRepository.java`

**`zms-be/animal-service/src/main/java/it/zoo/animal/application/`:**

- Purpose: Use case implementations (`@ApplicationScoped`, `@Transactional` on writes)
- Key files: `RegisterAnimalService.java`, `UpdateAnimalStatusService.java`, `TransferAnimalService.java`

**`zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/`:**

- Purpose: HTTP adapter
- Key files: `AnimalResource.java`, `ZooExceptionMapper.java`, `SecurityExceptionMapper.java`, `OpenApiConfig.java`

**`zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/persistence/`:**

- Purpose: JPA adapter for `AnimalRepository`
- Key files: `AnimalEntity.java`, `AnimalEntityMapper.java`, `AnimalPanacheRepository.java`

**`zms-be/animal-service/src/main/resources/db/migration/`:**

- Purpose: Flyway schema migrations (Hibernate schema generation is disabled)

## Key File Locations

**Entry Points:**

- `zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`: `/animals` REST API
- `zms-be/infrastructure/docker-compose.yml`: local Postgres + Keycloak

**Configuration:**

- `zms-be/pom.xml`: module list, Quarkus BOM, Java 21, plugin versions
- `zms-be/animal-service/pom.xml`: service dependencies
- `zms-be/animal-service/src/main/resources/application.properties`: OIDC, datasource, Flyway, CORS (per `%dev`/`%test` profile)
- `zms-be/infrastructure/keycloak/realm-export.json`: realm, clients, roles

**Core Logic:**

- `zms-be/animal-service/src/main/java/it/zoo/animal/domain/model/Animal.java`: transition rules
- `zms-be/animal-service/src/main/java/it/zoo/animal/application/`: use cases

**Testing:**

- `zms-be/animal-service/src/test/java/it/zoo/animal/`: unit (`*Test.java`) and integration (`*IT.java`) tests

## Naming Conventions

**Files (from `zms-be/CLAUDE.md`, verified in code):**

- Use case interface: `{Verb}{Entity}UseCase` — `RegisterAnimalUseCase.java`
- Command record: `{Verb}{Entity}Command` — `RegisterAnimalCommand.java`
- Application service: `{Verb}{Entity}Service` — `RegisterAnimalService.java`
- Outbound port: `{Entity}Repository` — `AnimalRepository.java`
- JPA entity: `{Entity}Entity` — `AnimalEntity.java`
- Repository adapter: `{Entity}PanacheRepository` — `AnimalPanacheRepository.java`
- Entity mapper: `{Entity}EntityMapper` (static) — `AnimalEntityMapper.java`
- REST resource: `{Entity}Resource` — `AnimalResource.java`
- DTOs: `{Verb}{Entity}Request`, `{Entity}Response` — `TransferAnimalRequest.java`, `AnimalResponse.java`
- REST mapper: `{Entity}DtoMapper` (MapStruct) — `AnimalDtoMapper.java`
- Exception mapper: `{Domain}ExceptionMapper` — `ZooExceptionMapper.java`
- Tests: `{Class}Test` (unit, surefire), `{Class}IT` (integration, failsafe)
- Flyway: `V{n}__{snake_case_description}.sql`

**Directories:**

- Java package root `it.zoo.{service}` (e.g. `it.zoo.animal`); lowercase single-word packages
- Service module folders: `{bounded-context}-service` (kebab-case)

## Where to Add New Code

**New use case in animal-service:**

- Port: `zms-be/animal-service/src/main/java/it/zoo/animal/domain/port/in/{Verb}AnimalUseCase.java` (+ `{Verb}AnimalCommand.java` if many params)
- Impl: `zms-be/animal-service/src/main/java/it/zoo/animal/application/{Verb}AnimalService.java`
- Endpoint: method in `infrastructure/rest/AnimalResource.java` + `infrastructure/rest/dto/{Verb}AnimalRequest.java`
- Tests: `src/test/java/it/zoo/animal/application/{Verb}AnimalServiceTest.java`, extend `infrastructure/rest/AnimalResourceIT.java` / `AnimalSecurityIT.java`

**New domain rule / exception:**

- Rule method on `domain/model/Animal.java`; exception in `domain/exception/`; add mapping branch in `infrastructure/rest/ZooExceptionMapper.java`

**Schema change:**

- `zms-be/animal-service/src/main/resources/db/migration/V{next}__{description}.sql`, then update `AnimalEntity`, `AnimalEntityMapper`, `Animal`

**New microservice (feeding/health/notification):**

- Create `zms-be/{name}-service/` mirroring `animal-service` layout with package `it.zoo.{name}`
- Register `<module>` in `zms-be/pom.xml`
- Add a dedicated Postgres service to `zms-be/infrastructure/docker-compose.yml` and a Keycloak client in `realm-export.json`

**Event adapters (Kafka):**

- `zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/event/` with an outbound port in `domain/port/out/`

**Utilities:**

- No shared library module exists; role constants live in `infrastructure/security/ZooRoles.java` per service.

## Special Directories

**`zms-be/animal-service/target/`:**

- Purpose: Maven build output
- Generated: Yes
- Committed: No

**`zms-be/animal-service/src/main/docker/`:**

- Purpose: Quarkus scaffold Dockerfiles
- Generated: Yes (Quarkus scaffold)
- Committed: Yes

**`zms-be/infrastructure/`:**

- Purpose: Dev-only containers (contains dev credentials inline)
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-09-19*
