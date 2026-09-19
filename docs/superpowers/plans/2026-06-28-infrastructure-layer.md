# Infrastructure Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the infrastructure adapter layer for `animal-service` — JPA persistence, Flyway migration, REST endpoints with DTOs and MapStruct mapper, and `@QuarkusTest` integration tests.

**Architecture:** Hexagonal — `AnimalPanacheRepository` implements `AnimalRepository` (port/out), bridging JPA to the domain. `AnimalResource` is the REST adapter (port/in), delegating to use-case services. Manual mapper for entity↔domain; MapStruct for domain→DTO.

**Tech Stack:** Java 21, Quarkus 3.20.0, Hibernate ORM Panache, Flyway, Quarkus REST (RESTEasy Reactive) + Jackson, Hibernate Validator, MapStruct 1.5.5.Final, RestAssured, JUnit 5, `@QuarkusTest`

## Global Constraints

- Package root: `it.zoo.animal`
- Persistence adapters: `it.zoo.animal.infrastructure.persistence`
- REST adapters: `it.zoo.animal.infrastructure.rest`
- REST DTOs: `it.zoo.animal.infrastructure.rest.dto`
- REST mapper: `it.zoo.animal.infrastructure.rest.mapper`
- Working directory for Maven commands: `zms-be/animal-service`
- Maven command: `.\mvnw.cmd` (Windows wrapper)
- No `@Entity`, `@Column`, `@Id` in `domain/` — infrastructure only
- No JAX-RS annotations in `application/`
- No business logic in `infrastructure/`
- `AnimalPanacheRepository` must implement `PanacheRepositoryBase<AnimalEntity, UUID>` (NOT `PanacheRepository<AnimalEntity>` — UUID ID requires the base variant)
- MapStruct `componentModel = "cdi"` — generated impl is `@ApplicationScoped` CDI bean
- Bean Validation (`@NotNull`, `@NotBlank`, `@Valid`) allowed ONLY on request DTOs and resource method parameters
- Constructor injection only — no `@Inject` on fields (except in `@QuarkusTest` which requires `@Inject`)
- No `@Transactional` on `AnimalResource` — application-layer services already handle it

---

## File Map

### Created
| File | Responsibility |
|------|----------------|
| `src/main/resources/db/migration/V1__create_animals_table.sql` | Flyway migration — `animals` table schema |
| `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java` | JPA `@Entity` — maps DB row to Java object |
| `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java` | Manual static mapper — `AnimalEntity ↔ Animal` |
| `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalPanacheRepository.java` | Implements `AnimalRepository` port/out via Panache |
| `src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java` | Response record — returned by all endpoints |
| `src/main/java/it/zoo/animal/infrastructure/rest/dto/RegisterAnimalRequest.java` | Request record — `POST /animals` |
| `src/main/java/it/zoo/animal/infrastructure/rest/dto/UpdateAnimalStatusRequest.java` | Request record — `PUT /animals/{id}/status` |
| `src/main/java/it/zoo/animal/infrastructure/rest/dto/TransferAnimalRequest.java` | Request record — `PUT /animals/{id}/transfer` |
| `src/main/java/it/zoo/animal/infrastructure/rest/mapper/AnimalDtoMapper.java` | MapStruct interface — `Animal → AnimalResponse` |
| `src/main/java/it/zoo/animal/infrastructure/rest/ZooExceptionMapper.java` | JAX-RS `@Provider` — domain exceptions → HTTP status codes |
| `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java` | JAX-RS resource — 5 endpoints |
| `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java` | Unit tests for `AnimalEntityMapper` (JUnit 5 only, no Quarkus) |
| `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java` | `@QuarkusTest` integration tests — full stack against real DB |

### Modified
| File | Change |
|------|--------|
| `pom.xml` | Add `quarkus-hibernate-validator`, `mapstruct`, `rest-assured`; add `mapstruct.version` property; configure annotation processor |
| `src/main/resources/application.properties` | Add test profile config; disable OIDC globally |

---

## Task 1: Dependencies + Configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

**Interfaces:**
- Produces: `@Valid` on resource parameters works; MapStruct processes `@Mapper`; `@QuarkusTest` can use RestAssured; OIDC does not block startup

- [ ] **Step 1: Add dependencies to pom.xml**

