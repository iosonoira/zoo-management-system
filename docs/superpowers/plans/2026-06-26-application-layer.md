# Application Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the application layer use case services for `animal-service`, plus the domain model changes they require.

**Architecture:** One `@ApplicationScoped` class per use case, each injecting `AnimalRepository` (port/out) via constructor. `@Transactional` lives only on write methods in the service layer. Domain model gains `canTransitionTo(AnimalStatus)` with DECEASED as terminal state.

**Tech Stack:** Java 21, Quarkus 3.20.0, CDI/Arc, Jakarta Transactions, JUnit 5, Mockito

## Global Constraints

- Package root: `it.zoo.animal`
- All services in package `it.zoo.animal.application`
- All exceptions in package `it.zoo.animal.domain.exception`
- Domain model (`it.zoo.animal.domain.model`) must have zero framework annotations
- Constructor injection only — no field injection
- `@Transactional` annotation: `jakarta.transaction.Transactional`
- `@ApplicationScoped` annotation: `jakarta.enterprise.context.ApplicationScoped`
- Working directory for Maven commands: `zms-be/animal-service`
- Maven command: `.\mvnw.cmd` (Windows wrapper)

---

## File Map

### Created
| File | Responsibility |
|------|----------------|
| `src/main/java/it/zoo/animal/domain/exception/InvalidStatusTransitionException.java` | Domain exception for illegal status transitions |
| `src/main/java/it/zoo/animal/domain/exception/InvalidAnimalDataException.java` | Domain exception for invalid input data |
| `src/main/java/it/zoo/animal/application/RegisterAnimalService.java` | Implements `RegisterAnimalUseCase` |
| `src/main/java/it/zoo/animal/application/GetAnimalService.java` | Implements `GetAnimalUseCase` |
| `src/main/java/it/zoo/animal/application/ListAnimalsService.java` | Implements `ListAnimalsUseCase` |
| `src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java` | Implements `UpdateAnimalStatusUseCase` |
| `src/main/java/it/zoo/animal/application/TransferAnimalService.java` | Implements `TransferAnimalUseCase` |
| `src/test/java/it/zoo/animal/domain/AnimalStatusTransitionTest.java` | Unit tests for `canTransitionTo` — no mocks |
| `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java` | Unit tests for RegisterAnimalService |
| `src/test/java/it/zoo/animal/application/GetAnimalServiceTest.java` | Unit tests for GetAnimalService |
| `src/test/java/it/zoo/animal/application/ListAnimalsServiceTest.java` | Unit tests for ListAnimalsService |
| `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java` | Unit tests for UpdateAnimalStatusService |
| `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java` | Unit tests for TransferAnimalService |

### Modified
| File | Change |
|------|--------|
| `src/main/java/it/zoo/animal/domain/model/Animal.java` | Add `canTransitionTo(AnimalStatus)` method |
| `pom.xml` | Add `mockito-junit-jupiter` test dependency |

---

## Task 1: Setup — Mockito dependency + domain exceptions

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/it/zoo/animal/domain/exception/InvalidStatusTransitionException.java`
- Create: `src/main/java/it/zoo/animal/domain/exception/InvalidAnimalDataException.java`

**Interfaces:**
- Produces: `InvalidStatusTransitionException(AnimalStatus from, AnimalStatus to)`, `InvalidAnimalDataException(String message)` — used by Tasks 3–7

- [ ] **Step 1: Add Mockito to pom.xml**

In `pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create InvalidStatusTransitionException**

```java
package it.zoo.animal.domain.exception;

import it.zoo.animal.domain.model.AnimalStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(AnimalStatus from, AnimalStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
```

- [ ] **Step 3: Create InvalidAnimalDataException**

```java
package it.zoo.animal.domain.exception;

public class InvalidAnimalDataException extends RuntimeException {
    public InvalidAnimalDataException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS, no output

- [ ] **Step 5: Commit**

```bash
git add zms-be/animal-service/pom.xml \
        "zms-be/animal-service/src/main/java/it/zoo/animal/domain/exception/InvalidStatusTransitionException.java" \
        "zms-be/animal-service/src/main/java/it/zoo/animal/domain/exception/InvalidAnimalDataException.java"
