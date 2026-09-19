---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# Testing Patterns

**Analysis Date:** 2026-09-19

**Scope:** `zms-be/` only. All tests live in `zms-be/animal-service/src/test/java/it/zoo/animal/` (9 classes, 53 test methods, 879 lines). Other services have no code or tests.

## Test Framework

**Runner:**

- JUnit 5 via `quarkus-junit5` (version from Quarkus BOM 3.20.0 in `zms-be/pom.xml`)
- Surefire / Failsafe 3.5.6 (`surefire-plugin.version` in `zms-be/pom.xml`)
- Config: `zms-be/animal-service/pom.xml` (surefire + failsafe with `integration-test`/`verify` goals), `src/main/resources/application.properties` (`%test.` profile)

**Assertion Library:**

- `org.junit.jupiter.api.Assertions` (unit tests)
- Hamcrest matchers via RestAssured (`equalTo`, `notNullValue`, `hasSize`) for HTTP tests

**Other:** `mockito-junit-jupiter`, `rest-assured`, `quarkus-test-security`.

**Run Commands (from `zms-be/animal-service/`):**

```bash
./mvnw test              # Unit tests (*Test classes) via Surefire
./mvnw verify            # Unit + *IT classes via Failsafe
./mvnw test -Dtest=TransferAnimalServiceTest   # Single class
```

Coverage: no tool configured.

`*IT` classes are `@QuarkusTest` and need PostgreSQL: the `%test` profile sets only `db-kind=postgresql` + Flyway migrate-at-start, relying on Quarkus Dev Services (Testcontainers/Docker). OIDC is off in tests (`quarkus.oidc.enabled=false`); auth is simulated with `@TestSecurity`.

## Test File Organization

**Location:** Separate tree `src/test/java`, mirroring the main package of the class under test.

**Naming:**

- Unit tests: `{ClassUnderTest}Test` (`TransferAnimalServiceTest`, `AnimalEntityMapperTest`) or `{Concept}Test` for domain rules (`AnimalStatusTransitionTest`)
- Quarkus/HTTP tests: `{Resource}IT` or `{Entity}{Concern}IT` (`AnimalResourceIT`, `AnimalSecurityIT`)
- Methods: `should{ExpectedBehaviour}[When{Condition}]` — `shouldThrowWhenAnimalNotFound`, `shouldReturn403WhenVetRegistersAnimal`, `shouldReturn422OnInvalidStatusTransition`
- Test classes and methods are package-private (no `public`)

**Structure:**

```
zms-be/animal-service/src/test/java/it/zoo/animal/
├── domain/AnimalStatusTransitionTest.java            # pure JUnit
├── application/{Verb}AnimalServiceTest.java (x5)     # JUnit + Mockito
└── infrastructure/
    ├── persistence/AnimalEntityMapperTest.java       # pure JUnit
    └── rest/AnimalResourceIT.java, AnimalSecurityIT.java  # @QuarkusTest + RestAssured
```

## Test Structure

**Layer rules (from `zms-be/CLAUDE.md`):**

- `domain`: JUnit only — no Quarkus, no Mockito
- `application`: JUnit + Mockito, never `@QuarkusTest` (CDI must not start)
- `infrastructure`: `@QuarkusTest` + RestAssured for REST; plain JUnit for static mappers

**Application service test (`application/TransferAnimalServiceTest.java`):**

```java
@ExtendWith(MockitoExtension.class)
class TransferAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    TransferAnimalService service;

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

**REST integration test (`infrastructure/rest/AnimalResourceIT.java`):**

```java
@QuarkusTest
@TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
class AnimalResourceIT {

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() ->
            em.createQuery("DELETE FROM AnimalEntity").executeUpdate()
        );
    }
```

Requests use the given/when/then layout with `.when()` and `.then()` outdented:

```java
given()
    .contentType(ContentType.JSON)
    .body("{\"status\": \"UNDER_OBSERVATION\"}")
.when()
    .put("/animals/" + id + "/status")
.then()
    .statusCode(200)
    .body("status", equalTo("UNDER_OBSERVATION"));
```

**Patterns:**

- Setup: Arrange / Act / Assert separated by blank lines; no `@BeforeEach` in Mockito tests
- Teardown: IT classes wipe the table in `@BeforeEach` inside `QuarkusTransaction.requiringNew()`
- Assertions: `assertEquals`/`assertTrue`/`assertFalse`/`assertThrows` for units; `.statusCode(...)` + `.body("field", matcher)` for HTTP; error bodies checked via `.body("message", ...)`
- Field injection (`@Inject`, `@Mock`) is allowed in tests only

## Mocking

**Framework:** Mockito (`MockitoExtension`, `@Mock`, `@InjectMocks`, `when`, `any`).

**Patterns:**

```java
when(repository.findById(animalId)).thenReturn(Optional.empty());
when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));
```

**What to Mock:** Only outbound ports (`domain/port/out/AnimalRepository`).

**What NOT to Mock:** Domain models (construct real `Animal`s), mappers, anything in `@QuarkusTest` (use the real DB via Dev Services). No `@InjectMock` / `QuarkusMock` in use.

## Fixtures and Factories

**Test Data:**

- Inline construction with the all-args constructor; canonical sample is "Leo" / "Lion" / `Habitat.TERRESTRIAL`
- Small private helpers per class:

```java
private Animal animalWithStatus(AnimalStatus status) {
    return new Animal(UUID.randomUUID(), "Leo", "Lion", true,
            Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), status);
}
```

- IT helpers: `postAnimal(name, species)` returning `ValidatableResponse` (`AnimalResourceIT`); JSON built via string concatenation / `private static final String ANIMAL_JSON`; fixed UUID constants (`SEEDED_ID`, `ENCLOSURE_ID`) and direct `em.persist(AnimalEntity)` seeding (`AnimalSecurityIT`)

**Location:** Inside each test class. No shared fixture/builder module.

## Coverage

**Requirements:** None enforced (no JaCoCo). Convention: every application service has a `*ServiceTest`; each domain rule has unit tests; each endpoint + role combination is covered by `AnimalSecurityIT`.

**View Coverage:** Not configured. Add `quarkus-jacoco` via parent BOM if needed.

## Test Types

**Unit Tests:** `domain/`, `application/`, `infrastructure/persistence/` — fast, no container.

**Integration Tests:** `*IT` `@QuarkusTest` classes — full HTTP stack, Flyway-migrated PostgreSQL, `@TestSecurity` for roles (class-level default, overridden per method, e.g. `@TestSecurity(user = "vet", roles = {ZooRoles.VET})`; anonymous = no annotation, expect 401).

**E2E Tests:** Not used.

## Common Patterns

**Async Testing:** Not applicable (no reactive/Kafka code yet).

**Error Testing:**

```java
assertThrows(AnimalNotFoundException.class,
        () -> service.transfer(animalId, newEnclosureId, "keeper"));
```

HTTP:

```java
.then()
    .statusCode(404)
    .body("message", notNullValue());
```

**Adding tests for a new use case:** create `application/{Verb}{Entity}ServiceTest` (Mockito, happy path + each `throw` branch), add domain rule tests under `domain/`, extend `{Entity}ResourceIT` for the endpoint and `{Entity}SecurityIT` for the role matrix.

---

*Testing analysis: 2026-09-19*