In `pom.xml`, add these three blocks inside `<dependencies>`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Add mapstruct.version property to pom.xml**

In `pom.xml`, add a `<properties>` section immediately after `<artifactId>animal-service</artifactId>`:

```xml
<properties>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>
```

- [ ] **Step 3: Configure maven-compiler-plugin for MapStruct annotation processor**

In `pom.xml`, replace the existing `maven-compiler-plugin` configuration:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
        <annotationProcessorPaths>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

- [ ] **Step 4: Update application.properties**

Replace the full content of `src/main/resources/application.properties`:

```properties
quarkus.oidc.enabled=false

%dev.quarkus.datasource.db-kind=postgresql
%dev.quarkus.datasource.username=zoo
%dev.quarkus.datasource.password=zoo_secret
%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/animal_db
%dev.quarkus.hibernate-orm.database.generation=none
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.devservices.enabled=false

%test.quarkus.datasource.db-kind=postgresql
%test.quarkus.hibernate-orm.database.generation=none
%test.quarkus.flyway.migrate-at-start=true
```

- [ ] **Step 5: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add zms-be/animal-service/pom.xml \
        zms-be/animal-service/src/main/resources/application.properties
git commit -m "feat(animal-service): add hibernate-validator, mapstruct, rest-assured dependencies"
```

---

## Task 2: Flyway Migration + AnimalEntity

**Files:**
- Create: `src/main/resources/db/migration/V1__create_animals_table.sql`
- Create: `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java`

**Interfaces:**
- Produces: `AnimalEntity(UUID id, String name, String species, boolean dangerous, Habitat habitat, UUID enclosureId, LocalDate arrivalDate, AnimalStatus status)` with full getters/setters; Flyway migration creates `animals` table

- [ ] **Step 1: Create Flyway migration**

Create `src/main/resources/db/migration/V1__create_animals_table.sql`:

```sql
CREATE TABLE animals (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    species      VARCHAR(100) NOT NULL,
    dangerous    BOOLEAN      NOT NULL DEFAULT FALSE,
    habitat      VARCHAR(20)  NOT NULL,
    enclosure_id UUID         NOT NULL,
    arrival_date DATE         NOT NULL,
    status       VARCHAR(20)  NOT NULL
);
```

- [ ] **Step 2: Create AnimalEntity**

Create `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java`:

```java
package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animals")
public class AnimalEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String species;

    @Column(nullable = false)
    private boolean dangerous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Habitat habitat;

    @Column(name = "enclosure_id", nullable = false)
    private UUID enclosureId;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnimalStatus status;

    public AnimalEntity() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public boolean isDangerous() { return dangerous; }
    public Habitat getHabitat() { return habitat; }
    public UUID getEnclosureId() { return enclosureId; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public AnimalStatus getStatus() { return status; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSpecies(String species) { this.species = species; }
    public void setDangerous(boolean dangerous) { this.dangerous = dangerous; }
    public void setHabitat(Habitat habitat) { this.habitat = habitat; }
    public void setEnclosureId(UUID enclosureId) { this.enclosureId = enclosureId; }
    public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
    public void setStatus(AnimalStatus status) { this.status = status; }
}
```

- [ ] **Step 3: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add "zms-be/animal-service/src/main/resources/db/migration/V1__create_animals_table.sql" \
        "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntity.java"
git commit -m "feat(animal-service): add Flyway migration and AnimalEntity JPA mapping"
```

---

## Task 3: AnimalEntityMapper (TDD)

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java`
- Test: `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java`

**Interfaces:**
- Produces: `AnimalEntityMapper.toDomain(AnimalEntity): Animal`, `AnimalEntityMapper.toEntity(Animal): AnimalEntity`, `AnimalEntityMapper.toDomainList(List<AnimalEntity>): List<Animal>` — all static, no CDI

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java`:

```java
package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.model.Animal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalEntityMapperTest {

    private final UUID id = UUID.randomUUID();
    private final UUID enclosureId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2024, 1, 15);

    @Test
    void shouldMapEntityToDomain() {
        AnimalEntity entity = buildEntity();

        Animal domain = AnimalEntityMapper.toDomain(entity);

        assertEquals(entity.getId(), domain.getId());
        assertEquals(entity.getName(), domain.getName());
        assertEquals(entity.getSpecies(), domain.getSpecies());
        assertEquals(entity.isDangerous(), domain.isDangerous());
        assertEquals(entity.getHabitat(), domain.getHabitat());
        assertEquals(entity.getEnclosureId(), domain.getEnclosureId());
        assertEquals(entity.getArrivalDate(), domain.getArrivalDate());
        assertEquals(entity.getStatus(), domain.getStatus());
    }

    @Test
    void shouldMapDomainToEntity() {
        Animal domain = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, enclosureId, date, AnimalStatus.HEALTHY);

        AnimalEntity entity = AnimalEntityMapper.toEntity(domain);

        assertEquals(domain.getId(), entity.getId());
        assertEquals(domain.getName(), entity.getName());
        assertEquals(domain.getSpecies(), entity.getSpecies());
        assertEquals(domain.isDangerous(), entity.isDangerous());
        assertEquals(domain.getHabitat(), entity.getHabitat());
        assertEquals(domain.getEnclosureId(), entity.getEnclosureId());
        assertEquals(domain.getArrivalDate(), entity.getArrivalDate());
        assertEquals(domain.getStatus(), entity.getStatus());
    }

    private AnimalEntity buildEntity() {
        AnimalEntity entity = new AnimalEntity();
        entity.setId(id);
        entity.setName("Leo");
        entity.setSpecies("Lion");
        entity.setDangerous(true);
        entity.setHabitat(Habitat.TERRESTRIAL);
        entity.setEnclosureId(enclosureId);
        entity.setArrivalDate(date);
        entity.setStatus(AnimalStatus.HEALTHY);
        return entity;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd test -Dtest=AnimalEntityMapperTest -q`
Expected: FAIL — `AnimalEntityMapper does not exist`

- [ ] **Step 3: Implement AnimalEntityMapper**

Create `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java`:

```java
package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.model.Animal;

import java.util.List;

public class AnimalEntityMapper {

    private AnimalEntityMapper() {}

    public static Animal toDomain(AnimalEntity entity) {
        return new Animal(
                entity.getId(),
                entity.getName(),
                entity.getSpecies(),
                entity.isDangerous(),
                entity.getHabitat(),
                entity.getEnclosureId(),
                entity.getArrivalDate(),
                entity.getStatus()
        );
    }

    public static AnimalEntity toEntity(Animal animal) {
        AnimalEntity entity = new AnimalEntity();
        entity.setId(animal.getId());
        entity.setName(animal.getName());
        entity.setSpecies(animal.getSpecies());
        entity.setDangerous(animal.isDangerous());
        entity.setHabitat(animal.getHabitat());
        entity.setEnclosureId(animal.getEnclosureId());
        entity.setArrivalDate(animal.getArrivalDate());
        entity.setStatus(animal.getStatus());
        return entity;
    }

    public static List<Animal> toDomainList(List<AnimalEntity> entities) {
        return entities.stream().map(AnimalEntityMapper::toDomain).toList();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test -Dtest=AnimalEntityMapperTest -q`
Expected: BUILD SUCCESS, 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapper.java" \
        "zms-be/animal-service/src/test/java/it/zoo/animal/infrastructure/persistence/AnimalEntityMapperTest.java"
git commit -m "feat(animal-service): implement AnimalEntityMapper with unit tests"
```

---

## Task 4: AnimalPanacheRepository

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalPanacheRepository.java`

**Interfaces:**
- Consumes: `AnimalEntityMapper.toEntity(Animal)`, `AnimalEntityMapper.toDomain(AnimalEntity)`, `AnimalEntityMapper.toDomainList(List<AnimalEntity>)`, `AnimalRepository` port/out interface
- Produces: `AnimalPanacheRepository` as `@ApplicationScoped` CDI bean implementing `AnimalRepository`

Note: `EntityManager.merge()` handles both INSERT (new entity) and UPDATE (existing entity) by ID — correct for a universal `save()` method that the application layer calls for both create and update operations.

- [ ] **Step 1: Implement AnimalPanacheRepository**

Create `src/main/java/it/zoo/animal/infrastructure/persistence/AnimalPanacheRepository.java`:

```java
package it.zoo.animal.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AnimalPanacheRepository implements PanacheRepositoryBase<AnimalEntity, UUID>, AnimalRepository {

    @Override
    public Animal save(Animal animal) {
        AnimalEntity entity = AnimalEntityMapper.toEntity(animal);
        entity = getEntityManager().merge(entity);
        return AnimalEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Animal> findById(UUID id) {
        return findByIdOptional(id).map(AnimalEntityMapper::toDomain);
    }

    @Override
    public List<Animal> findAll() {
        return AnimalEntityMapper.toDomainList(listAll());
    }

    @Override
    public boolean existsById(UUID id) {
        return findByIdOptional(id).isPresent();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/persistence/AnimalPanacheRepository.java"
git commit -m "feat(animal-service): implement AnimalPanacheRepository adapter"
```

---

## Task 5: AnimalResponse + MapStruct Mapper

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java`
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/mapper/AnimalDtoMapper.java`

**Interfaces:**
- Produces: `AnimalResponse` record; `AnimalDtoMapper.toResponse(Animal): AnimalResponse`; `AnimalDtoMapper.toResponseList(List<Animal>): List<AnimalResponse>` — injectable as CDI bean via `componentModel = "cdi"`

- [ ] **Step 1: Create AnimalResponse**

Create `src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java`:

```java
package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalResponse(
        UUID id,
        String name,
        String species,
        boolean dangerous,
        Habitat habitat,
        UUID enclosureId,
        LocalDate arrivalDate,
        AnimalStatus status
) {}
```

- [ ] **Step 2: Create AnimalDtoMapper**

Create `src/main/java/it/zoo/animal/infrastructure/rest/mapper/AnimalDtoMapper.java`:

```java
package it.zoo.animal.infrastructure.rest.mapper;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.infrastructure.rest.dto.AnimalResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface AnimalDtoMapper {
    AnimalResponse toResponse(Animal animal);
    List<AnimalResponse> toResponseList(List<Animal> animals);
}
```

- [ ] **Step 3: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS — MapStruct generates `AnimalDtoMapperImpl` during compilation

- [ ] **Step 4: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/dto/AnimalResponse.java" \
        "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/mapper/AnimalDtoMapper.java"
git commit -m "feat(animal-service): add AnimalResponse DTO and MapStruct mapper"
```

---

## Task 6: Request DTOs

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/dto/RegisterAnimalRequest.java`
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/dto/UpdateAnimalStatusRequest.java`
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/dto/TransferAnimalRequest.java`

**Interfaces:**
- Produces: Three request records with Bean Validation annotations; consumed by `AnimalResource` method parameters annotated `@Valid`

- [ ] **Step 1: Create RegisterAnimalRequest**

Create `src/main/java/it/zoo/animal/infrastructure/rest/dto/RegisterAnimalRequest.java`:

```java
package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.Habitat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterAnimalRequest(
        @NotBlank String name,
        @NotBlank String species,
        boolean dangerous,
        @NotNull Habitat habitat,
        @NotNull UUID enclosureId,
        @NotNull LocalDate arrivalDate
) {}
```

- [ ] **Step 2: Create UpdateAnimalStatusRequest**

Create `src/main/java/it/zoo/animal/infrastructure/rest/dto/UpdateAnimalStatusRequest.java`:

```java
package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.AnimalStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAnimalStatusRequest(
        @NotNull AnimalStatus status
) {}
```

- [ ] **Step 3: Create TransferAnimalRequest**

Create `src/main/java/it/zoo/animal/infrastructure/rest/dto/TransferAnimalRequest.java`:

```java
package it.zoo.animal.infrastructure.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferAnimalRequest(
        @NotNull UUID targetEnclosureId
) {}
```

- [ ] **Step 4: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/dto/RegisterAnimalRequest.java" \
        "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/dto/UpdateAnimalStatusRequest.java" \
        "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/dto/TransferAnimalRequest.java"
git commit -m "feat(animal-service): add REST request DTOs with Bean Validation"
```

---

## Task 7: ZooExceptionMapper

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/ZooExceptionMapper.java`

**Interfaces:**
- Consumes: `AnimalNotFoundException` → 404, `InvalidAnimalDataException` → 400, `InvalidStatusTransitionException` → 422
- Produces: JAX-RS `@Provider` registered globally; `ErrorResponse(String message)` as JSON body on all error responses

- [ ] **Step 1: Create ZooExceptionMapper**

Create `src/main/java/it/zoo/animal/infrastructure/rest/ZooExceptionMapper.java`:

```java
package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ZooExceptionMapper implements ExceptionMapper<RuntimeException> {

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof AnimalNotFoundException) {
            return errorResponse(404, exception.getMessage());
        }
        if (exception instanceof InvalidAnimalDataException) {
            return errorResponse(400, exception.getMessage());
        }
        if (exception instanceof InvalidStatusTransitionException) {
            return errorResponse(422, exception.getMessage());
        }
        return errorResponse(500, "Internal server error");
    }

    private Response errorResponse(int status, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    public record ErrorResponse(String message) {}
}
```

- [ ] **Step 2: Verify compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/ZooExceptionMapper.java"
git commit -m "feat(animal-service): implement ZooExceptionMapper for domain exceptions"
```

---

## Task 8: AnimalResource

**Files:**
- Create: `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`

**Interfaces:**
- Consumes: `RegisterAnimalUseCase.register(RegisterAnimalCommand): Animal`, `GetAnimalUseCase.getById(UUID): Animal`, `ListAnimalsUseCase.listAll(): List<Animal>`, `UpdateAnimalStatusUseCase.updateStatus(UUID, AnimalStatus): Animal`, `TransferAnimalUseCase.transfer(UUID, UUID): Animal`, `AnimalDtoMapper.toResponse(Animal): AnimalResponse`, `AnimalDtoMapper.toResponseList(List<Animal>): List<AnimalResponse>`
- Produces: `POST /animals` → 201, `GET /animals` → 200, `GET /animals/{id}` → 200, `PUT /animals/{id}/status` → 200, `PUT /animals/{id}/transfer` → 200

- [ ] **Step 1: Implement AnimalResource**

Create `src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java`:

```java
package it.zoo.animal.infrastructure.rest;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.*;
import it.zoo.animal.infrastructure.rest.dto.AnimalResponse;
import it.zoo.animal.infrastructure.rest.dto.RegisterAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.TransferAnimalRequest;
import it.zoo.animal.infrastructure.rest.dto.UpdateAnimalStatusRequest;
import it.zoo.animal.infrastructure.rest.mapper.AnimalDtoMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/animals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnimalResource {

    private final RegisterAnimalUseCase registerAnimal;
    private final GetAnimalUseCase getAnimal;
    private final ListAnimalsUseCase listAnimals;
    private final UpdateAnimalStatusUseCase updateAnimalStatus;
    private final TransferAnimalUseCase transferAnimal;
    private final AnimalDtoMapper mapper;

    public AnimalResource(RegisterAnimalUseCase registerAnimal,
                          GetAnimalUseCase getAnimal,
                          ListAnimalsUseCase listAnimals,
                          UpdateAnimalStatusUseCase updateAnimalStatus,
                          TransferAnimalUseCase transferAnimal,
                          AnimalDtoMapper mapper) {
        this.registerAnimal = registerAnimal;
        this.getAnimal = getAnimal;
        this.listAnimals = listAnimals;
        this.updateAnimalStatus = updateAnimalStatus;
        this.transferAnimal = transferAnimal;
        this.mapper = mapper;
    }

    @POST
    public Response register(@Valid RegisterAnimalRequest request) {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                request.name(), request.species(), request.dangerous(),
                request.habitat(), request.enclosureId(), request.arrivalDate()
        );
        Animal animal = registerAnimal.register(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(animal))
                .build();
    }

    @GET
    public List<AnimalResponse> listAll() {
        return mapper.toResponseList(listAnimals.listAll());
    }

    @GET
    @Path("/{id}")
    public AnimalResponse getById(@PathParam("id") UUID id) {
        return mapper.toResponse(getAnimal.getById(id));
    }

    @PUT
    @Path("/{id}/status")
    public AnimalResponse updateStatus(@PathParam("id") UUID id,
                                       @Valid UpdateAnimalStatusRequest request) {
        return mapper.toResponse(updateAnimalStatus.updateStatus(id, request.status()));
    }

    @PUT
    @Path("/{id}/transfer")
    public AnimalResponse transfer(@PathParam("id") UUID id,
                                   @Valid TransferAnimalRequest request) {
        return mapper.toResponse(transferAnimal.transfer(id, request.targetEnclosureId()));
    }
}
```

- [ ] **Step 2: Verify full compile**

Run: `.\mvnw.cmd compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run existing unit tests to verify nothing broke**

