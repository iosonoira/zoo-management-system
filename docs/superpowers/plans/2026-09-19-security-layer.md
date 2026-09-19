# Security Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OIDC authentication, per-endpoint role authorization and actor auditing to `animal-service`.

**Architecture:** Security lives entirely in the `infrastructure` layer: `AnimalResource` enforces roles with `@RolesAllowed` and extracts the caller from `SecurityIdentity`, then passes the actor down as a plain `String` through the use case signatures. The domain and application layers gain audit data but no framework dependency. OIDC is configured only for the `%dev` profile against a Keycloak container; in `%test` OIDC stays disabled and authorization is exercised with `@TestSecurity`, which works because `@RolesAllowed` is enforced by `quarkus-security` independently of OIDC.

**Tech Stack:** Java 21, Quarkus 3.20.0, `quarkus-oidc`, `quarkus-test-security`, Keycloak 26, Flyway, Hibernate ORM Panache, RestAssured, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-09-19-security-layer-design.md`

## Global Constraints

- Java 21, Quarkus 3.20.0. **Never** put a version in `animal-service/pom.xml`; the parent BOM (`quarkus-bom`) manages all versions.
- Dependency rule, never violated: `infrastructure → application → domain`.
- `domain/` has ZERO framework annotations. No Jakarta, no Quarkus, no Bean Validation.
- `application/`: only `@ApplicationScoped` and `@Transactional`; `@Transactional` on methods only, never on the class. Constructor injection only, never `@Inject` on a field.
- Validation in `application/` is explicit `if` + `throw`, never Bean Validation annotations.
- Bean Validation (`@NotNull`, `@Valid`, …) is allowed **only** on DTOs in `infrastructure/rest/`.
- Test conventions: domain → plain JUnit 5; application → JUnit 5 + Mockito, no `@QuarkusTest`; infrastructure → `@QuarkusTest` + RestAssured. Test names follow `should{ExpectedBehavior}[When{Condition}]`.
- Exact role strings: `zoo-admin`, `zoo-vet`, `zoo-keeper`.
- Working directory for all commands: `zms-be/animal-service`. Maven wrapper: `.\mvnw.cmd` (Windows PowerShell).
- **Docker Desktop must be running**: `%test` uses Dev Services (Testcontainers) for PostgreSQL.
- Commit messages: Conventional Commits, no `Co-Authored-By` trailer.
- Branch: `feature/security-layer` (already created; the spec commit is its first commit).

---

## File Structure

### Created

| File | Responsibility |
|---|---|
| `src/main/java/it/zoo/animal/infrastructure/security/ZooRoles.java` | The three role name constants, shared by resource and tests |
| `src/main/java/it/zoo/animal/infrastructure/rest/SecurityExceptionMapper.java` | Maps Quarkus security failures to 401/403 with the project error body |
| `src/main/java/it/zoo/animal/infrastructure/rest/OpenApiConfig.java` | Declares the `bearerAuth` OpenAPI security scheme |
| `src/main/resources/db/migration/V2__add_audit_columns.sql` | Adds `created_by` / `updated_by` to `animals` |
| `src/test/java/it/zoo/animal/infrastructure/rest/AnimalSecurityIT.java` | Asserts the authorization matrix: 401 anonymous, 403 wrong role, 2xx right role |
| `zms-be/infrastructure/keycloak/realm-export.json` | Dev realm: 3 roles, 2 clients, 3 demo users |

### Modified

| File | Change |
|---|---|
| `pom.xml` | Adds `quarkus-test-security` (test scope, no version) |
| `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java` | `@RolesAllowed` per method, `SecurityIdentity` injection, actor passed to use cases, `@SecurityRequirement` |
| `src/main/java/it/zoo/animal/domain/model/Animal.java` | Adds `createdBy` / `updatedBy` fields plus accessors |
| `src/main/java/it/zoo/animal/domain/port/in/RegisterAnimalCommand.java` | Adds `performedBy` component |
| `src/main/java/it/zoo/animal/domain/port/in/UpdateAnimalStatusUseCase.java` | Adds `performedBy` parameter |
| `src/main/java/it/zoo/animal/domain/port/in/TransferAnimalUseCase.java` | Adds `performedBy` parameter |
| `src/main/java/it/zoo/animal/application/RegisterAnimalService.java` | Validates actor, sets `createdBy` + `updatedBy` |
| `src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java` | Validates actor, sets `updatedBy` |
| `src/main/java/it/zoo/animal/application/TransferAnimalService.java` | Validates actor, sets `updatedBy` |
| `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java` | Adds the two audit columns |
| `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java` | Maps the two audit fields both ways |
| `src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java` | Exposes `createdBy` / `updatedBy` |
| `src/main/resources/application.properties` | `%dev` OIDC block, `%dev` CORS block |
| `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java` | Class-level `@TestSecurity`, one new audit assertion |
| `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java` | Audit field assertions |
| `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java` | Command arity, new blank-actor test |
| `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java` | Signature, new blank-actor test |
| `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java` | Signature, new blank-actor test |
| `zms-be/infrastructure/docker-compose.yml` | Adds the `keycloak` service |
| `zms-be/CLAUDE.md` | Status section: phase 6 done |

`GetAnimalServiceTest` and `ListAnimalsServiceTest` are **not** touched: reads are not audited and the `Animal` constructor keeps its current 8-argument signature.

### Task order rationale

Security lands before auditing, which is the reverse of the order sketched in the spec's
"Implementation order" section. Reason: `AnimalResource` builds a 6-component
`RegisterAnimalCommand` and calls `updateStatus(id, status)` / `transfer(id, target)`.
Adding `performedBy` to those signatures breaks compilation of the main sources until the
resource is updated, and the resource can only read `identity.getPrincipal().getName()`
safely once `@RolesAllowed` rejects anonymous callers (an anonymous `SecurityIdentity` has
a `null` principal). Authorization first, then auditing, keeps every task compiling and
green.

---

## Task 1: Role constants, endpoint authorization, 401/403 mapper

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/security/ZooRoles.java`
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/SecurityExceptionMapper.java`
- Modify: `pom.xml` (dependencies block)
- Modify: `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`
- Test: `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `it.zoo.animal.infrastructure.security.ZooRoles` with `public static final String ADMIN = "zoo-admin"`, `VET = "zoo-vet"`, `KEEPER = "zoo-keeper"`. Task 2 and Task 5 reference these constants.

