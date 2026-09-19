# Security Layer Design — animal-service

**Date:** 2026-09-19
**Phase:** 6 (new; roadmap `zms-be/docs/zoo-roadmap.html` currently stops at 5)
**Scope:** OIDC authentication, role-based authorization and actor auditing for `animal-service`

---

## Context

Phases 1-5 are complete for `animal-service`: domain, application (5 use cases),
infrastructure (Panache/Flyway persistence, 5 REST endpoints, MapStruct DTO mapping,
global exception mapper), 9 `@QuarkusTest` integration tests.

Current security state:
- `quarkus-oidc` dependency is already declared in `animal-service/pom.xml`
- `application.properties` contains `quarkus.oidc.enabled=false` (global)
- `zms-be/infrastructure/keycloak/` exists but is empty
- `docker-compose.yml` defines only `postgres-animal`
- `AnimalResource` has no security annotations; every endpoint is open
- No audit trail: `animals` has no record of who registered or modified a row

`zms-be/CLAUDE.md` already lists this phase as "Security — OIDC, JWT, `@RolesAllowed`".

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Role model | 3 realm roles: `zoo-admin`, `zoo-vet`, `zoo-keeper` | Matches the domain; exercises differentiated `@RolesAllowed` per endpoint |
| Dev auth server | Keycloak container + committed realm export | Reproducible, no manual realm setup |
| Test strategy | OIDC stays disabled in `%test`; authorization tested with `@TestSecurity` | `@RolesAllowed` is enforced by `quarkus-security`, independent of OIDC, so authorization is still real in tests without a Keycloak container. Keeps `quarkus.devservices.enabled=false` (existing project rule) intact |
| Actor propagation | Actor passed explicitly through use case signatures | Keeps the dependency rule `infrastructure → application → domain` explicit and auditable; alternatives (a `port/out` actor provider, or a JPA entity listener) hide request state behind ambient context and cannot be tested without `@QuarkusTest` |

### Rejected alternatives

- **`ActorProvider` port/out**: no churn on existing signatures, but models request-scoped
  state as an outbound port and forces every application test to mock one more collaborator.
- **Hibernate entity listener**: least code, but auditing becomes invisible to the domain
  and untestable outside `@QuarkusTest`.
- **Keycloak Dev Services in tests**: real JWT verification end to end, but slows every
  build and contradicts the existing `quarkus.devservices.enabled=false` rule.
- **Resource-level ownership (keeper sees only their enclosures)**: out of scope for this phase.

---

## Architecture

```
it.zoo.animal/
├── domain/
│   ├── model/Animal.java                    ← MODIFIED: createdBy, updatedBy
│   └── port/in/
│       ├── RegisterAnimalCommand.java       ← MODIFIED: performedBy field
│       ├── UpdateAnimalStatusUseCase.java   ← MODIFIED: performedBy argument
│       └── TransferAnimalUseCase.java       ← MODIFIED: performedBy argument
├── application/
│   ├── RegisterAnimalService.java           ← MODIFIED: sets createdBy + updatedBy
│   ├── UpdateAnimalStatusService.java       ← MODIFIED: sets updatedBy
│   └── TransferAnimalService.java           ← MODIFIED: sets updatedBy
└── infrastructure/
    ├── security/ZooRoles.java               ← NEW: role name constants
    ├── persistence/
    │   ├── AnimalEntity.java                ← MODIFIED: created_by, updated_by
    │   └── AnimalEntityMapper.java          ← MODIFIED
    └── rest/
        ├── AnimalResource.java              ← MODIFIED: @RolesAllowed, SecurityIdentity
        ├── SecurityExceptionMapper.java     ← NEW: 401/403 bodies
        ├── OpenApiConfig.java               ← NEW: bearer security scheme
        └── dto/AnimalResponse.java          ← MODIFIED: createdBy, updatedBy
```

The security adapter lives entirely in `infrastructure`. The token never leaves that layer:
`AnimalResource` extracts the principal name and passes it down as a plain `String`.
`domain` and `application` gain no framework dependency.

---

## Component 1: Keycloak dev infrastructure

`zms-be/infrastructure/docker-compose.yml` gains one service:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.0
  command: ["start-dev", "--import-realm"]
  environment:
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
  ports: ["8081:8080"]
  volumes:
    - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