Run: `.\mvnw.cmd test -q -Dexcludes="**/*IT.java"`
Expected: BUILD SUCCESS — all previous unit tests pass

- [ ] **Step 4: Commit**

```bash
git add "zms-be/animal-service/src/main/java/it/zoo/animal/infrastructure/rest/AnimalResource.java"
git commit -m "feat(animal-service): implement AnimalResource with 5 REST endpoints"
```

---

## Task 9: Integration Tests

**Files:**
- Create: `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`

**Interfaces:**
- Consumes: All 5 endpoints; Quarkus Dev Services auto-starts PostgreSQL container; Flyway runs migration at startup

**Prerequisite:** Docker must be running. Quarkus Dev Services creates and destroys a PostgreSQL container automatically for `@QuarkusTest`.

- [ ] **Step 1: Create AnimalResourceIT**

Create `src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`:

```java
package it.zoo.animal.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import it.zoo.animal.infrastructure.persistence.AnimalPanacheRepository;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AnimalResourceIT {

    @Inject
    AnimalPanacheRepository repository;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> repository.deleteAll());
    }

    @Test
    void shouldRegisterAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "name": "Leo",
                  "species": "Lion",
                  "dangerous": true,
                  "habitat": "TERRESTRIAL",
                  "enclosureId": "550e8400-e29b-41d4-a716-446655440000",
                  "arrivalDate": "2024-01-15"
                }
                """)
        .when()
            .post("/animals")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Leo"))
            .body("species", equalTo("Lion"))
            .body("dangerous", equalTo(true))
            .body("status", equalTo("HEALTHY"));
    }

    @Test
    void shouldListAllAnimals() {
        postAnimal("Leo", "Lion");
        postAnimal("Nemo", "Fish");

        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2));
    }

    @Test
    void shouldGetAnimalById() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
        .when()
            .get("/animals/" + id)
        .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("name", equalTo("Leo"));
    }

    @Test
    void shouldReturn404WhenAnimalNotFound() {
        given()
        .when()
            .get("/animals/00000000-0000-0000-0000-000000000000")
        .then()
            .statusCode(404)
            .body("message", notNullValue());
    }

    @Test
    void shouldUpdateAnimalStatus() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("""{"status": "UNDER_OBSERVATION"}""")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("UNDER_OBSERVATION"));
    }

    @Test
    void shouldReturn422OnInvalidStatusTransition() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("""{"status": "DECEASED"}""")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("""{"status": "HEALTHY"}""")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(422)
            .body("message", notNullValue());
    }

    @Test
    void shouldTransferAnimal() {
        String id = postAnimal("Leo", "Lion").extract().path("id");
        String newEnclosureId = "660e8400-e29b-41d4-a716-446655440001";

        given()
            .contentType(ContentType.JSON)
            .body("""{"targetEnclosureId": "%s"}""".formatted(newEnclosureId))
        .when()
            .put("/animals/" + id + "/transfer")
        .then()
            .statusCode(200)
            .body("enclosureId", equalTo(newEnclosureId));
    }

    @Test
    void shouldReturn400OnBlankName() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "name": "",
                  "species": "Lion",
                  "dangerous": false,
                  "habitat": "TERRESTRIAL",
                  "enclosureId": "550e8400-e29b-41d4-a716-446655440000",
                  "arrivalDate": "2024-01-15"
                }
                """)
        .when()
            .post("/animals")
        .then()
            .statusCode(400);
    }

    private ValidatableResponse postAnimal(String name, String species) {
        return given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "name": "%s",
                  "species": "%s",
                  "dangerous": false,
                  "habitat": "TERRESTRIAL",
                  "enclosureId": "550e8400-e29b-41d4-a716-446655440000",
                  "arrivalDate": "2024-01-15"
                }
                """.formatted(name, species))
        .when()
            .post("/animals")
        .then()
            .statusCode(201);
    }
}
```

- [ ] **Step 2: Run all tests (unit + integration)**

Ensure Docker is running first.

Run: `.\mvnw.cmd test -q`
Expected: BUILD SUCCESS — all unit tests + 8 integration tests pass. Quarkus logs will show PostgreSQL container startup and Flyway migration.

- [ ] **Step 3: Commit**

```bash
git add "zms-be/animal-service/src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java"
git commit -m "test(animal-service): add AnimalResourceIT integration tests"
```