This task must land as one commit. Splitting it breaks the build: annotating the resource without `@TestSecurity` makes all 9 existing tests fail with 401, and adding roles without the mapper turns denials into 500 responses.

- [ ] **Step 1: Add the test-security dependency**

In `pom.xml`, inside `<dependencies>`, directly after the `rest-assured` dependency:

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-test-security</artifactId>
            <scope>test</scope>
        </dependency>
```

No `<version>` — the parent BOM provides it.

- [ ] **Step 2: Annotate the existing integration tests with an admin identity**

`zoo-admin` is accepted by all five endpoints, so one class-level identity covers all 9
existing tests without touching a single test body. The per-role matrix is asserted
separately in Task 2.

In `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`, add the imports:

```java
import io.quarkus.test.security.TestSecurity;
import it.zoo.animal.infrastructure.security.ZooRoles;
```

and change the class declaration from:

```java
@QuarkusTest
class AnimalResourceIT {
```

to:

```java
@QuarkusTest
@TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
class AnimalResourceIT {
```

- [ ] **Step 3: Run the integration tests to verify they fail**

```powershell
.\mvnw.cmd verify -Dit.test=AnimalResourceIT
```

Expected: FAIL. Compilation error on `ZooRoles` — `package it.zoo.animal.infrastructure.security does not exist`.

- [ ] **Step 4: Create the role constants**

`src/main/java/it/zoo/animal/infrastructure/security/ZooRoles.java`:

```java
package it.zoo.animal.infrastructure.security;

public final class ZooRoles {

    public static final String ADMIN = "zoo-admin";
    public static final String VET = "zoo-vet";
    public static final String KEEPER = "zoo-keeper";

    private ZooRoles() {}
}
```

- [ ] **Step 5: Create the security exception mapper**

`src/main/java/it/zoo/animal/infrastructure/rest/SecurityExceptionMapper.java`:

```java
package it.zoo.animal.infrastructure.rest;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    @Override
    public Response toResponse(SecurityException exception) {
        if (exception instanceof ForbiddenException) {
            return errorResponse(403, "Insufficient role");
        }
        if (exception instanceof UnauthorizedException
                || exception instanceof AuthenticationFailedException) {
            return errorResponse(401, "Authentication required");
        }
        return errorResponse(403, "Access denied");
    }

    private Response errorResponse(int status, String message) {
        return Response.status(status)
                .entity(new ZooExceptionMapper.ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

Notes for the implementer:
- `io.quarkus.security.UnauthorizedException`, `ForbiddenException` and
  `AuthenticationFailedException` all extend `java.lang.SecurityException`, so one mapper
  covers them. `SecurityException` is more specific than the `RuntimeException` handled by
  the existing `ZooExceptionMapper`, and JAX-RS picks the most specific mapper — which is
  exactly why this class is needed: without it, a denied request would be reported as
  `500 {"message":"Internal server error"}`.
- The body reuses `ZooExceptionMapper.ErrorResponse`, so all error responses share one shape.
- Messages are deliberately generic. Do not add the required role or the reason for token
  rejection to the response.

- [ ] **Step 6: Annotate the resource endpoints**

In `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`, add the imports:

```java
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
```

Then add exactly one annotation per endpoint method, leaving everything else untouched:

```java
    @POST
    @RolesAllowed(ZooRoles.ADMIN)
    public Response register(@Valid RegisterAnimalRequest request) {
```

```java
    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public List<AnimalResponse> listAll() {
```

```java
    @GET
    @Path("/{id}")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public AnimalResponse getById(@PathParam("id") UUID id) {
```

```java
    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public AnimalResponse updateStatus(@PathParam("id") UUID id,
```

```java
    @PUT
    @Path("/{id}/transfer")
    @RolesAllowed({ZooRoles.KEEPER, ZooRoles.ADMIN})
    public AnimalResponse transfer(@PathParam("id") UUID id,
```

Never annotate the class — same rule as `@Transactional`.

- [ ] **Step 7: Run the integration tests to verify they pass**

```powershell
.\mvnw.cmd verify -Dit.test=AnimalResourceIT
```

Expected: PASS, 9 tests.

If instead requests come back 401 despite `@TestSecurity`, check that
`quarkus-test-security` resolved:

```powershell
.\mvnw.cmd dependency:tree -Dincludes=io.quarkus:quarkus-test-security
```

- [ ] **Step 8: Run the whole suite**

```powershell
.\mvnw.cmd verify
```

Expected: PASS — 1 domain test class, 5 application test classes, `AnimalEntityMapperTest`, `AnimalResourceIT`.

- [ ] **Step 9: Commit**

```bash
git add pom.xml src/main/java/it/zoo/animal/infrastructure/security/ZooRoles.java src/main/java/it/zoo/animal/infrastructure/rest/SecurityExceptionMapper.java src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java
git commit -m "feat(animal-service): enforce role-based authorization on REST endpoints"
```

---

## Task 2: Authorization matrix integration test

**Files:**
- Create: `src/test/java/it/zoo/animal/infrastructure/rest/AnimalSecurityIT.java`

**Interfaces:**
- Consumes: `ZooRoles.ADMIN`, `ZooRoles.VET`, `ZooRoles.KEEPER` from Task 1; `AnimalEntity` and its setters from the existing persistence package.
- Produces: nothing consumed by later tasks.

Note on seeding: the 403 and 401 cases need no data, because authorization runs before the
resource method body — a random UUID is enough. The positive case needs one row, inserted
directly through `EntityManager` in `@BeforeEach`, because `@TestSecurity` binds one
identity to the whole test method and seeding over HTTP would need the admin identity.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/animal/infrastructure/rest/AnimalSecurityIT.java`:

```java
package it.zoo.animal.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.infrastructure.persistence.AnimalEntity;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AnimalSecurityIT {

    private static final UUID SEEDED_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENCLOSURE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String ANIMAL_JSON = "{"
            + "  \"name\": \"Leo\","
            + "  \"species\": \"Lion\","
            + "  \"dangerous\": false,"
            + "  \"habitat\": \"TERRESTRIAL\","
            + "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\","
            + "  \"arrivalDate\": \"2024-01-15\""
            + "}";

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM AnimalEntity").executeUpdate();
            AnimalEntity entity = new AnimalEntity();
            entity.setId(SEEDED_ID);
            entity.setName("Leo");
            entity.setSpecies("Lion");
            entity.setDangerous(false);
            entity.setHabitat(Habitat.TERRESTRIAL);
            entity.setEnclosureId(ENCLOSURE_ID);
            entity.setArrivalDate(LocalDate.of(2024, 1, 15));
            entity.setStatus(AnimalStatus.HEALTHY);
            em.persist(entity);
        });
    }

