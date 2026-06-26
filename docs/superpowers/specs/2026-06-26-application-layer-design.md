# Application Layer Design — animal-service

**Date:** 2026-06-26
**Phase:** 4 of 5 (roadmap: `zms-be/docs/zoo-roadmap.html`)
**Scope:** Application layer use case implementations for `animal-service`

---

## Context

Domain layer (Phase 3) is complete:
- `Animal`, `Habitat`, `AnimalStatus` domain models
- 5 port/in interfaces: `RegisterAnimalUseCase`, `GetAnimalUseCase`, `ListAnimalsUseCase`, `UpdateAnimalStatusUseCase`, `TransferAnimalUseCase`
- `AnimalRepository` port/out interface
- `AnimalNotFoundException`, `RegisterAnimalCommand`

---

## Architecture

One `@ApplicationScoped` class per use case. Each class:
- Implements one port/in interface
- Depends only on `AnimalRepository` (port/out, never the JPA adapter)
- Constructor injection (CDI/Arc)
- `@Transactional` on write methods; read methods are transaction-free

```
it.zoo.animal/
├── domain/
│   ├── model/Animal.java               ← adds canTransitionTo(AnimalStatus)
│   └── exception/
│       ├── AnimalNotFoundException      ← existing
│       └── InvalidStatusTransitionException  ← NEW
└── application/
    ├── RegisterAnimalService.java
    ├── GetAnimalService.java
    ├── ListAnimalsService.java
    ├── UpdateAnimalStatusService.java
    └── TransferAnimalService.java
```

---

## Domain Model Changes

### `Animal.canTransitionTo(AnimalStatus target)`

Status transition logic lives in the domain model, not in services.

Allowed transitions:
- `HEALTHY` → `UNDER_OBSERVATION`, `IN_TREATMENT`, `DECEASED`
- `UNDER_OBSERVATION` → `HEALTHY`, `IN_TREATMENT`, `DECEASED`
- `IN_TREATMENT` → `HEALTHY`, `UNDER_OBSERVATION`, `DECEASED`
- `DECEASED` → nothing (terminal state)

Any other transition returns `false`. Services check this before updating.

### `InvalidStatusTransitionException`

```java
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(AnimalStatus from, AnimalStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
```

### `InvalidAnimalDataException`

```java
public class InvalidAnimalDataException extends RuntimeException {
    public InvalidAnimalDataException(String message) {
        super(message);
    }
}
```

Used by `RegisterAnimalService` and `TransferAnimalService` for input validation failures.

---

## Service Contracts

### `RegisterAnimalService`

Implements `RegisterAnimalUseCase`.

**Validation (explicit, in-service):**
- `name` not blank
- `species` not blank
- `habitat` not null
- `enclosureId` not null
- `arrivalDate` not null
- Throws `IllegalArgumentException` with descriptive message on failure

**Logic:**
- Assigns `UUID.randomUUID()` as id
- Sets initial status to `HEALTHY`
- Calls `repository.save(animal)`, returns saved `Animal`
- Annotated `@Transactional`

---

### `GetAnimalService`

Implements `GetAnimalUseCase`.

**Logic:**
- Calls `repository.findById(id)`
- Throws `AnimalNotFoundException` if `Optional.empty()`
- Returns `Animal`
- No `@Transactional`

---

### `ListAnimalsService`

Implements `ListAnimalsUseCase`.

**Logic:**
- Calls `repository.findAll()`
- Returns list (empty list is valid, no exception)
- No `@Transactional`

---

### `UpdateAnimalStatusService`

Implements `UpdateAnimalStatusUseCase`.

**Logic:**
1. Find animal by id → `AnimalNotFoundException` if missing
2. Call `animal.canTransitionTo(newStatus)` → `InvalidStatusTransitionException` if false
3. `animal.setStatus(newStatus)`
4. `repository.save(animal)`, return updated animal
- Annotated `@Transactional`

---

### `TransferAnimalService`

Implements `TransferAnimalUseCase`.

**Validation:**
- `targetEnclosureId` not null → `IllegalArgumentException`

**Logic:**
1. Find animal by id → `AnimalNotFoundException` if missing
2. `animal.setEnclosureId(targetEnclosureId)`
3. `repository.save(animal)`, return updated animal
- Annotated `@Transactional`

---

## Testing

Framework: JUnit 5 + Mockito. Zero Quarkus, zero DB.

```
test/java/it/zoo/animal/
├── application/
│   ├── RegisterAnimalServiceTest.java
│   ├── GetAnimalServiceTest.java
│   ├── ListAnimalsServiceTest.java
│   ├── UpdateAnimalStatusServiceTest.java
│   └── TransferAnimalServiceTest.java
└── domain/
    └── AnimalStatusTransitionTest.java
```

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class RegisterAnimalServiceTest {
    @Mock AnimalRepository repository;
    @InjectMocks RegisterAnimalService service;
}
```

**Test cases per service:**

| Service | Cases |
|---------|-------|
| Register | happy path, blank name, blank species, null habitat, null enclosureId, null arrivalDate |
| Get | happy path, not found |
| List | returns list, returns empty list |
| UpdateStatus | happy path, not found, invalid transition (DECEASED → HEALTHY) |
| Transfer | happy path, not found, null targetEnclosureId |
| AnimalStatusTransitionTest | all valid paths, all invalid paths, DECEASED terminal |

---

## What This Phase Does NOT Cover

- JPA entity, Panache repository adapter (Phase 5)
- REST resource endpoints (Phase 5)
- Flyway migration scripts (Phase 5)
- Integration tests with `@QuarkusTest` (Phase 5)
