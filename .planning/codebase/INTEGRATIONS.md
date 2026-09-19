---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# External Integrations

**Analysis Date:** 2026-09-19

Scope: `zms-be/` only.

## APIs & External Services

**Identity (Keycloak):**

- Keycloak 26.0 (`quay.io/keycloak/keycloak:26.0`, `start-dev --import-realm`) - issues JWTs consumed by animal-service
  - SDK/Client: `quarkus-oidc` (`application-type=service`, bearer-token only)
  - Auth: `%dev.quarkus.oidc.client-id=animal-service` + client secret, both hardcoded in `zms-be/animal-service/src/main/resources/application.properties`

**Outbound HTTP / messaging:**

- None. No REST clients, Kafka, AMQP, or other messaging dependencies in `zms-be/animal-service/pom.xml`. `notification-service/` is an empty directory.

## Data Storage

**Databases:**

- PostgreSQL 16 (`postgres:16-alpine`, service `postgres-animal` in `zms-be/infrastructure/docker-compose.yml`), database `animal_db`, port 5432, named volume `animal_data`
  - Connection: `%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/animal_db` (hardcoded; no env var)
  - Client: Hibernate ORM with Panache + `quarkus-jdbc-postgresql`; schema via Flyway (`zms-be/animal-service/src/main/resources/db/migration/`)
- Test: Quarkus Dev Services PostgreSQL container (no URL configured under `%test`)
- Database-per-service pattern implied (`animal_db`); no DBs yet for other services

**File Storage:**

- Local filesystem only (none used)

**Caching:**

- None

## Authentication & Identity

**Auth Provider:**

- Keycloak, realm `zoo`, defined in `zms-be/infrastructure/keycloak/realm-export.json` (auto-imported, mounted read-only)
  - Clients: `animal-service` (confidential backend), `zms-fe` (frontend)
  - Realm roles: `zoo-admin`, `zoo-vet`, `zoo-keeper` - mirrored as constants in `zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/security/ZooRoles.java`
  - Roles read from `realm_access/roles` claim (`quarkus.oidc.roles.role-claim-path`)
  - Enforcement: `@RolesAllowed(ZooRoles.X)` on JAX-RS resources (`AnimalResource.java`)
  - Keycloak exposed at `http://localhost:8081` (container 8080); admin bootstrap user set in compose
  - OIDC is disabled in the default profile (`quarkus.oidc.enabled=false`) and enabled only in `%dev`; tests use `@TestSecurity`

## Monitoring & Observability

**Error Tracking:**

- None

**Logs:**

- Quarkus default JBoss LogManager (configured for surefire/failsafe in `zms-be/animal-service/pom.xml`); no custom logging config, no health/metrics extensions (`smallrye-health`, `micrometer` absent)

## CI/CD & Deployment

**Hosting:**

- Not configured. Container images buildable from `zms-be/animal-service/src/main/docker/Dockerfile.*`

**CI Pipeline:**

- None in `zms-be/`

## Environment Configuration

**Required env vars:**

- None consumed. All dev settings (DB URL/user/password, OIDC URL/client/secret) are literal values in `application.properties`; Docker Compose uses literal values too
- For non-dev deployment, supply `QUARKUS_DATASOURCE_*` and `QUARKUS_OIDC_*` overrides (Quarkus env mapping) - not yet defined

**Secrets location:**

- Dev-only secrets committed in plain text: `zms-be/animal-service/src/main/resources/application.properties`, `zms-be/infrastructure/docker-compose.yml`, `zms-be/infrastructure/keycloak/realm-export.json`

## Webhooks & Callbacks

**Incoming:**

- None. HTTP surface is the REST API in `zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`; CORS in `%dev` allows `http://localhost:4200`, methods `GET,POST,PUT,OPTIONS`, headers `Content-Type,Authorization`

**Outgoing:**

- None

---

*Integration audit: 2026-09-19*