    @Test
    void shouldReturn401WhenListingAnimalsAnonymously() {
        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    void shouldReturn401WhenRegisteringAnimalAnonymously() {
        given()
            .contentType(ContentType.JSON)
            .body(ANIMAL_JSON)
        .when()
            .post("/animals")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldReturn403WhenVetRegistersAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body(ANIMAL_JSON)
        .when()
            .post("/animals")
        .then()
            .statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn403WhenKeeperUpdatesStatus() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"UNDER_OBSERVATION\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/status")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldReturn403WhenVetTransfersAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"660e8400-e29b-41d4-a716-446655440001\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/transfer")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldUpdateStatusWhenCallerIsVet() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"UNDER_OBSERVATION\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("UNDER_OBSERVATION"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldTransferAnimalWhenCallerIsKeeper() {
        String target = "660e8400-e29b-41d4-a716-446655440001";

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"" + target + "\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/transfer")
        .then()
            .statusCode(200)
            .body("enclosureId", equalTo(target));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldListAnimalsWhenCallerIsKeeper() {
        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1));
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

```powershell
.\mvnw.cmd verify -Dit.test=AnimalSecurityIT
```

Expected: PASS, 8 tests. This test asserts behaviour already implemented in Task 1, so a green run on the first attempt is the expected outcome.

Two failures worth recognising:
- anonymous request returns **200** instead of 401 → `@RolesAllowed` is not being enforced; check that the annotations from Task 1 landed on the methods.
- wrong-role request returns **500** with `"Internal server error"` → `SecurityExceptionMapper` is not registered; check the `@Provider` annotation.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/it/zoo/animal/infrastructure/rest/AnimalSecurityIT.java
git commit -m "test(animal-service): add AnimalSecurityIT authorization matrix"
```

---

## Task 3: Actor auditing through domain, application and REST

**Files:**
- Modify: `src/main/java/it/zoo/animal/domain/model/Animal.java`
- Modify: `src/main/java/it/zoo/animal/domain/port/in/RegisterAnimalCommand.java`
- Modify: `src/main/java/it/zoo/animal/domain/port/in/UpdateAnimalStatusUseCase.java`
- Modify: `src/main/java/it/zoo/animal/domain/port/in/TransferAnimalUseCase.java`
- Modify: `src/main/java/it/zoo/animal/application/RegisterAnimalService.java`
- Modify: `src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java`
- Modify: `src/main/java/it/zoo/animal/application/TransferAnimalService.java`
- Modify: `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`
- Test: `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java`
- Test: `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java`
- Test: `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java`

**Interfaces:**
- Consumes: `@RolesAllowed` already on every endpoint (Task 1) — this is what guarantees `identity.getPrincipal()` is non-null inside the resource methods.
- Produces:
  - `Animal.getCreatedBy()` / `setCreatedBy(String)`, `Animal.getUpdatedBy()` / `setUpdatedBy(String)`
  - `RegisterAnimalCommand(String name, String species, boolean dangerous, Habitat habitat, UUID enclosureId, LocalDate arrivalDate, String performedBy)`
  - `UpdateAnimalStatusUseCase.updateStatus(UUID id, AnimalStatus newStatus, String performedBy)`
  - `TransferAnimalUseCase.transfer(UUID animalId, UUID targetEnclosureId, String performedBy)`
  - Task 4 persists these fields; Task 5 exposes them over REST.

The `Animal` 8-argument constructor stays **unchanged**; the audit fields are set through
setters. This is what keeps `GetAnimalServiceTest`, `ListAnimalsServiceTest` and the
existing `AnimalEntityMapperTest` construction sites compiling.

- [ ] **Step 1: Write the failing tests — RegisterAnimalServiceTest**

In `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java`, every
`new RegisterAnimalCommand(...)` gains a final `"vet"` argument, the happy-path test gains
audit assertions, and one new test is added.

Replace the `shouldRegisterAnimalWithHealthyStatus` test with:

```java
    @Test
    void shouldRegisterAnimalWithHealthyStatus() {
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        Animal result = service.register(cmd);

        assertEquals(AnimalStatus.HEALTHY, result.getStatus());
        assertEquals("Leo", result.getName());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());
    }