git commit -m "feat(animal-service): add domain exceptions and Mockito test dependency"
```

---

## Task 2: Animal.canTransitionTo + domain tests

**Files:**
- Modify: `src/main/java/it/zoo/animal/domain/model/Animal.java`
- Test: `src/test/java/it/zoo/animal/domain/AnimalStatusTransitionTest.java`

**Interfaces:**
- Consumes: `AnimalStatus` (existing), `Animal` constructor `(UUID, String, String, boolean, Habitat, UUID, LocalDate, AnimalStatus)`
- Produces: `animal.canTransitionTo(AnimalStatus target): boolean` — used by `UpdateAnimalStatusService` in Task 6

- [ ] **Step 1: Write the failing test**

Create `src/test/java/it/zoo/animal/domain/AnimalStatusTransitionTest.java`:

```java
package it.zoo.animal.domain;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalStatusTransitionTest {

    private Animal animalWithStatus(AnimalStatus status) {
        return new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), status);
    }

    @Test
    void healthyCanTransitionToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void healthyCanTransitionToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void healthyCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void underObservationCanTransitionToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void underObservationCanTransitionToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void underObservationCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void inTreatmentCanTransitionToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void inTreatmentCanTransitionToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void inTreatmentCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void deceasedIsTerminal() {
        Animal deceased = animalWithStatus(AnimalStatus.DECEASED);
        assertFalse(deceased.canTransitionTo(AnimalStatus.HEALTHY));
        assertFalse(deceased.canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
        assertFalse(deceased.canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void cannotTransitionToSameStatus() {
        assertFalse(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.HEALTHY));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -Dtest=AnimalStatusTransitionTest -q`
Expected: FAIL — `cannot find symbol: method canTransitionTo`

- [ ] **Step 3: Add canTransitionTo to Animal**

In `src/main/java/it/zoo/animal/domain/model/Animal.java`, add this method at the end of the class (before the closing `}`):

```java
public boolean canTransitionTo(AnimalStatus target) {
    if (this.status == AnimalStatus.DECEASED) {
        return false;
    }
    return this.status != target;
}
```

Also add import at the top of the file if not already present — `AnimalStatus` is in the same package so no import needed.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=AnimalStatusTransitionTest -q`
Expected: BUILD SUCCESS, 11 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/domain/model/Animal.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/domain/AnimalStatusTransitionTest.java"
git commit -m "feat(animal-service): add canTransitionTo with DECEASED terminal state"
```

---

## Task 3: RegisterAnimalService

**Files:**
- Create: `src/main/java/it/zoo/animal/application/RegisterAnimalService.java`
- Test: `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java`

**Interfaces:**
- Consumes: `RegisterAnimalUseCase.register(RegisterAnimalCommand): Animal`, `AnimalRepository.save(Animal): Animal`, `InvalidAnimalDataException(String)`
- Produces: `RegisterAnimalService` as CDI bean implementing `RegisterAnimalUseCase`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    RegisterAnimalService service;

    private final UUID enclosureId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @Test
    void shouldRegisterAnimalWithHealthyStatus() {
        Animal saved = new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, enclosureId, today, AnimalStatus.HEALTHY);
        when(repository.save(any(Animal.class))).thenReturn(saved);

        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        Animal result = service.register(cmd);

        assertEquals(AnimalStatus.HEALTHY, result.getStatus());
        assertEquals("Leo", result.getName());
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                null, "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenSpeciesIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenHabitatIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, null, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenEnclosureIdIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, null, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenArrivalDateIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, null);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=RegisterAnimalServiceTest -q`
Expected: FAIL — `package it.zoo.animal.application does not exist`

- [ ] **Step 3: Implement RegisterAnimalService**

Create `src/main/java/it/zoo/animal/application/RegisterAnimalService.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.in.RegisterAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class RegisterAnimalService implements RegisterAnimalUseCase {

    private final AnimalRepository repository;

    public RegisterAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal register(RegisterAnimalCommand cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new InvalidAnimalDataException("Animal name must not be blank");
        }
        if (cmd.species() == null || cmd.species().isBlank()) {
            throw new InvalidAnimalDataException("Animal species must not be blank");
        }
        if (cmd.habitat() == null) {
            throw new InvalidAnimalDataException("Animal habitat must not be null");
        }
        if (cmd.enclosureId() == null) {
            throw new InvalidAnimalDataException("Enclosure ID must not be null");
        }
        if (cmd.arrivalDate() == null) {
            throw new InvalidAnimalDataException("Arrival date must not be null");
        }

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
        return repository.save(animal);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=RegisterAnimalServiceTest -q`
Expected: BUILD SUCCESS, 7 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/application/RegisterAnimalService.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/application/RegisterAnimalServiceTest.java"
git commit -m "feat(animal-service): implement RegisterAnimalService with input validation"
```

---

## Task 4: GetAnimalService

**Files:**
- Create: `src/main/java/it/zoo/animal/application/GetAnimalService.java`
- Test: `src/test/java/it/zoo/animal/application/GetAnimalServiceTest.java`

**Interfaces:**
- Consumes: `GetAnimalUseCase.getById(UUID): Animal`, `AnimalRepository.findById(UUID): Optional<Animal>`, `AnimalNotFoundException(UUID)`
- Produces: `GetAnimalService` as CDI bean implementing `GetAnimalUseCase`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/application/GetAnimalServiceTest.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    GetAnimalService service;

    @Test
    void shouldReturnAnimalWhenFound() {
        UUID id = UUID.randomUUID();
        Animal animal = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(id)).thenReturn(Optional.of(animal));

        Animal result = service.getById(id);

        assertEquals(id, result.getId());
        assertEquals("Leo", result.getName());
    }

    @Test
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> service.getById(id));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=GetAnimalServiceTest -q`
Expected: FAIL — `GetAnimalService does not exist`

- [ ] **Step 3: Implement GetAnimalService**

Create `src/main/java/it/zoo/animal/application/GetAnimalService.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.GetAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class GetAnimalService implements GetAnimalUseCase {

    private final AnimalRepository repository;

    public GetAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    public Animal getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=GetAnimalServiceTest -q`
Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/application/GetAnimalService.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/application/GetAnimalServiceTest.java"
git commit -m "feat(animal-service): implement GetAnimalService"
```

---

## Task 5: ListAnimalsService

**Files:**
- Create: `src/main/java/it/zoo/animal/application/ListAnimalsService.java`
- Test: `src/test/java/it/zoo/animal/application/ListAnimalsServiceTest.java`

**Interfaces:**
- Consumes: `ListAnimalsUseCase.listAll(): List<Animal>`, `AnimalRepository.findAll(): List<Animal>`
- Produces: `ListAnimalsService` as CDI bean implementing `ListAnimalsUseCase`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/application/ListAnimalsServiceTest.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAnimalsServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    ListAnimalsService service;

    @Test
    void shouldReturnAllAnimals() {
        Animal a1 = new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        Animal a2 = new Animal(UUID.randomUUID(), "Nemo", "Fish", false,
                Habitat.AQUATIC, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findAll()).thenReturn(List.of(a1, a2));

        List<Animal> result = service.listAll();

        assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoAnimals() {
        when(repository.findAll()).thenReturn(List.of());

        List<Animal> result = service.listAll();

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=ListAnimalsServiceTest -q`
Expected: FAIL — `ListAnimalsService does not exist`

- [ ] **Step 3: Implement ListAnimalsService**

Create `src/main/java/it/zoo/animal/application/ListAnimalsService.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.ListAnimalsUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ListAnimalsService implements ListAnimalsUseCase {

    private final AnimalRepository repository;

    public ListAnimalsService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Animal> listAll() {
        return repository.findAll();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=ListAnimalsServiceTest -q`
Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/application/ListAnimalsService.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/application/ListAnimalsServiceTest.java"
git commit -m "feat(animal-service): implement ListAnimalsService"
```

---

## Task 6: UpdateAnimalStatusService

**Files:**
- Create: `src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java`
- Test: `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java`

**Interfaces:**
- Consumes: `UpdateAnimalStatusUseCase.updateStatus(UUID, AnimalStatus): Animal`, `Animal.canTransitionTo(AnimalStatus): boolean`, `InvalidStatusTransitionException(AnimalStatus, AnimalStatus)`, `AnimalNotFoundException(UUID)`
- Produces: `UpdateAnimalStatusService` as CDI bean implementing `UpdateAnimalStatusUseCase`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAnimalStatusServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    UpdateAnimalStatusService service;

    private Animal healthyAnimal(UUID id) {
        return new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
    }

    @Test
    void shouldUpdateStatus() {
        UUID id = UUID.randomUUID();
        Animal animal = healthyAnimal(id);
        Animal updated = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, animal.getEnclosureId(), LocalDate.now(), AnimalStatus.UNDER_OBSERVATION);
        when(repository.findById(id)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenReturn(updated);

        Animal result = service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION);

        assertEquals(AnimalStatus.UNDER_OBSERVATION, result.getStatus());
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class,
                () -> service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        UUID id = UUID.randomUUID();
        Animal deceased = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.DECEASED);
        when(repository.findById(id)).thenReturn(Optional.of(deceased));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateStatus(id, AnimalStatus.HEALTHY));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=UpdateAnimalStatusServiceTest -q`
Expected: FAIL — `UpdateAnimalStatusService does not exist`

- [ ] **Step 3: Implement UpdateAnimalStatusService**

Create `src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.port.in.UpdateAnimalStatusUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class UpdateAnimalStatusService implements UpdateAnimalStatusUseCase {

    private final AnimalRepository repository;

    public UpdateAnimalStatusService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal updateStatus(UUID id, AnimalStatus newStatus) {
        Animal animal = repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));

        if (!animal.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(animal.getStatus(), newStatus);
        }

        animal.setStatus(newStatus);
        return repository.save(animal);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=UpdateAnimalStatusServiceTest -q`
Expected: BUILD SUCCESS, 3 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/application/UpdateAnimalStatusService.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/application/UpdateAnimalStatusServiceTest.java"
git commit -m "feat(animal-service): implement UpdateAnimalStatusService with transition guard"
```

---

## Task 7: TransferAnimalService

**Files:**
- Create: `src/main/java/it/zoo/animal/application/TransferAnimalService.java`
- Test: `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java`

**Interfaces:**
- Consumes: `TransferAnimalUseCase.transfer(UUID, UUID): Animal`, `AnimalRepository.findById(UUID): Optional<Animal>`, `AnimalRepository.save(Animal): Animal`, `AnimalNotFoundException(UUID)`, `InvalidAnimalDataException(String)`
- Produces: `TransferAnimalService` as CDI bean implementing `TransferAnimalUseCase`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        Animal transferred = new Animal(animalId, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, newEnclosureId, LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(animalId)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenReturn(transferred);

        Animal result = service.transfer(animalId, newEnclosureId);

        assertEquals(newEnclosureId, result.getEnclosureId());
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        when(repository.findById(animalId)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class,
                () -> service.transfer(animalId, newEnclosureId));
    }

    @Test
    void shouldThrowWhenTargetEnclosureIdIsNull() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=TransferAnimalServiceTest -q`
Expected: FAIL — `TransferAnimalService does not exist`

- [ ] **Step 3: Implement TransferAnimalService**

Create `src/main/java/it/zoo/animal/application/TransferAnimalService.java`:

```java
package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.TransferAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class TransferAnimalService implements TransferAnimalUseCase {

    private final AnimalRepository repository;

    public TransferAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal transfer(UUID animalId, UUID targetEnclosureId) {
        if (targetEnclosureId == null) {
            throw new InvalidAnimalDataException("Target enclosure ID must not be null");
        }

        Animal animal = repository.findById(animalId)
                .orElseThrow(() -> new AnimalNotFoundException(animalId));

        animal.setEnclosureId(targetEnclosureId);
        return repository.save(animal);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=TransferAnimalServiceTest -q`
Expected: BUILD SUCCESS, 3 tests passed

- [ ] **Step 5: Run all tests together**

Run: `.\mvnw.cmd test -q`
Expected: BUILD SUCCESS, 28 tests passed (11 + 7 + 2 + 2 + 3 + 3)

- [ ] **Step 6: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/application/TransferAnimalService.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/application/TransferAnimalServiceTest.java"
git commit -m "feat(animal-service): implement TransferAnimalService"
```
