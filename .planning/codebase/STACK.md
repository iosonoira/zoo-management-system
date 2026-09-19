---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# Technology Stack

**Analysis Date:** 2026-09-19

Scope: `zms-be/` only.

## Languages

**Primary:**

- Java 21 - all backend code (`zms-be/animal-service/src/main/java/it/zoo/animal/...`); set via `maven.compiler.release=21` in `zms-be/pom.xml`

**Secondary:**

- SQL (PostgreSQL dialect) - Flyway migrations in `zms-be/animal-service/src/main/resources/db/migration/` (`V1__create_animals_table.sql`, `V2__add_audit_columns.sql`)

## Runtime

**Environment:**

- JVM 21 for build. Note: generated Dockerfiles `zms-be/animal-service/src/main/docker/Dockerfile.jvm` and `Dockerfile.legacy-jar` use `registry.access.redhat.com/ubi9/openjdk-17-runtime:1.24` (Java 17 runtime, mismatched with Java 21 target)
- Native images supported via `native` profile in `zms-be/animal-service/pom.xml` (`Dockerfile.native` on `ubi9/ubi-minimal:9.7`, `Dockerfile.native-micro` on `quay.io/quarkus/ubi9-quarkus-micro-image:2.0`)

**Package Manager:**

- Maven 3.9.16 via wrapper `zms-be/animal-service/mvnw` (wrapper 3.3.4, `zms-be/animal-service/.mvn/wrapper/maven-wrapper.properties`)
- Lockfile: Not applicable (Maven; versions pinned by `quarkus-bom`)

## Frameworks

**Core:**

- Quarkus 3.20.0 (`quarkus.platform.version` in `zms-be/pom.xml`, imported as `io.quarkus.platform:quarkus-bom`)
- Quarkus REST + Jackson (`quarkus-rest-jackson`) - JAX-RS endpoints, e.g. `zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`
- Hibernate ORM with Panache (`quarkus-hibernate-orm-panache`) - persistence
- ArC CDI (`quarkus-arc`) - dependency injection (`@ApplicationScoped`, `@Inject`)
- Hibernate Validator (`quarkus-hibernate-validator`) - `@Valid` on REST inputs

**Testing:**

- JUnit 5 (`quarkus-junit5`) - unit and `@QuarkusTest`
- Mockito (`mockito-junit-jupiter`, BOM-managed) - `@ExtendWith(MockitoExtension.class)` unit tests
- REST Assured (`io.rest-assured:rest-assured`) + Hamcrest - HTTP-level tests
- `quarkus-test-security` - `@TestSecurity` role simulation
- maven-surefire-plugin 3.5.6 / maven-failsafe-plugin 3.5.6 (integration tests)

**Build/Dev:**

- quarkus-maven-plugin 3.20.0 (goals `build`, `generate-code`, `generate-code-tests`)
- maven-compiler-plugin 3.15.0 with `-parameters` and MapStruct annotation processor

## Key Dependencies

**Critical:**

- `org.mapstruct:mapstruct` 1.5.5.Final (+ `mapstruct-processor`) - DTO mapping, e.g. `it.zoo.animal.infrastructure.rest.mapper.AnimalDtoMapper`
- `quarkus-jdbc-postgresql` - PostgreSQL driver
- `quarkus-flyway` - schema migrations (`migrate-at-start=true` in dev/test)
- `quarkus-oidc` - JWT bearer validation against Keycloak

**Infrastructure:**

- `quarkus-smallrye-openapi` - OpenAPI spec + Swagger UI; `@SecurityRequirement` annotations on resources

## Configuration

**Environment:**

- Single file `zms-be/animal-service/src/main/resources/application.properties`, profile-prefixed (`%dev.`, `%test.`)
- Default profile: `quarkus.oidc.enabled=false`; `%dev` enables OIDC, datasource, CORS
- `%dev.quarkus.devservices.enabled=false` - dev relies on docker-compose services, not Dev Services
- `%test` sets only `db-kind=postgresql` + Flyway (no URL) - tests rely on Quarkus Dev Services (Testcontainers/Docker) for the DB
- `hibernate-orm.database.generation=none` - schema owned solely by Flyway
- No environment-variable indirection: dev credentials are hardcoded in `application.properties`
- No `.env` files present in `zms-be/`

**Build:**

- `zms-be/pom.xml` - parent aggregator (groupId `it.zoo`, version `1.0.0-SNAPSHOT`); only module declared: `animal-service`
- `zms-be/animal-service/pom.xml` - service build and `native` profile
- `feeding-service/`, `health-service/`, `notification-service/` directories exist but are empty (not modules)

## Platform Requirements

**Development:**

- JDK 21, Docker (for `zms-be/infrastructure/docker-compose.yml` and test Dev Services)
- Run infra: `docker compose -f zms-be/infrastructure/docker-compose.yml up`; run service: `./mvnw quarkus:dev` in `zms-be/animal-service`
- Frontend dev origin assumed at `http://localhost:4200` (CORS)

**Production:**

- Not defined. Only Quarkus-generated Dockerfiles exist; no prod profile, deployment manifests, or CI in `zms-be/`

---

*Stack analysis: 2026-09-19*