```

(The stub now echoes the saved animal, so the assertions observe what the service built
rather than a fixture.)

In the seven remaining tests, append `, "vet"` as the last constructor argument, for example:

```java
    @Test
    void shouldThrowWhenNameIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }
```

Do the same for `shouldThrowWhenNameIsNull`, `shouldThrowWhenSpeciesIsBlank`,
`shouldThrowWhenSpeciesIsNull`, `shouldThrowWhenHabitatIsNull`,
`shouldThrowWhenEnclosureIdIsNull` and `shouldThrowWhenArrivalDateIsNull`.

Then add the new test:

```java
    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "  ");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }
```

- [ ] **Step 2: Write the failing tests — UpdateAnimalStatusServiceTest**

In `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java`, add
`, "vet"` to each `service.updateStatus(...)` call, assert the actor on the happy path, and
add the blank-actor test.

Replace `shouldUpdateStatus` with:

```java
    @Test
    void shouldUpdateStatus() {
        UUID id = UUID.randomUUID();
        Animal animal = healthyAnimal(id);
        when(repository.findById(id)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Animal result = service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, "vet");

        assertEquals(AnimalStatus.UNDER_OBSERVATION, result.getStatus());
        assertEquals("vet", result.getUpdatedBy());
    }
```

Update the two remaining calls:

```java
        assertThrows(AnimalNotFoundException.class,
                () -> service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, "vet"));
```

```java
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateStatus(id, AnimalStatus.HEALTHY, "vet"));
```

Add:

```java
    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID id = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, ""));
    }
```

This needs one extra import in that file:

```java
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
```

Note: the actor check must run **before** the repository lookup, otherwise this test needs
a `findById` stub and Mockito strictness fails the run with `UnnecessaryStubbingException`.

- [ ] **Step 3: Write the failing tests — TransferAnimalServiceTest**

In `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java`, replace
`shouldTransferAnimalToNewEnclosure` with:

```java
    @Test
    void shouldTransferAnimalToNewEnclosure() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        Animal animal = new Animal(animalId, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(animalId)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Animal result = service.transfer(animalId, newEnclosureId, "keeper");

        assertEquals(newEnclosureId, result.getEnclosureId());
        assertEquals("keeper", result.getUpdatedBy());
    }
```

Update the other three calls to pass `"keeper"` as the third argument:

```java
        assertThrows(AnimalNotFoundException.class,
                () -> service.transfer(animalId, newEnclosureId, "keeper"));
```

```java
        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, null, "keeper"));
```

```java
        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, newEnclosureId, "keeper"));
```

Add:

```java
    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, newEnclosureId, null));
    }
```

- [ ] **Step 4: Run the application tests to verify they fail**

```powershell
.\mvnw.cmd test -Dtest="RegisterAnimalServiceTest,UpdateAnimalStatusServiceTest,TransferAnimalServiceTest"
```

Expected: FAIL at compilation — `constructor RegisterAnimalCommand cannot be applied to given types` and `method updateStatus cannot be applied to given types`.

- [ ] **Step 5: Add the audit fields to the domain model**

In `src/main/java/it/zoo/animal/domain/model/Animal.java`, after the `status` field:

```java
    private String createdBy;
    private String updatedBy;
```

After `getStatus()`:

```java
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
```

After `setStatus(...)`:

```java
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
```

Do not change the existing constructors and do not add any annotation.

- [ ] **Step 6: Extend the inbound ports**

`src/main/java/it/zoo/animal/domain/port/in/RegisterAnimalCommand.java`:

```java
package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.enums.Habitat;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterAnimalCommand(
    String name,
    String species,
    boolean dangerous,
    Habitat habitat,
    UUID enclosureId,
    LocalDate arrivalDate,
    String performedBy
) {}
```

`src/main/java/it/zoo/animal/domain/port/in/UpdateAnimalStatusUseCase.java`:

```java
public interface UpdateAnimalStatusUseCase {
    Animal updateStatus(UUID id, AnimalStatus newStatus, String performedBy);
}
```

`src/main/java/it/zoo/animal/domain/port/in/TransferAnimalUseCase.java`:

```java
public interface TransferAnimalUseCase {
    Animal transfer(UUID animalId, UUID targetEnclosureId, String performedBy);
}
```

Leave the imports in those two files as they are.

- [ ] **Step 7: Set the actor in RegisterAnimalService**

In `src/main/java/it/zoo/animal/application/RegisterAnimalService.java`, add the actor
check as the **first** validation in `register`:

```java
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }
```

and replace the `return repository.save(animal);` tail so the animal carries the actor:

```java
        Animal animal = new Animal(
                UUID.randomUUID(),
                cmd.name(),
                cmd.species(),
                cmd.dangerous(),
                cmd.habitat(),
                cmd.enclosureId(),
                cmd.arrivalDate(),
                AnimalStatus.HEALTHY
        );
        animal.setCreatedBy(cmd.performedBy());
        animal.setUpdatedBy(cmd.performedBy());
        return repository.save(animal);
```

- [ ] **Step 8: Set the actor in UpdateAnimalStatusService**

Replace the `updateStatus` method in
`src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java` with:

```java
    @Override
    @Transactional
    public Animal updateStatus(UUID id, AnimalStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }

        Animal animal = repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));

        if (!animal.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(animal.getStatus(), newStatus);
        }

        animal.setStatus(newStatus);
        animal.setUpdatedBy(performedBy);
        return repository.save(animal);
    }