```

Port 8081 avoids the Quarkus dev port (8080).

`zms-be/infrastructure/keycloak/realm-export.json` defines:
- realm `zoo`, enabled
- realm roles `zoo-admin`, `zoo-vet`, `zoo-keeper`
- client `animal-service`: confidential bearer resource server, standard flow off,
  secret `animal-service-dev-secret` (must match `%dev.quarkus.oidc.credentials.secret`)
- client `zms-fe`: public, direct access grants enabled, so a token can be obtained
  with a password grant from curl or Swagger UI during development
- users `admin`, `vet`, `keeper`, each with a fixed dev password and exactly one realm role

The realm export contains development credentials in clear text. It is a development
fixture only and must not be reused for any deployed environment.

---

## Component 2: OIDC configuration

`application.properties` keeps `quarkus.oidc.enabled=false` as the default value, so the
`%test` profile is unaffected, and adds a `%dev` block:

```properties
%dev.quarkus.oidc.enabled=true
%dev.quarkus.oidc.auth-server-url=http://localhost:8081/realms/zoo
%dev.quarkus.oidc.client-id=animal-service
%dev.quarkus.oidc.credentials.secret=animal-service-dev-secret
%dev.quarkus.oidc.application-type=service
%dev.quarkus.oidc.roles.role-claim-path=realm_access/roles
```

`role-claim-path` is required: Keycloak places realm roles under `realm_access.roles`,
while Quarkus OIDC reads the `groups` claim by default. Without it every authenticated
request fails authorization with 403.

CORS, development only, for the Angular frontend:

```properties
%dev.quarkus.http.cors=true
%dev.quarkus.http.cors.origins=http://localhost:4200
%dev.quarkus.http.cors.methods=GET,POST,PUT,OPTIONS
%dev.quarkus.http.cors.headers=Content-Type,Authorization
```

The exact CORS property names changed across Quarkus 3.x releases
(`quarkus.http.cors` vs `quarkus.http.cors.enabled`). The implementer must confirm the
spelling against the Quarkus 3.20.0 documentation rather than from memory, and verify
that a preflight `OPTIONS` request returns the expected headers.

---

## Component 3: Authorization matrix

| Endpoint | Allowed roles |
|---|---|
| `POST /animals` | `zoo-admin` |
| `GET /animals` | `zoo-admin`, `zoo-vet`, `zoo-keeper` |
| `GET /animals/{id}` | `zoo-admin`, `zoo-vet`, `zoo-keeper` |
| `PUT /animals/{id}/status` | `zoo-vet`, `zoo-admin` |
| `PUT /animals/{id}/transfer` | `zoo-keeper`, `zoo-admin` |

`@RolesAllowed` is applied to methods, never to the class — same convention as
`@Transactional`. Role names are compile-time constants in
`infrastructure/security/ZooRoles.java` so resources and tests share one definition:

```java
public final class ZooRoles {
    public static final String ADMIN = "zoo-admin";
    public static final String VET = "zoo-vet";
    public static final String KEEPER = "zoo-keeper";

    private ZooRoles() {}
}
```

No `quarkus.http.auth.permission` configuration is introduced. Because authorization is
annotation-driven, `/q/health` and `/q/openapi` remain public without extra configuration.

---

## Component 4: Actor auditing

### Domain

`Animal` gains `createdBy` and `updatedBy` (`String`, with getters and setters, no
annotations). No new domain logic: the actor is data, not a rule.

### Ports in

```java
public record RegisterAnimalCommand(String name, String species, boolean dangerous,
                                    Habitat habitat, UUID enclosureId,
                                    LocalDate arrivalDate, String performedBy) {}

Animal updateStatus(UUID id, AnimalStatus newStatus, String performedBy);
Animal transfer(UUID animalId, UUID targetEnclosureId, String performedBy);
```

`GetAnimalUseCase` and `ListAnimalsUseCase` are unchanged: reads are not audited.

### Application

- `RegisterAnimalService`: sets `createdBy` and `updatedBy` to `performedBy`
- `UpdateAnimalStatusService`, `TransferAnimalService`: set `updatedBy` only
- All three validate the actor explicitly before doing any work:

```java
if (performedBy == null || performedBy.isBlank()) {
    throw new InvalidAnimalDataException("Actor must not be blank");
}
```

Explicit `if` + `throw`, per the project rule that Bean Validation is confined to REST DTOs.

### REST adapter

`AnimalResource` receives `SecurityIdentity` through its constructor (a CDI normal-scoped
proxy, so constructor injection into an `@ApplicationScoped` bean is valid) and derives
the actor with `identity.getPrincipal().getName()`.

An anonymous `SecurityIdentity` has a `null` principal, so this call must never run on an
unauthenticated request. That is guaranteed by `@RolesAllowed` on the same methods: the
authorization check rejects anonymous callers before the method body executes. The
consequence for sequencing is that the REST wiring and the role annotations must land
together — see Implementation order.

### Persistence

`AnimalEntity` and `AnimalEntityMapper` carry the two new fields.
`V2__add_audit_columns.sql`:

```sql
ALTER TABLE animals ADD COLUMN created_by VARCHAR(100);
ALTER TABLE animals ADD COLUMN updated_by VARCHAR(100);
UPDATE animals SET created_by = 'system' WHERE created_by IS NULL;
ALTER TABLE animals ALTER COLUMN created_by SET NOT NULL;
```

Columns are added nullable, backfilled, then constrained, because the development database
may already hold rows. `updated_by` stays nullable: an animal registered and never modified
has no modifier.

### Response

`AnimalResponse` exposes `createdBy` and `updatedBy`. MapStruct maps both by name; no
mapper code changes.

---

## Component 5: 401 and 403 responses

`ZooExceptionMapper` is declared as `ExceptionMapper<RuntimeException>` with a 500
fallback. Quarkus security failures (`UnauthorizedException`, `ForbiddenException`,
`AuthenticationFailedException`) are `RuntimeException` subclasses, so without a more
specific mapper a denied request would be reported as
`500 {"message":"Internal server error"}`.

JAX-RS selects the most specific mapper, so the fix is a dedicated provider rather than a
change to the existing mapper. `infrastructure/rest/SecurityExceptionMapper.java` reuses
the same `{"message": "..."}` body shape:

| Exception | Status | Message |
|---|---|---|
| `AuthenticationFailedException`, `UnauthorizedException` | 401 | `Authentication required` |
| `ForbiddenException` | 403 | `Insufficient role` |

Messages are deliberately generic: they reveal neither which role is required nor whether
a token was expired, malformed or absent.

---

## Component 6: OpenAPI

`infrastructure/rest/OpenApiConfig.java` declares `@OpenAPIDefinition` plus:

```java
@SecurityScheme(securitySchemeName = "bearerAuth", type = SecuritySchemeType.HTTP,
                scheme = "bearer", bearerFormat = "JWT")