```

and add the import:

```java
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
```

- [ ] **Step 9: Set the actor in TransferAnimalService**

Replace the `transfer` method in
`src/main/java/it/zoo/animal/application/TransferAnimalService.java` with:

```java
    @Override
    @Transactional
    public Animal transfer(UUID animalId, UUID targetEnclosureId, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }
        if (targetEnclosureId == null) {
            throw new InvalidAnimalDataException("Target enclosure ID must not be null");
        }

        Animal animal = repository.findById(animalId)
                .orElseThrow(() -> new AnimalNotFoundException(animalId));

        if (!animal.canBeTransferred()) {
            throw new InvalidAnimalDataException("Cannot transfer a deceased animal");
        }

        animal.setEnclosureId(targetEnclosureId);
        animal.setUpdatedBy(performedBy);
        return repository.save(animal);
    }
```

- [ ] **Step 10: Run the application tests to verify they pass**

```powershell
.\mvnw.cmd test -Dtest="RegisterAnimalServiceTest,UpdateAnimalStatusServiceTest,TransferAnimalServiceTest"
```

Expected: PASS — 9 + 4 + 5 tests.

- [ ] **Step 11: Wire the actor in the REST adapter**

In `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`, add the import:

```java
import io.quarkus.security.identity.SecurityIdentity;
```

Add the field and the constructor parameter (constructor injection only):

```java
    private final AnimalDtoMapper mapper;
    private final SecurityIdentity identity;

    public AnimalResource(RegisterAnimalUseCase registerAnimal,
                          GetAnimalUseCase getAnimal,
                          ListAnimalsUseCase listAnimals,
                          UpdateAnimalStatusUseCase updateAnimalStatus,
                          TransferAnimalUseCase transferAnimal,
                          AnimalDtoMapper mapper,
                          SecurityIdentity identity) {
        this.registerAnimal = registerAnimal;
        this.getAnimal = getAnimal;
        this.listAnimals = listAnimals;
        this.updateAnimalStatus = updateAnimalStatus;
        this.transferAnimal = transferAnimal;
        this.mapper = mapper;
        this.identity = identity;
    }
```

Add a private helper at the end of the class:

```java
    private String currentActor() {
        return identity.getPrincipal().getName();
    }
```

`identity.getPrincipal()` is null for an anonymous caller, so `currentActor()` must only be
called from methods carrying `@RolesAllowed` — which is every endpoint after Task 1.

Then pass the actor in the three writing endpoints:

```java
    @POST
    @RolesAllowed(ZooRoles.ADMIN)
    public Response register(@Valid RegisterAnimalRequest request) {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                request.name(), request.species(), request.dangerous(),
                request.habitat(), request.enclosureId(), request.arrivalDate(),
                currentActor()
        );
        Animal animal = registerAnimal.register(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(animal))
                .build();
    }
```

```java
    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public AnimalResponse updateStatus(@PathParam("id") UUID id,
                                       @Valid UpdateAnimalStatusRequest request) {
        return mapper.toResponse(updateAnimalStatus.updateStatus(id, request.status(), currentActor()));
    }
```

```java
    @PUT
    @Path("/{id}/transfer")
    @RolesAllowed({ZooRoles.KEEPER, ZooRoles.ADMIN})
    public AnimalResponse transfer(@PathParam("id") UUID id,
                                   @Valid TransferAnimalRequest request) {
        return mapper.toResponse(transferAnimal.transfer(id, request.targetEnclosureId(), currentActor()));
    }
```

- [ ] **Step 12: Run the whole suite**

```powershell
.\mvnw.cmd verify
```

Expected: PASS. The audit values are not persisted yet (Task 4) and not exposed yet (Task 5), so no integration test observes them — but nothing regresses.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/it/zoo/animal/domain src/main/java/it/zoo/animal/application src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java src/test/java/it/zoo/animal/application
git commit -m "feat(animal-service): propagate acting user from token to use cases"
```

---

## Task 4: Persist the audit columns

**Files:**
- Create: `src/main/resources/db/migration/V2__add_audit_columns.sql`
- Modify: `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java`
- Modify: `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java`
- Test: `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java`

**Interfaces:**
- Consumes: `Animal.getCreatedBy()`, `Animal.setCreatedBy(String)`, `Animal.getUpdatedBy()`, `Animal.setUpdatedBy(String)` from Task 3.
- Produces: `AnimalEntity.getCreatedBy()` / `setCreatedBy(String)`, `AnimalEntity.getUpdatedBy()` / `setUpdatedBy(String)`; DB columns `animals.created_by` (NOT NULL) and `animals.updated_by` (nullable).

- [ ] **Step 1: Write the failing test**

In `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java`,
add the two audit assertions to `shouldMapEntityToDomain`:

```java
        assertEquals(entity.getCreatedBy(), domain.getCreatedBy());
        assertEquals(entity.getUpdatedBy(), domain.getUpdatedBy());
```

Replace `shouldMapDomainToEntity` with:

```java
    @Test
    void shouldMapDomainToEntity() {
        Animal domain = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, enclosureId, date, AnimalStatus.HEALTHY);
        domain.setCreatedBy("admin");
        domain.setUpdatedBy("vet");

        AnimalEntity entity = AnimalEntityMapper.toEntity(domain);

        assertEquals(domain.getId(), entity.getId());
        assertEquals(domain.getName(), entity.getName());
        assertEquals(domain.getSpecies(), entity.getSpecies());
        assertEquals(domain.isDangerous(), entity.isDangerous());
        assertEquals(domain.getHabitat(), entity.getHabitat());
        assertEquals(domain.getEnclosureId(), entity.getEnclosureId());
        assertEquals(domain.getArrivalDate(), entity.getArrivalDate());
        assertEquals(domain.getStatus(), entity.getStatus());
        assertEquals("admin", entity.getCreatedBy());
        assertEquals("vet", entity.getUpdatedBy());
    }
```

and extend the `buildEntity()` helper:

```java
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("vet");
```

- [ ] **Step 2: Run the test to verify it fails**

```powershell
.\mvnw.cmd test -Dtest=AnimalEntityMapperTest
```

Expected: FAIL at compilation — `cannot find symbol: method setCreatedBy(String)` on `AnimalEntity`.

- [ ] **Step 3: Write the migration**

`src/main/resources/db/migration/V2__add_audit_columns.sql`:

```sql
ALTER TABLE animals ADD COLUMN created_by VARCHAR(100);
ALTER TABLE animals ADD COLUMN updated_by VARCHAR(100);

UPDATE animals SET created_by = 'system' WHERE created_by IS NULL;

ALTER TABLE animals ALTER COLUMN created_by SET NOT NULL;
```

Columns are added nullable, backfilled, then constrained: the development database may
already hold rows registered before auditing existed. `updated_by` stays nullable by
design — the column has no meaning for a row that was never modified. Never edit
`V1__create_animals_table.sql`; Flyway checksums applied migrations.

- [ ] **Step 4: Add the entity columns**

In `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java`, after the
`status` field:

```java
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
```

After `getStatus()`:

```java
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
```

After `setStatus(...)`:

```java
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
```

- [ ] **Step 5: Map the fields both ways**

In `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java`,
`toDomain` keeps using the 8-argument constructor and then sets the audit fields:

```java
    public static Animal toDomain(AnimalEntity entity) {
        Animal animal = new Animal(
                entity.getId(),
                entity.getName(),
                entity.getSpecies(),
                entity.isDangerous(),
                entity.getHabitat(),
                entity.getEnclosureId(),
                entity.getArrivalDate(),
                entity.getStatus()
        );
        animal.setCreatedBy(entity.getCreatedBy());
        animal.setUpdatedBy(entity.getUpdatedBy());
        return animal;
    }
```

In `toEntity`, add before the `return`:

```java
        entity.setCreatedBy(animal.getCreatedBy());
        entity.setUpdatedBy(animal.getUpdatedBy());
```

- [ ] **Step 6: Run the test to verify it passes**

```powershell
.\mvnw.cmd test -Dtest=AnimalEntityMapperTest
```

Expected: PASS, 2 tests.

- [ ] **Step 7: Run the whole suite**

```powershell
.\mvnw.cmd verify
```

Expected: PASS. If the integration tests fail with a not-null constraint violation on
`created_by`, the actor is not reaching the entity — re-check Step 5 and Task 3 Step 7.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V2__add_audit_columns.sql src/main/java/it/zoo/animal/infrastructure/persistence src/test/java/it/zoo/animal/infrastructure/persistence
git commit -m "feat(animal-service): persist created_by and updated_by audit columns"
```

---

## Task 5: Expose the audit fields over REST

**Files:**
- Modify: `src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java`
- Test: `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`

**Interfaces:**
- Consumes: persisted audit fields from Task 4; the class-level `@TestSecurity(user = "admin", …)` added in Task 1.
- Produces: `AnimalResponse.createdBy()` / `AnimalResponse.updatedBy()` in every REST payload.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`, before
the private `postAnimal` helper:

```java
    @Test
    void shouldRecordActingUserWhenRegisteringAnimal() {
        String id = postAnimal("Leo", "Lion")
            .body("createdBy", equalTo("admin"))
            .body("updatedBy", equalTo("admin"))
            .extract().path("id");

        given()
        .when()
            .get("/animals/" + id)
        .then()
            .statusCode(200)
            .body("createdBy", equalTo("admin"));
    }
```

`"admin"` is the principal name from the class-level `@TestSecurity` annotation, so this
asserts the whole path: token identity → resource → use case → database → response.

- [ ] **Step 2: Run the test to verify it fails**

```powershell
.\mvnw.cmd verify -Dit.test=AnimalResourceIT
```

Expected: FAIL — `JSON path createdBy doesn't match. Expected: admin, Actual: null` (the field is absent from the response payload).

- [ ] **Step 3: Add the fields to the response DTO**

`src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java`:

```java
public record AnimalResponse(
        UUID id,
        String name,
        String species,
        boolean dangerous,
        Habitat habitat,
        UUID enclosureId,
        LocalDate arrivalDate,
        AnimalStatus status,
        String createdBy,
        String updatedBy
) {}
```

No change to `AnimalDtoMapper`: MapStruct matches the new components by name against the
`Animal` getters added in Task 3.

- [ ] **Step 4: Run the test to verify it passes**

```powershell
.\mvnw.cmd verify -Dit.test=AnimalResourceIT
```

Expected: PASS, 10 tests.

- [ ] **Step 5: Run the whole suite**

```powershell
.\mvnw.cmd verify
```

Expected: PASS, including the 8 tests in `AnimalSecurityIT`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java
git commit -m "feat(animal-service): expose audit fields in animal responses"
```

---

## Task 6: Keycloak dev infrastructure, OIDC and CORS configuration, OpenAPI scheme

**Files:**
- Create: `zms-be/infrastructure/keycloak/realm-export.json`
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/OpenApiConfig.java`
- Modify: `zms-be/infrastructure/docker-compose.yml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`

**Interfaces:**
- Consumes: the role names from `ZooRoles` (Task 1) — the realm's role names must match them exactly.
- Produces: a `zoo` realm reachable at `http://localhost:8081/realms/zoo`; the `bearerAuth` OpenAPI security scheme.