```

`AnimalResource` is annotated `@SecurityRequirement(name = "bearerAuth")`, so Swagger UI
renders the authorize control and forwards a pasted token.

---

## Testing

New test-scoped dependency: `io.quarkus:quarkus-test-security`. No version in the child
POM; the parent BOM manages it.

**Domain** — no new tests. Auditing adds fields, not behaviour.

**Application** — the 5 existing test classes need mechanical signature updates (one extra
constructor field in `RegisterAnimalCommand`, one extra argument on the two other write
use cases). Three new tests, one per writing service:
`shouldThrowWhenPerformedByIsBlank`. Still JUnit 5 + Mockito, no `@QuarkusTest`.

**Infrastructure** — the 9 tests in `AnimalResourceIT` are annotated with the least
privileged role that the exercised endpoint accepts, so the tests double as documentation
of who may do what:

| Test target | Annotation |
|---|---|
| `POST /animals` (and any test that seeds data through it) | `@TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})` |
| `GET /animals`, `GET /animals/{id}` | `@TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})` |
| `PUT /{id}/status` | `@TestSecurity(user = "vet", roles = {ZooRoles.VET})` |
| `PUT /{id}/transfer` | `@TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})` |

Several existing tests seed data with a helper that posts an animal and then exercise a
different endpoint. Those need the union of the roles involved (e.g.
`roles = {ZooRoles.ADMIN, ZooRoles.VET}`), since `@TestSecurity` applies one identity to
the whole test method.

New `AnimalSecurityIT` covers the negative matrix:

| Case | Expected |
|---|---|
| Any endpoint, no `@TestSecurity` (anonymous) | 401 |
| `PUT /{id}/status` as `zoo-keeper` | 403 |
| `PUT /{id}/transfer` as `zoo-vet` | 403 |
| `POST /animals` as `zoo-vet` | 403 |
| `PUT /{id}/status` as `zoo-vet` | 200 |
| `POST /animals` as `zoo-admin`, then read back | 201, `createdBy` equals the token user |

Anonymous requests yield 401 (no identity) while authenticated requests with the wrong role
yield 403 — the distinction is asserted, not assumed.

**Manual verification (dev profile)** — not covered by automated tests, therefore an
explicit implementation step: start `docker compose up`, obtain a token for each demo user
via password grant against the `zms-fe` client, and confirm that the role matrix holds
against a real Keycloak-issued JWT and that a request without a token returns 401.

---

## Implementation order

1. **Auditing, inner layers** — TDD: domain → ports in → application (+ updated and new
   tests) → persistence + `V2` migration. No REST change yet, so the build stays green
   on its own and this step is independent of Keycloak.
2. **REST slice, atomic** — `ZooRoles`, `@RolesAllowed` on `AnimalResource`,
   `SecurityIdentity` wiring, `AnimalResponse` fields, `SecurityExceptionMapper`, and
   `@TestSecurity` on the 9 existing tests, all in one commit. These cannot be split:
   reading the principal without `@RolesAllowed` would hit a `null` principal on
   anonymous requests, annotating without `@TestSecurity` would fail every existing test
   with 401, and annotating without the mapper would report denials as 500.
3. **`AnimalSecurityIT`** — the negative matrix above.
4. **Dev infrastructure** — realm export, `docker-compose` Keycloak service, `%dev` OIDC
   and CORS configuration, OpenAPI security scheme.
5. **Manual dev-profile verification** against real Keycloak-issued tokens.

Steps 1-3 keep `mvnw verify` green at every commit boundary. Step 4 changes only the
`%dev` profile and cannot affect the test suite; step 5 is the only check that exercises
real JWT verification and role-claim mapping, which no automated test in this phase covers.

---

## Out of scope

- Resource-level authorization (per-enclosure ownership, tenant isolation)
- Token propagation to other services (`health-service`, `feeding-service` are not started)
- Production OIDC configuration, secret management, TLS
- Rate limiting, audit log table, security event streaming
- Frontend authentication flow in `zms-fe`