This task changes only the `%dev` profile plus one annotation, so it cannot affect the test
suite. Nothing here is covered by automated tests; Task 7 verifies it by hand.

- [ ] **Step 1: Write the realm export**

`zms-be/infrastructure/keycloak/realm-export.json`:

```json
{
  "realm": "zoo",
  "enabled": true,
  "sslRequired": "none",
  "roles": {
    "realm": [
      { "name": "zoo-admin" },
      { "name": "zoo-vet" },
      { "name": "zoo-keeper" }
    ]
  },
  "clients": [
    {
      "clientId": "animal-service",
      "enabled": true,
      "publicClient": false,
      "bearerOnly": false,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "secret": "animal-service-dev-secret"
    },
    {
      "clientId": "zms-fe",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": true,
      "redirectUris": ["http://localhost:4200/*"],
      "webOrigins": ["http://localhost:4200"]
    }
  ],
  "users": [
    {
      "username": "admin",
      "enabled": true,
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "admin", "temporary": false }],
      "realmRoles": ["zoo-admin"]
    },
    {
      "username": "vet",
      "enabled": true,
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "vet", "temporary": false }],
      "realmRoles": ["zoo-vet"]
    },
    {
      "username": "keeper",
      "enabled": true,
      "emailVerified": true,
      "credentials": [{ "type": "password", "value": "keeper", "temporary": false }],
      "realmRoles": ["zoo-keeper"]
    }
  ]
}
```

This file holds development credentials in clear text. It is a local fixture and must never
be reused for a deployed environment — a deployed realm needs generated secrets and real
user provisioning.

- [ ] **Step 2: Add the Keycloak service to docker-compose**

In `zms-be/infrastructure/docker-compose.yml`, add under `services:` (sibling of
`postgres-animal`, above the `volumes:` block):

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    ports:
      - "8081:8080"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
```

Port 8081 keeps 8080 free for `quarkus:dev`.

- [ ] **Step 3: Start the stack and confirm the realm imported**

```powershell
cd ..\infrastructure
docker compose up -d
```

Then:

```powershell
curl http://localhost:8081/realms/zoo/.well-known/openid-configuration
```

Expected: a JSON document whose `issuer` is `http://localhost:8081/realms/zoo`.
If the realm is missing, inspect the import log with `docker compose logs keycloak`.
Return to the service directory afterwards: `cd ..\animal-service`.

- [ ] **Step 4: Configure OIDC for the dev profile**

In `src/main/resources/application.properties`, keep the existing first line
`quarkus.oidc.enabled=false` — it is the default that keeps the `%test` profile free of
OIDC — and add after it:

```properties
%dev.quarkus.oidc.enabled=true
%dev.quarkus.oidc.auth-server-url=http://localhost:8081/realms/zoo
%dev.quarkus.oidc.client-id=animal-service
%dev.quarkus.oidc.credentials.secret=animal-service-dev-secret
%dev.quarkus.oidc.application-type=service
%dev.quarkus.oidc.roles.role-claim-path=realm_access/roles
```

`role-claim-path` is not optional: Keycloak puts realm roles under `realm_access.roles`
while Quarkus OIDC reads the `groups` claim by default, so without it every authenticated
request is rejected with 403.

- [ ] **Step 5: Configure CORS for the dev profile**

Append to `src/main/resources/application.properties`:

```properties
%dev.quarkus.http.cors=true
%dev.quarkus.http.cors.origins=http://localhost:4200
%dev.quarkus.http.cors.methods=GET,POST,PUT,OPTIONS
%dev.quarkus.http.cors.headers=Content-Type,Authorization
```

The CORS property names changed across Quarkus 3.x (`quarkus.http.cors` vs
`quarkus.http.cors.enabled`). Confirm the spelling for 3.20.0 in the Quarkus HTTP
reference guide instead of trusting this snippet, then verify with the preflight check in
Step 8.

- [ ] **Step 6: Declare the OpenAPI security scheme**

`src/main/java/it/zoo/animal/infrastructure/rest/OpenApiConfig.java`:

```java
package it.zoo.animal.infrastructure.rest;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(title = "Animal Service API", version = "1.0.0")
)
@SecurityScheme(
        securitySchemeName = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig extends Application {
}
```

Then add to `AnimalResource`, at class level, next to `@Path`:

```java
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
```

```java
@ApplicationScoped
@Path("/animals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class AnimalResource {
```

`@SecurityRequirement` is documentation only and does not enforce anything — authorization
stays with `@RolesAllowed`.

Fallback: SmallRye picks up `@SecurityScheme` from the JAX-RS `Application` subclass. If
Step 8 shows no `bearerAuth` in `/q/openapi`, delete `OpenApiConfig.java` and configure the
scheme declaratively instead, keeping the `@SecurityRequirement` name in sync:

```properties
quarkus.smallrye-openapi.security-scheme=jwt
quarkus.smallrye-openapi.security-scheme-name=bearerAuth
```

- [ ] **Step 7: Run the whole suite**

```powershell
.\mvnw.cmd verify
```

Expected: PASS, unchanged. Only the `%dev` profile and OpenAPI metadata changed.

- [ ] **Step 8: Verify the OpenAPI document and the CORS preflight in dev**

Start the service (Keycloak and Postgres already up from Step 3):

```powershell
.\mvnw.cmd quarkus:dev
```

In a second shell:

```powershell
curl http://localhost:8080/q/openapi | Select-String bearerAuth
```

Expected: the security scheme appears in the document, and `/q/openapi` answers without a
token (it is not annotated, so it stays public).

```powershell
curl -i -X OPTIONS http://localhost:8080/animals -H "Origin: http://localhost:4200" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"
```

Expected: `access-control-allow-origin: http://localhost:4200` in the response headers. If
that header is absent, the CORS property names are wrong for this Quarkus version — fix
Step 5 before continuing. Stop dev mode with Ctrl+C afterwards.

- [ ] **Step 9: Commit**

```bash
git add ../infrastructure/keycloak/realm-export.json ../infrastructure/docker-compose.yml src/main/resources/application.properties src/main/java/it/zoo/animal/infrastructure/rest/OpenApiConfig.java src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java
git commit -m "feat(animal-service): add Keycloak dev realm, OIDC dev config and OpenAPI bearer scheme"
```

---

## Task 7: End-to-end verification against real tokens, then documentation

**Files:**
- Modify: `zms-be/CLAUDE.md` (status section only)

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: evidence that role-claim mapping works against Keycloak-issued JWTs — the one thing no automated test in this phase covers.

- [ ] **Step 1: Start the stack**

```powershell
cd ..\infrastructure
docker compose up -d
cd ..\animal-service
.\mvnw.cmd quarkus:dev
```

- [ ] **Step 2: Obtain a token for each demo user**

In a second shell:

```powershell
$tokenUrl = "http://localhost:8081/realms/zoo/protocol/openid-connect/token"

function Get-ZooToken($user) {
    $body = @{ client_id = "zms-fe"; username = $user; password = $user; grant_type = "password" }
    return (Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $body).access_token
}

$admin = Get-ZooToken "admin"
$vet = Get-ZooToken "vet"
$keeper = Get-ZooToken "keeper"
```

Each demo user's password equals their username, as set in the realm export.

If the token request fails with `invalid_client`, direct access grants are off for `zms-fe`
— re-check the realm export (Task 6 Step 1).

- [ ] **Step 3: Verify the role matrix against real tokens**

```powershell
# admin may register — expect 201
curl -i -X POST http://localhost:8080/animals -H "Authorization: Bearer $admin" -H "Content-Type: application/json" -d '{\"name\":\"Leo\",\"species\":\"Lion\",\"dangerous\":true,\"habitat\":\"TERRESTRIAL\",\"enclosureId\":\"550e8400-e29b-41d4-a716-446655440000\",\"arrivalDate\":\"2024-01-15\"}'

# no token — expect 401 {"message":"Authentication required"}
curl -i http://localhost:8080/animals

# vet may not register — expect 403 {"message":"Insufficient role"}
curl -i -X POST http://localhost:8080/animals -H "Authorization: Bearer $vet" -H "Content-Type: application/json" -d '{\"name\":\"Nemo\",\"species\":\"Fish\",\"dangerous\":false,\"habitat\":\"AQUATIC\",\"enclosureId\":\"550e8400-e29b-41d4-a716-446655440000\",\"arrivalDate\":\"2024-01-15\"}'

# keeper may read — expect 200
curl -i http://localhost:8080/animals -H "Authorization: Bearer $keeper"
```

Checklist, all four must hold:
- [ ] admin POST returns 201 and `createdBy` in the body is `admin` (proves the principal from a real JWT reaches the database, not just `@TestSecurity`)
- [ ] anonymous GET returns 401 with the generic message
- [ ] vet POST returns 403 with the generic message
- [ ] keeper GET returns 200

A 403 on **every** authenticated call means role-claim mapping failed: verify
`%dev.quarkus.oidc.roles.role-claim-path=realm_access/roles` and decode the token payload
to confirm the roles are where the config expects them.

- [ ] **Step 4: Stop the stack**

```powershell
# Ctrl+C in the quarkus:dev shell, then:
cd ..\infrastructure
docker compose down
cd ..\animal-service
```

- [ ] **Step 5: Update the project status documentation**

In `zms-be/CLAUDE.md`, under "Stato attuale e prossimi passi", replace the "Prossima fase"
and "Fasi successive" content so that security is recorded as done and Kafka is next:

```markdown
### Completato — Security (fase 6)

- `infrastructure/security/ZooRoles.java` — costanti dei 3 realm role
- `@RolesAllowed` per endpoint su `AnimalResource` (matrice: POST=admin, GET=tutti,
  status=vet+admin, transfer=keeper+admin)
- `SecurityExceptionMapper` — 401/403 con lo stesso body degli altri errori
- Audit dell'attore: `performedBy` nelle firme dei use case di scrittura,
  colonne `created_by`/`updated_by` (migration `V2`)
- OIDC attivo solo in `%dev` contro Keycloak (`zms-be/infrastructure/keycloak/realm-export.json`),
  `quarkus.oidc.enabled=false` resta il default per `%test`
- Test: `@TestSecurity` su `AnimalResourceIT`, matrice di autorizzazione in `AnimalSecurityIT`

### Prossima fase

- `infrastructure/event/` — Kafka producer per eventi animale
- Servizi restanti: `health-service`, `feeding-service`, `notification-service`
```

- [ ] **Step 6: Commit**

```bash
git add ../CLAUDE.md
git commit -m "docs: record phase 6 security completion in project rules"
```

---

## Done when

- `.\mvnw.cmd verify` passes: domain tests, 5 application test classes (3 with a new blank-actor test), `AnimalEntityMapperTest`, 10 tests in `AnimalResourceIT`, 8 tests in `AnimalSecurityIT`.
- Anonymous requests get 401, wrong-role requests 403, both with the project error body.
- `created_by` is populated from the token principal on every registered animal.
- The four checks in Task 7 Step 3 hold against real Keycloak tokens.
- `zms-be/CLAUDE.md` records phase 6 as complete.
