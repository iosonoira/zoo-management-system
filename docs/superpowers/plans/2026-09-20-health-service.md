# health-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `zms-be/health-service`, a second Quarkus microservice holding veterinary medical records and treatments, with its own database and no runtime dependency on `animal-service`.

**Architecture:** Hexagonal (Ports & Adapters), identical in shape to `animal-service`: a framework-free `domain/`, one `@ApplicationScoped` service per use case in `application/`, and JPA/JAX-RS adapters in `infrastructure/`. The animal is referenced only as an opaque `UUID` that this service never dereferences, so the two services start, test and fail independently.

**Tech Stack:** Java 21, Quarkus 3.20.0, Hibernate ORM with Panache, PostgreSQL 16, Flyway, MapStruct 1.5.5.Final, Keycloak OIDC, JUnit 5, Mockito, RestAssured, Testcontainers 1.21.4.

**Spec:** `docs/superpowers/specs/2026-09-20-health-service-design.md`

## Global Constraints

- Dependency rule, never violated: `infrastructure → application → domain`.
- `domain/` carries **zero** framework annotations and imports only `java.*` and its own packages.
- One use case = one `{Verb}{Entity}UseCase` interface in `domain/port/in/` + one `{Verb}{Entity}Service` in `application/`.
- Constructor injection only. Never `@Inject` on a field.
- `@Transactional` on the write method only, never on the class.
- Validation in application services is explicit `if` + `throw`. Bean Validation annotations are allowed **only** on request DTOs in `infrastructure/rest/dto/`.
- Panache **Repository** pattern (`implements` the port, injected `EntityManager`), never Active Record.
- Test naming: `should{Behaviour}[When{Condition}]`.
- Domain tests: JUnit 5 only, no Quarkus, no Mockito. Application tests: JUnit 5 + Mockito, no `@QuarkusTest`. Infrastructure tests: `@QuarkusTest`.
- Module POM declares **no** versions except `mapstruct.version`; the parent BOM governs.
- Base package: `it.zoo.health`. Group `it.zoo`, parent `zoo-management-system:1.0.0-SNAPSHOT`.
- Dates are `java.time.LocalDate`.
- Build command, run from `zms-be/health-service`: `./mvnw -B --no-transfer-progress verify`.
- Commits: conventional-commit prefixes (`feat`, `test`, `build`, `chore`, `ci`, `docs`), scope `health-service` where it clarifies. **No `Co-Authored-By` trailer.**

---

## File Structure

| File | Responsibility |
|---|---|
| `zms-be/pom.xml` | MODIFIED — adds `<module>health-service</module>` |
| `zms-be/health-service/pom.xml` | Module build, mirrors `animal-service` |
| `domain/model/MedicalRecord.java` | Clinical visit POJO; no behaviour beyond accessors |
| `domain/model/Treatment.java` | Therapy POJO; owns `canTransitionTo` |
| `domain/model/MedicalRecordPage.java` | Paged list result |
| `domain/model/MedicalRecordDetail.java` | A record plus its treatments |
| `domain/enums/TreatmentStatus.java` | Four-state lifecycle |
| `domain/exception/*.java` | Six unchecked domain exceptions |
| `domain/port/in/*.java` | Five use case interfaces + two Command records |
| `domain/port/out/MedicalRecordRepository.java` | Record persistence contract |
| `domain/port/out/TreatmentRepository.java` | Treatment persistence contract |
| `application/*Service.java` | Five use case implementations |
| `infrastructure/persistence/*Entity.java` | JPA entities on `medical_records` / `treatments` |
| `infrastructure/persistence/*EntityMapper.java` | Static domain↔entity mapping |
| `infrastructure/persistence/*PanacheRepository.java` | Port implementations over `EntityManager` |
| `infrastructure/rest/MedicalRecordResource.java` | `/medical-records` endpoints |
| `infrastructure/rest/TreatmentResource.java` | `/treatments/{id}/status` |
| `infrastructure/rest/*ExceptionMapper.java` | One `@Provider` per exception type |
| `infrastructure/rest/dto/*.java` | Request/response records, Bean Validation |
| `infrastructure/rest/mapper/HealthDtoMapper.java` | MapStruct domain→response |
| `infrastructure/security/ZooRoles.java` | Role constants, copied not shared |
| `resources/db/migration/V1__*.sql` | Both tables, audit and version included |
| `resources/application.properties` | Profiles, datasource, OIDC, CORS |
| `zms-be/infrastructure/docker-compose.yml` | MODIFIED — `postgres-health` |
| `zms-be/infrastructure/keycloak/realm-export.json` | MODIFIED — `health-service` client |

All Java paths are relative to `zms-be/health-service/src/main/java/it/zoo/health/`; test paths to `src/test/java/it/zoo/health/`.

---

### Task 1: Module skeleton and build wiring

**Files:**
- Create: `zms-be/health-service/pom.xml`
- Create: `zms-be/health-service/mvnw`, `mvnw.cmd`, `.mvn/` (copied)
- Create: `zms-be/health-service/src/main/resources/application.properties`
- Create: `zms-be/health-service/env.example`
- Create: `zms-be/health-service/.gitignore`
- Modify: `zms-be/pom.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable module `it.zoo:health-service`, base package `it.zoo.health`, reachable by `./mvnw verify` from `zms-be/health-service`.

- [ ] **Step 1: Register the module in the parent POM**

In `zms-be/pom.xml`, replace the `<modules>` block:

```xml
    <modules>
        <module>animal-service</module>
        <module>health-service</module>
    </modules>
```

- [ ] **Step 2: Copy the Maven wrapper**

```bash
cd zms-be
cp animal-service/mvnw health-service/mvnw
cp animal-service/mvnw.cmd health-service/mvnw.cmd
cp -r animal-service/.mvn health-service/.mvn
```

- [ ] **Step 3: Create the module POM**

`zms-be/health-service/pom.xml` — identical to `animal-service/pom.xml` except `<artifactId>`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>it.zoo</groupId>
        <artifactId>zoo-management-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>health-service</artifactId>
    <packaging>jar</packaging>

    <properties>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm-panache</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-rest-jackson</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-openapi</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-oidc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-validator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-test-security</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
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
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                        <maven.home>${maven.home}</maven.home>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
            <plugin>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <systemPropertyVariables>
                        <native.image.path>${project.build.directory}/${project.build.finalName}-runner</native.image.path>
                        <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                        <maven.home>${maven.home}</maven.home>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>native</id>
            <activation>
                <property>
                    <name>native</name>
                </property>
            </activation>
            <properties>
                <quarkus.package.jar.enabled>false</quarkus.package.jar.enabled>
                <skipITs>false</skipITs>
                <quarkus.native.enabled>true</quarkus.native.enabled>
            </properties>
        </profile>
    </profiles>

</project>
```

- [ ] **Step 4: Create `application.properties`**

`zms-be/health-service/src/main/resources/application.properties`:

```properties
quarkus.oidc.enabled=false

%dev.quarkus.http.port=8082
%dev.quarkus.oidc.enabled=true
%dev.quarkus.oidc.auth-server-url=http://localhost:8081/realms/zoo
%dev.quarkus.oidc.client-id=health-service
%dev.quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
%dev.quarkus.oidc.application-type=service
%dev.quarkus.oidc.roles.role-claim-path=realm_access/roles

%dev.quarkus.datasource.db-kind=postgresql
%dev.quarkus.datasource.username=${DB_USERNAME:zoo}
%dev.quarkus.datasource.password=${DB_PASSWORD}
%dev.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5433/health_db
%dev.quarkus.hibernate-orm.database.generation=none
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.devservices.enabled=false

%dev.quarkus.http.cors=true
%dev.quarkus.http.cors.origins=http://localhost:4200
%dev.quarkus.http.cors.methods=GET,POST,PUT,OPTIONS
%dev.quarkus.http.cors.headers=Content-Type,Authorization

%prod.quarkus.oidc.enabled=true
%prod.quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL}
%prod.quarkus.oidc.client-id=${OIDC_CLIENT_ID:health-service}
%prod.quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
%prod.quarkus.oidc.application-type=service
%prod.quarkus.oidc.roles.role-claim-path=realm_access/roles

%prod.quarkus.datasource.db-kind=postgresql
%prod.quarkus.datasource.username=${DB_USERNAME}
%prod.quarkus.datasource.password=${DB_PASSWORD}
%prod.quarkus.datasource.jdbc.url=${DB_JDBC_URL}
%prod.quarkus.hibernate-orm.database.generation=none
%prod.quarkus.flyway.migrate-at-start=true

%prod.quarkus.http.cors=${CORS_ENABLED:false}
%prod.quarkus.http.cors.origins=${CORS_ORIGINS:}
%prod.quarkus.http.cors.methods=GET,POST,PUT,OPTIONS
%prod.quarkus.http.cors.headers=Content-Type,Authorization

%test.quarkus.datasource.db-kind=postgresql
%test.quarkus.hibernate-orm.database.generation=none
%test.quarkus.flyway.migrate-at-start=true
```

Note the deliberate asymmetry with `animal-service`: `quarkus.http.port` is set under `%dev` only. A global value would also move the test harness off its default port.

- [ ] **Step 5: Create `env.example` and `.gitignore`**

`zms-be/health-service/env.example`:

```bash
# Copy to .env in this directory (git-ignored) before running mvnw quarkus:dev:
#   cp env.example .env
# Quarkus reads .env automatically. Local development only.

# Must match POSTGRES_HEALTH_USER / POSTGRES_HEALTH_PASSWORD in infrastructure/.env
DB_USERNAME=zoo
DB_PASSWORD=change-me-locally

# Must match HEALTH_OIDC_CLIENT_SECRET in infrastructure/.env
OIDC_CLIENT_SECRET=change-me-locally
```

`zms-be/health-service/.gitignore`:

```
.env
target/
```

- [ ] **Step 6: Verify the module builds**

Run:

```bash
cd zms-be/health-service && ./mvnw -B --no-transfer-progress verify
```

Expected: `BUILD SUCCESS`. No tests run yet — the module has no sources.

- [ ] **Step 7: Commit**

```bash
git add zms-be/pom.xml zms-be/health-service
git commit -m "build(health-service): scaffold the module

Second Maven module in the reactor, mirroring animal-service. The HTTP port
is set under %dev only so the test harness keeps its default."
```

---

### Task 2: Treatment lifecycle in the domain

**Files:**
- Create: `domain/enums/TreatmentStatus.java`
- Create: `domain/model/Treatment.java`
- Test: `src/test/java/it/zoo/health/domain/TreatmentStatusTransitionTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `TreatmentStatus` with constants `PRESCRIBED`, `ACTIVE`, `COMPLETED`, `CANCELLED`; `Treatment` with the constructor `Treatment(UUID id, UUID medicalRecordId, String description, TreatmentStatus status)`, accessors `getId/getMedicalRecordId/getDescription/getStatus/getStartedOn/getEndedOn/getCreatedBy/getUpdatedBy/getVersion` with matching setters, and `boolean canTransitionTo(TreatmentStatus target)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/health/domain/TreatmentStatusTransitionTest.java`:

```java
package it.zoo.health.domain;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TreatmentStatusTransitionTest {

    private Treatment treatmentWithStatus(TreatmentStatus status) {
        return new Treatment(UUID.randomUUID(), UUID.randomUUID(), "Antibiotics", status);
    }

    @Test
    void shouldAllowTransitionFromPrescribedToActive() {
        assertTrue(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.ACTIVE));
    }

    @Test
    void shouldAllowTransitionFromPrescribedToCancelled() {
        assertTrue(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.CANCELLED));
    }

    @Test
    void shouldRejectTransitionFromPrescribedToCompleted() {
        assertFalse(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.COMPLETED));
    }

    @Test
    void shouldAllowTransitionFromActiveToCompleted() {
        assertTrue(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.COMPLETED));
    }

    @Test
    void shouldAllowTransitionFromActiveToCancelled() {
        assertTrue(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.CANCELLED));
    }

    @Test
    void shouldRejectTransitionFromActiveBackToPrescribed() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectAnyTransitionWhenCompleted() {
        Treatment completed = treatmentWithStatus(TreatmentStatus.COMPLETED);
        assertFalse(completed.canTransitionTo(TreatmentStatus.ACTIVE));
        assertFalse(completed.canTransitionTo(TreatmentStatus.CANCELLED));
        assertFalse(completed.canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectAnyTransitionWhenCancelled() {
        Treatment cancelled = treatmentWithStatus(TreatmentStatus.CANCELLED);
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.ACTIVE));
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.COMPLETED));
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectTransitionToSameStatus() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.ACTIVE));
    }

    @Test
    void shouldRejectTransitionToNull() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=TreatmentStatusTransitionTest
```

Expected: compilation failure — `package it.zoo.health.domain.enums does not exist`.

- [ ] **Step 3: Write the enum**

`domain/enums/TreatmentStatus.java`:

```java
package it.zoo.health.domain.enums;

public enum TreatmentStatus {
    PRESCRIBED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
```

- [ ] **Step 4: Write the model**

`domain/model/Treatment.java`:

```java
package it.zoo.health.domain.model;

import it.zoo.health.domain.enums.TreatmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public class Treatment {

    private UUID id;
    private UUID medicalRecordId;
    private String description;
    private TreatmentStatus status;
    private LocalDate startedOn;
    private LocalDate endedOn;
    private String createdBy;
    private String updatedBy;
    private Long version;

    public Treatment() {}

    public Treatment(UUID id, UUID medicalRecordId, String description, TreatmentStatus status) {
        this.id = id;
        this.medicalRecordId = medicalRecordId;
        this.description = description;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getMedicalRecordId() { return medicalRecordId; }
    public String getDescription() { return description; }
    public TreatmentStatus getStatus() { return status; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setMedicalRecordId(UUID medicalRecordId) { this.medicalRecordId = medicalRecordId; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(TreatmentStatus status) { this.status = status; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }

    public boolean canTransitionTo(TreatmentStatus target) {
        if (target == null || target == this.status) {
            return false;
        }
        return switch (this.status) {
            case PRESCRIBED -> target == TreatmentStatus.ACTIVE || target == TreatmentStatus.CANCELLED;
            case ACTIVE -> target == TreatmentStatus.COMPLETED || target == TreatmentStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=TreatmentStatusTransitionTest
```

Expected: 10 tests, all PASS.

- [ ] **Step 6: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add treatment lifecycle to the domain

PRESCRIBED and ACTIVE both reach CANCELLED; COMPLETED and CANCELLED are
terminal. Same-status and null targets are rejected so callers never see a
no-op transition succeed."
```

---

### Task 3: Remaining domain — records, page, detail, exceptions, ports

**Files:**
- Create: `domain/model/MedicalRecord.java`, `domain/model/MedicalRecordPage.java`, `domain/model/MedicalRecordDetail.java`
- Create: `domain/exception/MedicalRecordNotFoundException.java`, `TreatmentNotFoundException.java`, `InvalidMedicalDataException.java`, `InvalidTreatmentStatusTransitionException.java`, `ConcurrentMedicalRecordUpdateException.java`, `ConcurrentTreatmentUpdateException.java`
- Create: `domain/port/in/CreateMedicalRecordUseCase.java`, `CreateMedicalRecordCommand.java`, `GetMedicalRecordUseCase.java`, `ListMedicalRecordsUseCase.java`, `PrescribeTreatmentUseCase.java`, `PrescribeTreatmentCommand.java`, `UpdateTreatmentStatusUseCase.java`
- Create: `domain/port/out/MedicalRecordRepository.java`, `TreatmentRepository.java`
- Test: `src/test/java/it/zoo/health/domain/DomainPurityTest.java`

**Interfaces:**
- Consumes: `Treatment`, `TreatmentStatus` from Task 2.
- Produces: every type the application layer needs. Exact signatures below — later tasks depend on these names verbatim.

- [ ] **Step 1: Write the failing purity test**

This is the domain's real invariant: it must not import a framework. `src/test/java/it/zoo/health/domain/DomainPurityTest.java`:

```java
package it.zoo.health.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainPurityTest {

    @Test
    void shouldNotImportAnyFrameworkInsideDomain() throws IOException {
        Path domain = Path.of("src/main/java/it/zoo/health/domain");
        assertTrue(Files.isDirectory(domain), "domain package must exist at " + domain.toAbsolutePath());

        try (Stream<Path> files = Files.walk(domain)) {
            List<String> offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .filter(line -> line.startsWith("import "))
                                    .filter(line -> line.contains("jakarta.")
                                            || line.contains("io.quarkus")
                                            || line.contains("org.hibernate")
                                            || line.contains("org.mapstruct"))
                                    .map(line -> p.getFileName() + ": " + line.trim());
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertTrue(offenders.isEmpty(), "Framework imports leaked into domain/: " + offenders);
        }
    }
}
```

- [ ] **Step 2: Run the test to establish the baseline**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=DomainPurityTest
```

Expected: **PASS**. Unlike the other tests in this plan, this one is not a red-to-green driver — it is a guard rail that must stay green from here on. It earns its place in Step 7, after this task adds nine more domain files, any one of which could have reached for a framework annotation.

- [ ] **Step 3: Write the models**

`domain/model/MedicalRecord.java`:

```java
package it.zoo.health.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public class MedicalRecord {

    private UUID id;
    private UUID animalId;
    private String reason;
    private String diagnosis;
    private LocalDate examinedOn;
    private String veterinarian;
    private String createdBy;
    private String updatedBy;
    private Long version;

    public MedicalRecord() {}

    public MedicalRecord(UUID id, UUID animalId, String reason, String diagnosis,
                         LocalDate examinedOn, String veterinarian) {
        this.id = id;
        this.animalId = animalId;
        this.reason = reason;
        this.diagnosis = diagnosis;
        this.examinedOn = examinedOn;
        this.veterinarian = veterinarian;
    }

    public UUID getId() { return id; }
    public UUID getAnimalId() { return animalId; }
    public String getReason() { return reason; }
    public String getDiagnosis() { return diagnosis; }
    public LocalDate getExaminedOn() { return examinedOn; }
    public String getVeterinarian() { return veterinarian; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public void setExaminedOn(LocalDate examinedOn) { this.examinedOn = examinedOn; }
    public void setVeterinarian(String veterinarian) { this.veterinarian = veterinarian; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }
}
```

`domain/model/MedicalRecordPage.java`:

```java
package it.zoo.health.domain.model;

import java.util.List;

public record MedicalRecordPage(List<MedicalRecord> items, int page, int size, long total) {}
```

`domain/model/MedicalRecordDetail.java`:

```java
package it.zoo.health.domain.model;

import java.util.List;

public record MedicalRecordDetail(MedicalRecord record, List<Treatment> treatments) {}
```

- [ ] **Step 4: Write the exceptions**

`domain/exception/MedicalRecordNotFoundException.java`:

```java
package it.zoo.health.domain.exception;

import java.util.UUID;

public class MedicalRecordNotFoundException extends RuntimeException {
    public MedicalRecordNotFoundException(UUID id) {
        super("Medical record not found with id: " + id);
    }
}
```

`domain/exception/TreatmentNotFoundException.java`:

```java
package it.zoo.health.domain.exception;

import java.util.UUID;

public class TreatmentNotFoundException extends RuntimeException {
    public TreatmentNotFoundException(UUID id) {
        super("Treatment not found with id: " + id);
    }
}
```

`domain/exception/InvalidMedicalDataException.java`:

```java
package it.zoo.health.domain.exception;

public class InvalidMedicalDataException extends RuntimeException {
    public InvalidMedicalDataException(String message) {
        super(message);
    }
}
```

`domain/exception/InvalidTreatmentStatusTransitionException.java`:

```java
package it.zoo.health.domain.exception;

import it.zoo.health.domain.enums.TreatmentStatus;

public class InvalidTreatmentStatusTransitionException extends RuntimeException {
    public InvalidTreatmentStatusTransitionException(TreatmentStatus from, TreatmentStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
```

`domain/exception/ConcurrentMedicalRecordUpdateException.java`:

```java
package it.zoo.health.domain.exception;

import java.util.UUID;

public class ConcurrentMedicalRecordUpdateException extends RuntimeException {
    public ConcurrentMedicalRecordUpdateException(UUID id) {
        super("Medical record was modified concurrently, retry the operation: " + id);
    }
}
```

`domain/exception/ConcurrentTreatmentUpdateException.java`:

```java
package it.zoo.health.domain.exception;

import java.util.UUID;

public class ConcurrentTreatmentUpdateException extends RuntimeException {
    public ConcurrentTreatmentUpdateException(UUID id) {
        super("Treatment was modified concurrently, retry the operation: " + id);
    }
}
```

- [ ] **Step 5: Write the inbound ports**

`domain/port/in/CreateMedicalRecordCommand.java`:

```java
package it.zoo.health.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMedicalRecordCommand(
    UUID animalId,
    String reason,
    String diagnosis,
    LocalDate examinedOn,
    String veterinarian,
    String performedBy
) {}
```

`domain/port/in/CreateMedicalRecordUseCase.java`:

```java
package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecord;

public interface CreateMedicalRecordUseCase {
    MedicalRecord create(CreateMedicalRecordCommand cmd);
}
```

`domain/port/in/GetMedicalRecordUseCase.java`:

```java
package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecordDetail;

import java.util.UUID;

public interface GetMedicalRecordUseCase {
    MedicalRecordDetail getById(UUID id);
}
```

`domain/port/in/ListMedicalRecordsUseCase.java`:

```java
package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecordPage;

import java.util.UUID;

public interface ListMedicalRecordsUseCase {

    int MAX_PAGE_SIZE = 100;

    MedicalRecordPage list(UUID animalId, int page, int size);
}
```

`domain/port/in/PrescribeTreatmentCommand.java`:

```java
package it.zoo.health.domain.port.in;

import java.util.UUID;

public record PrescribeTreatmentCommand(
    UUID medicalRecordId,
    String description,
    String performedBy
) {}
```

`domain/port/in/PrescribeTreatmentUseCase.java`:

```java
package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.Treatment;

public interface PrescribeTreatmentUseCase {
    Treatment prescribe(PrescribeTreatmentCommand cmd);
}
```

`domain/port/in/UpdateTreatmentStatusUseCase.java`:

```java
package it.zoo.health.domain.port.in;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;

import java.util.UUID;

public interface UpdateTreatmentStatusUseCase {
    Treatment updateStatus(UUID id, TreatmentStatus newStatus, String performedBy);
}
```

- [ ] **Step 6: Write the outbound ports**

`domain/port/out/MedicalRecordRepository.java`:

```java
package it.zoo.health.domain.port.out;

import it.zoo.health.domain.model.MedicalRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository {
    MedicalRecord save(MedicalRecord record);
    Optional<MedicalRecord> findById(UUID id);
    List<MedicalRecord> findPage(UUID animalId, int page, int size);
    long count(UUID animalId);
    boolean existsById(UUID id);
}
```

A `null` `animalId` means "no filter" for both `findPage` and `count`.

`domain/port/out/TreatmentRepository.java`:

```java
package it.zoo.health.domain.port.out;

import it.zoo.health.domain.model.Treatment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRepository {
    Treatment save(Treatment treatment);
    Optional<Treatment> findById(UUID id);
    List<Treatment> findByMedicalRecordId(UUID medicalRecordId);
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test
```

Expected: `DomainPurityTest` PASS and `TreatmentStatusTransitionTest` PASS (11 tests total).

- [ ] **Step 8: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add domain models, exceptions and ports

MedicalRecordDetail exists because GET /medical-records/{id} is the only way
a treatment is ever read back; without it treatments would be write-only.
DomainPurityTest guards the dependency rule mechanically instead of by review."
```

---

### Task 4: CreateMedicalRecordService

**Files:**
- Create: `application/CreateMedicalRecordService.java`
- Test: `src/test/java/it/zoo/health/application/CreateMedicalRecordServiceTest.java`

**Interfaces:**
- Consumes: `CreateMedicalRecordCommand`, `CreateMedicalRecordUseCase`, `MedicalRecordRepository`, `MedicalRecord`, `InvalidMedicalDataException`.
- Produces: `CreateMedicalRecordService implements CreateMedicalRecordUseCase`, `@ApplicationScoped`, constructor `CreateMedicalRecordService(MedicalRecordRepository repository)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/health/application/CreateMedicalRecordServiceTest.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.in.CreateMedicalRecordCommand;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
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
class CreateMedicalRecordServiceTest {

    @Mock
    MedicalRecordRepository repository;

    @InjectMocks
    CreateMedicalRecordService service;

    private CreateMedicalRecordCommand validCommand() {
        return new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");
    }

    @Test
    void shouldCreateMedicalRecord() {
        when(repository.save(any(MedicalRecord.class))).thenAnswer(i -> i.getArgument(0));
        CreateMedicalRecordCommand cmd = validCommand();

        MedicalRecord result = service.create(cmd);

        assertNotNull(result.getId());
        assertEquals(cmd.animalId(), result.getAnimalId());
        assertEquals("Limping", result.getReason());
        assertEquals("Sprained paw", result.getDiagnosis());
        assertEquals("Dr Rossi", result.getVeterinarian());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                null, "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenReasonIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "  ", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenDiagnosisIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenVeterinarianIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenExaminedOnIsNull() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                null, "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenExaminedOnIsInTheFuture() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.now().plusDays(1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=CreateMedicalRecordServiceTest
```

Expected: compilation failure — `cannot find symbol: class CreateMedicalRecordService`.

- [ ] **Step 3: Write the implementation**

`application/CreateMedicalRecordService.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.in.CreateMedicalRecordCommand;
import it.zoo.health.domain.port.in.CreateMedicalRecordUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class CreateMedicalRecordService implements CreateMedicalRecordUseCase {

    private final MedicalRecordRepository repository;

    public CreateMedicalRecordService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MedicalRecord create(CreateMedicalRecordCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }
        if (cmd.animalId() == null) {
            throw new InvalidMedicalDataException("Animal ID must not be null");
        }
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new InvalidMedicalDataException("Reason must not be blank");
        }
        if (cmd.diagnosis() == null || cmd.diagnosis().isBlank()) {
            throw new InvalidMedicalDataException("Diagnosis must not be blank");
        }
        if (cmd.veterinarian() == null || cmd.veterinarian().isBlank()) {
            throw new InvalidMedicalDataException("Veterinarian must not be blank");
        }
        if (cmd.examinedOn() == null) {
            throw new InvalidMedicalDataException("Examination date must not be null");
        }
        if (cmd.examinedOn().isAfter(LocalDate.now())) {
            throw new InvalidMedicalDataException("Examination date must not be in the future");
        }

        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(),
                cmd.animalId(),
                cmd.reason(),
                cmd.diagnosis(),
                cmd.examinedOn(),
                cmd.veterinarian()
        );
        record.setCreatedBy(cmd.performedBy());
        record.setUpdatedBy(cmd.performedBy());
        return repository.save(record);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=CreateMedicalRecordServiceTest
```

Expected: 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add CreateMedicalRecordService

The animalId is required but never dereferenced: this service owns no animal
data and must not couple to animal-service to accept a write."
```

---

### Task 5: GetMedicalRecordService and ListMedicalRecordsService

**Files:**
- Create: `application/GetMedicalRecordService.java`, `application/ListMedicalRecordsService.java`
- Test: `src/test/java/it/zoo/health/application/GetMedicalRecordServiceTest.java`, `ListMedicalRecordsServiceTest.java`

**Interfaces:**
- Consumes: `MedicalRecordRepository`, `TreatmentRepository`, `MedicalRecordDetail`, `MedicalRecordPage`, `MedicalRecordNotFoundException`, `InvalidMedicalDataException`, `ListMedicalRecordsUseCase.MAX_PAGE_SIZE`.
- Produces: `GetMedicalRecordService(MedicalRecordRepository recordRepository, TreatmentRepository treatmentRepository)` and `ListMedicalRecordsService(MedicalRecordRepository repository)`, both `@ApplicationScoped`, neither transactional.

- [ ] **Step 1: Write the failing tests**

`src/test/java/it/zoo/health/application/GetMedicalRecordServiceTest.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMedicalRecordServiceTest {

    @Mock
    MedicalRecordRepository recordRepository;

    @Mock
    TreatmentRepository treatmentRepository;

    @InjectMocks
    GetMedicalRecordService service;

    @Test
    void shouldReturnRecordWithItsTreatments() {
        UUID id = UUID.randomUUID();
        MedicalRecord record = new MedicalRecord(id, UUID.randomUUID(), "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
        Treatment treatment = new Treatment(UUID.randomUUID(), id, "Rest", TreatmentStatus.PRESCRIBED);
        when(recordRepository.findById(id)).thenReturn(Optional.of(record));
        when(treatmentRepository.findByMedicalRecordId(id)).thenReturn(List.of(treatment));

        MedicalRecordDetail result = service.getById(id);

        assertEquals(id, result.record().getId());
        assertEquals(1, result.treatments().size());
        assertEquals("Rest", result.treatments().get(0).getDescription());
    }

    @Test
    void shouldReturnEmptyTreatmentListWhenNonePrescribed() {
        UUID id = UUID.randomUUID();
        MedicalRecord record = new MedicalRecord(id, UUID.randomUUID(), "Checkup",
                "Healthy", LocalDate.of(2026, 9, 1), "Dr Rossi");
        when(recordRepository.findById(id)).thenReturn(Optional.of(record));
        when(treatmentRepository.findByMedicalRecordId(id)).thenReturn(List.of());

        MedicalRecordDetail result = service.getById(id);

        assertTrue(result.treatments().isEmpty());
    }

    @Test
    void shouldThrowWhenRecordNotFound() {
        UUID id = UUID.randomUUID();
        when(recordRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(MedicalRecordNotFoundException.class, () -> service.getById(id));
    }
}
```

`src/test/java/it/zoo/health/application/ListMedicalRecordsServiceTest.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.port.in.ListMedicalRecordsUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
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
class ListMedicalRecordsServiceTest {

    @Mock
    MedicalRecordRepository repository;

    @InjectMocks
    ListMedicalRecordsService service;

    private MedicalRecord record(UUID animalId) {
        return new MedicalRecord(UUID.randomUUID(), animalId, "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
    }

    @Test
    void shouldReturnPageFilteredByAnimal() {
        UUID animalId = UUID.randomUUID();
        when(repository.findPage(animalId, 0, 20)).thenReturn(List.of(record(animalId)));
        when(repository.count(animalId)).thenReturn(1L);

        MedicalRecordPage result = service.list(animalId, 0, 20);

        assertEquals(1, result.items().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.total());
    }

    @Test
    void shouldListEveryRecordWhenAnimalIdIsNull() {
        when(repository.findPage(null, 0, 20)).thenReturn(List.of(record(UUID.randomUUID())));
        when(repository.count(null)).thenReturn(1L);

        MedicalRecordPage result = service.list(null, 0, 20);

        assertEquals(1, result.items().size());
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        assertThrows(InvalidMedicalDataException.class, () -> service.list(null, -1, 20));
    }

    @Test
    void shouldThrowWhenSizeIsBelowOne() {
        assertThrows(InvalidMedicalDataException.class, () -> service.list(null, 0, 0));
    }

    @Test
    void shouldThrowWhenSizeExceedsMaximum() {
        assertThrows(InvalidMedicalDataException.class,
                () -> service.list(null, 0, ListMedicalRecordsUseCase.MAX_PAGE_SIZE + 1));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest='GetMedicalRecordServiceTest,ListMedicalRecordsServiceTest'
```

Expected: compilation failure — both service classes are missing.

- [ ] **Step 3: Write GetMedicalRecordService**

`application/GetMedicalRecordService.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.port.in.GetMedicalRecordUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class GetMedicalRecordService implements GetMedicalRecordUseCase {

    private final MedicalRecordRepository recordRepository;
    private final TreatmentRepository treatmentRepository;

    public GetMedicalRecordService(MedicalRecordRepository recordRepository,
                                   TreatmentRepository treatmentRepository) {
        this.recordRepository = recordRepository;
        this.treatmentRepository = treatmentRepository;
    }

    @Override
    public MedicalRecordDetail getById(UUID id) {
        MedicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new MedicalRecordNotFoundException(id));
        return new MedicalRecordDetail(record, treatmentRepository.findByMedicalRecordId(id));
    }
}
```

- [ ] **Step 4: Write ListMedicalRecordsService**

`application/ListMedicalRecordsService.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.port.in.ListMedicalRecordsUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListMedicalRecordsService implements ListMedicalRecordsUseCase {

    private final MedicalRecordRepository repository;

    public ListMedicalRecordsService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public MedicalRecordPage list(UUID animalId, int page, int size) {
        if (page < 0) {
            throw new InvalidMedicalDataException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidMedicalDataException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidMedicalDataException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        List<MedicalRecord> items = repository.findPage(animalId, page, size);
        return new MedicalRecordPage(items, page, size, repository.count(animalId));
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest='GetMedicalRecordServiceTest,ListMedicalRecordsServiceTest'
```

Expected: 8 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add read services for medical records

The detail read joins treatments in the application layer rather than the
repository, so neither port has to know about the other's aggregate."
```

---

### Task 6: PrescribeTreatmentService

**Files:**
- Create: `application/PrescribeTreatmentService.java`
- Test: `src/test/java/it/zoo/health/application/PrescribeTreatmentServiceTest.java`

**Interfaces:**
- Consumes: `PrescribeTreatmentCommand`, `TreatmentRepository`, `MedicalRecordRepository.existsById`, `MedicalRecordNotFoundException`, `InvalidMedicalDataException`.
- Produces: `PrescribeTreatmentService(MedicalRecordRepository recordRepository, TreatmentRepository treatmentRepository)`, `@ApplicationScoped`, `@Transactional` on `prescribe`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/health/application/PrescribeTreatmentServiceTest.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescribeTreatmentServiceTest {

    @Mock
    MedicalRecordRepository recordRepository;

    @Mock
    TreatmentRepository treatmentRepository;

    @InjectMocks
    PrescribeTreatmentService service;

    @Test
    void shouldPrescribeTreatmentInPrescribedStatus() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.existsById(recordId)).thenReturn(true);
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet"));

        assertNotNull(result.getId());
        assertEquals(recordId, result.getMedicalRecordId());
        assertEquals("Antibiotics", result.getDescription());
        assertEquals(TreatmentStatus.PRESCRIBED, result.getStatus());
        assertNull(result.getStartedOn());
        assertNull(result.getEndedOn());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenMedicalRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.existsById(recordId)).thenReturn(false);

        assertThrows(MedicalRecordNotFoundException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet")));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(UUID.randomUUID(), "Antibiotics", " ")));
    }

    @Test
    void shouldThrowWhenMedicalRecordIdIsNull() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(null, "Antibiotics", "vet")));
    }

    @Test
    void shouldThrowWhenDescriptionIsBlank() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(UUID.randomUUID(), "", "vet")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=PrescribeTreatmentServiceTest
```

Expected: compilation failure — `cannot find symbol: class PrescribeTreatmentService`.

- [ ] **Step 3: Write the implementation**

`application/PrescribeTreatmentService.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.in.PrescribeTreatmentUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class PrescribeTreatmentService implements PrescribeTreatmentUseCase {

    private final MedicalRecordRepository recordRepository;
    private final TreatmentRepository treatmentRepository;

    public PrescribeTreatmentService(MedicalRecordRepository recordRepository,
                                     TreatmentRepository treatmentRepository) {
        this.recordRepository = recordRepository;
        this.treatmentRepository = treatmentRepository;
    }

    @Override
    @Transactional
    public Treatment prescribe(PrescribeTreatmentCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }
        if (cmd.medicalRecordId() == null) {
            throw new InvalidMedicalDataException("Medical record ID must not be null");
        }
        if (cmd.description() == null || cmd.description().isBlank()) {
            throw new InvalidMedicalDataException("Treatment description must not be blank");
        }
        if (!recordRepository.existsById(cmd.medicalRecordId())) {
            throw new MedicalRecordNotFoundException(cmd.medicalRecordId());
        }

        Treatment treatment = new Treatment(
                UUID.randomUUID(),
                cmd.medicalRecordId(),
                cmd.description(),
                TreatmentStatus.PRESCRIBED
        );
        treatment.setCreatedBy(cmd.performedBy());
        treatment.setUpdatedBy(cmd.performedBy());
        return treatmentRepository.save(treatment);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=PrescribeTreatmentServiceTest
```

Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add PrescribeTreatmentService

The parent record is checked because it lives in this service's own database;
that is a local invariant, not a cross-service call."
```

---

### Task 7: UpdateTreatmentStatusService

**Files:**
- Create: `application/UpdateTreatmentStatusService.java`
- Test: `src/test/java/it/zoo/health/application/UpdateTreatmentStatusServiceTest.java`

**Interfaces:**
- Consumes: `TreatmentRepository`, `Treatment.canTransitionTo`, `TreatmentNotFoundException`, `InvalidTreatmentStatusTransitionException`, `InvalidMedicalDataException`.
- Produces: `UpdateTreatmentStatusService(TreatmentRepository repository)`, `@ApplicationScoped`, `@Transactional` on `updateStatus`. Sets `startedOn` on `ACTIVE`, `endedOn` on `COMPLETED` and `CANCELLED`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/health/application/UpdateTreatmentStatusServiceTest.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.TreatmentRepository;
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
class UpdateTreatmentStatusServiceTest {

    @Mock
    TreatmentRepository repository;

    @InjectMocks
    UpdateTreatmentStatusService service;

    private Treatment treatmentWithStatus(UUID id, TreatmentStatus status) {
        return new Treatment(id, UUID.randomUUID(), "Antibiotics", status);
    }

    @Test
    void shouldSetStartedOnWhenActivated() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.PRESCRIBED)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.ACTIVE, "vet");

        assertEquals(TreatmentStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.now(), result.getStartedOn());
        assertNull(result.getEndedOn());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldSetEndedOnWhenCompleted() {
        UUID id = UUID.randomUUID();
        Treatment active = treatmentWithStatus(id, TreatmentStatus.ACTIVE);
        active.setStartedOn(LocalDate.of(2026, 9, 1));
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.COMPLETED, "vet");

        assertEquals(TreatmentStatus.COMPLETED, result.getStatus());
        assertEquals(LocalDate.of(2026, 9, 1), result.getStartedOn());
        assertEquals(LocalDate.now(), result.getEndedOn());
    }

    @Test
    void shouldSetEndedOnWhenCancelled() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.PRESCRIBED)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.CANCELLED, "vet");

        assertEquals(TreatmentStatus.CANCELLED, result.getStatus());
        assertEquals(LocalDate.now(), result.getEndedOn());
    }

    @Test
    void shouldNotOverwriteStartedOnWhenAlreadySet() {
        UUID id = UUID.randomUUID();
        Treatment prescribed = treatmentWithStatus(id, TreatmentStatus.PRESCRIBED);
        prescribed.setStartedOn(LocalDate.of(2026, 1, 1));
        when(repository.findById(id)).thenReturn(Optional.of(prescribed));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.ACTIVE, "vet");

        assertEquals(LocalDate.of(2026, 1, 1), result.getStartedOn());
    }

    @Test
    void shouldThrowWhenTreatmentNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(TreatmentNotFoundException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.COMPLETED)));

        assertThrows(InvalidTreatmentStatusTransitionException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        assertThrows(InvalidMedicalDataException.class,
                () -> service.updateStatus(UUID.randomUUID(), TreatmentStatus.ACTIVE, ""));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=UpdateTreatmentStatusServiceTest
```

Expected: compilation failure — `cannot find symbol: class UpdateTreatmentStatusService`.

- [ ] **Step 3: Write the implementation**

`application/UpdateTreatmentStatusService.java`:

```java
package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class UpdateTreatmentStatusService implements UpdateTreatmentStatusUseCase {

    private final TreatmentRepository repository;

    public UpdateTreatmentStatusService(TreatmentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Treatment updateStatus(UUID id, TreatmentStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }

        Treatment treatment = repository.findById(id)
                .orElseThrow(() -> new TreatmentNotFoundException(id));

        if (!treatment.canTransitionTo(newStatus)) {
            throw new InvalidTreatmentStatusTransitionException(treatment.getStatus(), newStatus);
        }

        treatment.setStatus(newStatus);
        if (newStatus == TreatmentStatus.ACTIVE && treatment.getStartedOn() == null) {
            treatment.setStartedOn(LocalDate.now());
        }
        if (newStatus == TreatmentStatus.COMPLETED || newStatus == TreatmentStatus.CANCELLED) {
            treatment.setEndedOn(LocalDate.now());
        }
        treatment.setUpdatedBy(performedBy);
        return repository.save(treatment);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest=UpdateTreatmentStatusServiceTest
```

Expected: 7 tests PASS.

- [ ] **Step 5: Run the whole unit suite**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test
```

Expected: 39 tests PASS — 10 transition + 1 purity + 8 create + 3 get + 5 list + 5 prescribe + 7 status.

- [ ] **Step 6: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add UpdateTreatmentStatusService

Dates are a side effect of a legal transition and are never accepted from the
client, so a treatment cannot claim to have started before it was activated."
```

---

### Task 8: Persistence adapters and migration

**Files:**
- Create: `resources/db/migration/V1__create_medical_records_and_treatments.sql`
- Create: `infrastructure/persistence/MedicalRecordEntity.java`, `TreatmentEntity.java`, `MedicalRecordEntityMapper.java`, `TreatmentEntityMapper.java`, `MedicalRecordPanacheRepository.java`, `TreatmentPanacheRepository.java`
- Test: `src/test/java/it/zoo/health/infrastructure/persistence/MedicalRecordEntityMapperTest.java`, `TreatmentEntityMapperTest.java`

**Interfaces:**
- Consumes: every domain type from Tasks 2 and 3.
- Produces: `MedicalRecordPanacheRepository implements MedicalRecordRepository` and `TreatmentPanacheRepository implements TreatmentRepository`, both `@ApplicationScoped` with constructor `(EntityManager em)`. Static mappers expose `toDomain`, `toEntity`, `toDomainList`.

- [ ] **Step 1: Write the failing mapper tests**

`src/test/java/it/zoo/health/infrastructure/persistence/MedicalRecordEntityMapperTest.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.MedicalRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalRecordEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        MedicalRecord original = new MedicalRecord(id, animalId, "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
        original.setCreatedBy("vet");
        original.setUpdatedBy("admin");
        original.setVersion(3L);

        MedicalRecord result = MedicalRecordEntityMapper.toDomain(
                MedicalRecordEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(animalId, result.getAnimalId());
        assertEquals("Limping", result.getReason());
        assertEquals("Sprained paw", result.getDiagnosis());
        assertEquals(LocalDate.of(2026, 9, 1), result.getExaminedOn());
        assertEquals("Dr Rossi", result.getVeterinarian());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("admin", result.getUpdatedBy());
        assertEquals(3L, result.getVersion());
    }
}
```

`src/test/java/it/zoo/health/infrastructure/persistence/TreatmentEntityMapperTest.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreatmentEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Treatment original = new Treatment(id, recordId, "Antibiotics", TreatmentStatus.ACTIVE);
        original.setStartedOn(LocalDate.of(2026, 9, 2));
        original.setEndedOn(LocalDate.of(2026, 9, 9));
        original.setCreatedBy("vet");
        original.setUpdatedBy("admin");
        original.setVersion(2L);

        Treatment result = TreatmentEntityMapper.toDomain(
                TreatmentEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(recordId, result.getMedicalRecordId());
        assertEquals("Antibiotics", result.getDescription());
        assertEquals(TreatmentStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.of(2026, 9, 2), result.getStartedOn());
        assertEquals(LocalDate.of(2026, 9, 9), result.getEndedOn());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("admin", result.getUpdatedBy());
        assertEquals(2L, result.getVersion());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test -Dtest='*EntityMapperTest'
```

Expected: compilation failure — the mapper classes do not exist.

- [ ] **Step 3: Write the migration**

`resources/db/migration/V1__create_medical_records_and_treatments.sql`:

```sql
CREATE TABLE medical_records (
    id            UUID          PRIMARY KEY,
    animal_id     UUID          NOT NULL,
    reason        VARCHAR(200)  NOT NULL,
    diagnosis     VARCHAR(1000) NOT NULL,
    examined_on   DATE          NOT NULL,
    veterinarian  VARCHAR(100)  NOT NULL,
    created_by    VARCHAR(100)  NOT NULL,
    updated_by    VARCHAR(100),
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_medical_records_animal_id ON medical_records (animal_id);

CREATE TABLE treatments (
    id                UUID         PRIMARY KEY,
    medical_record_id UUID         NOT NULL REFERENCES medical_records (id),
    description       VARCHAR(500) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    started_on        DATE,
    ended_on          DATE,
    created_by        VARCHAR(100) NOT NULL,
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_treatments_medical_record_id ON treatments (medical_record_id);
```

There is no foreign key toward `animals`: that table lives in another database, owned by another service.

- [ ] **Step 4: Write the entities**

`infrastructure/persistence/MedicalRecordEntity.java`:

```java
package it.zoo.health.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_records")
public class MedicalRecordEntity {

    @Id
    private UUID id;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 1000)
    private String diagnosis;

    @Column(name = "examined_on", nullable = false)
    private LocalDate examinedOn;

    @Column(nullable = false, length = 100)
    private String veterinarian;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public MedicalRecordEntity() {}

    public UUID getId() { return id; }
    public UUID getAnimalId() { return animalId; }
    public String getReason() { return reason; }
    public String getDiagnosis() { return diagnosis; }
    public LocalDate getExaminedOn() { return examinedOn; }
    public String getVeterinarian() { return veterinarian; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public void setExaminedOn(LocalDate examinedOn) { this.examinedOn = examinedOn; }
    public void setVeterinarian(String veterinarian) { this.veterinarian = veterinarian; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }
}
```

`infrastructure/persistence/TreatmentEntity.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.enums.TreatmentStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "treatments")
public class TreatmentEntity {

    @Id
    private UUID id;

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentStatus status;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public TreatmentEntity() {}

    public UUID getId() { return id; }
    public UUID getMedicalRecordId() { return medicalRecordId; }
    public String getDescription() { return description; }
    public TreatmentStatus getStatus() { return status; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setMedicalRecordId(UUID medicalRecordId) { this.medicalRecordId = medicalRecordId; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(TreatmentStatus status) { this.status = status; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }
}
```

- [ ] **Step 5: Write the mappers**

`infrastructure/persistence/MedicalRecordEntityMapper.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.MedicalRecord;

import java.util.List;

public class MedicalRecordEntityMapper {

    private MedicalRecordEntityMapper() {}

    public static MedicalRecord toDomain(MedicalRecordEntity entity) {
        MedicalRecord record = new MedicalRecord(
                entity.getId(),
                entity.getAnimalId(),
                entity.getReason(),
                entity.getDiagnosis(),
                entity.getExaminedOn(),
                entity.getVeterinarian()
        );
        record.setCreatedBy(entity.getCreatedBy());
        record.setUpdatedBy(entity.getUpdatedBy());
        record.setVersion(entity.getVersion());
        return record;
    }

    public static MedicalRecordEntity toEntity(MedicalRecord record) {
        MedicalRecordEntity entity = new MedicalRecordEntity();
        entity.setId(record.getId());
        entity.setAnimalId(record.getAnimalId());
        entity.setReason(record.getReason());
        entity.setDiagnosis(record.getDiagnosis());
        entity.setExaminedOn(record.getExaminedOn());
        entity.setVeterinarian(record.getVeterinarian());
        entity.setCreatedBy(record.getCreatedBy());
        entity.setUpdatedBy(record.getUpdatedBy());
        entity.setVersion(record.getVersion());
        return entity;
    }

    public static List<MedicalRecord> toDomainList(List<MedicalRecordEntity> entities) {
        return entities.stream().map(MedicalRecordEntityMapper::toDomain).toList();
    }
}
```

`infrastructure/persistence/TreatmentEntityMapper.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.Treatment;

import java.util.List;

public class TreatmentEntityMapper {

    private TreatmentEntityMapper() {}

    public static Treatment toDomain(TreatmentEntity entity) {
        Treatment treatment = new Treatment(
                entity.getId(),
                entity.getMedicalRecordId(),
                entity.getDescription(),
                entity.getStatus()
        );
        treatment.setStartedOn(entity.getStartedOn());
        treatment.setEndedOn(entity.getEndedOn());
        treatment.setCreatedBy(entity.getCreatedBy());
        treatment.setUpdatedBy(entity.getUpdatedBy());
        treatment.setVersion(entity.getVersion());
        return treatment;
    }

    public static TreatmentEntity toEntity(Treatment treatment) {
        TreatmentEntity entity = new TreatmentEntity();
        entity.setId(treatment.getId());
        entity.setMedicalRecordId(treatment.getMedicalRecordId());
        entity.setDescription(treatment.getDescription());
        entity.setStatus(treatment.getStatus());
        entity.setStartedOn(treatment.getStartedOn());
        entity.setEndedOn(treatment.getEndedOn());
        entity.setCreatedBy(treatment.getCreatedBy());
        entity.setUpdatedBy(treatment.getUpdatedBy());
        entity.setVersion(treatment.getVersion());
        return entity;
    }

    public static List<Treatment> toDomainList(List<TreatmentEntity> entities) {
        return entities.stream().map(TreatmentEntityMapper::toDomain).toList();
    }
}
```

- [ ] **Step 6: Write the repository adapters**

`infrastructure/persistence/MedicalRecordPanacheRepository.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.exception.ConcurrentMedicalRecordUpdateException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MedicalRecordPanacheRepository implements MedicalRecordRepository {

    private final EntityManager em;

    public MedicalRecordPanacheRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public MedicalRecord save(MedicalRecord record) {
        MedicalRecordEntity entity = MedicalRecordEntityMapper.toEntity(record);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentMedicalRecordUpdateException(record.getId());
        }
        return MedicalRecordEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<MedicalRecord> findById(UUID id) {
        return Optional.ofNullable(em.find(MedicalRecordEntity.class, id))
                .map(MedicalRecordEntityMapper::toDomain);
    }

    @Override
    public List<MedicalRecord> findPage(UUID animalId, int page, int size) {
        String jpql = animalId == null
                ? "SELECT m FROM MedicalRecordEntity m ORDER BY m.examinedOn DESC, m.id"
                : "SELECT m FROM MedicalRecordEntity m WHERE m.animalId = :animalId ORDER BY m.examinedOn DESC, m.id";
        TypedQuery<MedicalRecordEntity> query = em.createQuery(jpql, MedicalRecordEntity.class);
        if (animalId != null) {
            query.setParameter("animalId", animalId);
        }
        return MedicalRecordEntityMapper.toDomainList(query
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList());
    }

    @Override
    public long count(UUID animalId) {
        if (animalId == null) {
            return em.createQuery("SELECT COUNT(m) FROM MedicalRecordEntity m", Long.class)
                    .getSingleResult();
        }
        return em.createQuery(
                        "SELECT COUNT(m) FROM MedicalRecordEntity m WHERE m.animalId = :animalId", Long.class)
                .setParameter("animalId", animalId)
                .getSingleResult();
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(MedicalRecordEntity.class, id) != null;
    }
}
```

`infrastructure/persistence/TreatmentPanacheRepository.java`:

```java
package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.exception.ConcurrentTreatmentUpdateException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TreatmentPanacheRepository implements TreatmentRepository {

    private final EntityManager em;

    public TreatmentPanacheRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Treatment save(Treatment treatment) {
        TreatmentEntity entity = TreatmentEntityMapper.toEntity(treatment);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentTreatmentUpdateException(treatment.getId());
        }
        return TreatmentEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Treatment> findById(UUID id) {
        return Optional.ofNullable(em.find(TreatmentEntity.class, id))
                .map(TreatmentEntityMapper::toDomain);
    }

    @Override
    public List<Treatment> findByMedicalRecordId(UUID medicalRecordId) {
        return TreatmentEntityMapper.toDomainList(em
                .createQuery("SELECT t FROM TreatmentEntity t WHERE t.medicalRecordId = :recordId ORDER BY t.id",
                        TreatmentEntity.class)
                .setParameter("recordId", medicalRecordId)
                .getResultList());
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run:

```bash
cd zms-be/health-service && ./mvnw -B test
```

Expected: 41 tests PASS — the 39 from Task 7 plus the 2 mapper round trips.

- [ ] **Step 8: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add persistence adapters and initial migration

One migration carries audit columns and the version column from the start,
rather than retrofitting them as animal-service did across V2 and V3. The
optimistic lock is rethrown as a domain exception so JPA never reaches REST."
```

---

### Task 9: REST layer

**Files:**
- Create: `infrastructure/security/ZooRoles.java`
- Create: `infrastructure/rest/dto/CreateMedicalRecordRequest.java`, `PrescribeTreatmentRequest.java`, `UpdateTreatmentStatusRequest.java`, `MedicalRecordResponse.java`, `MedicalRecordDetailResponse.java`, `TreatmentResponse.java`, `MedicalRecordPageResponse.java`, `ErrorResponse.java`
- Create: `infrastructure/rest/mapper/HealthDtoMapper.java`
- Create: `infrastructure/rest/MedicalRecordResource.java`, `TreatmentResource.java`, `OpenApiConfig.java`
- Create: `infrastructure/rest/MedicalRecordNotFoundExceptionMapper.java`, `TreatmentNotFoundExceptionMapper.java`, `InvalidMedicalDataExceptionMapper.java`, `InvalidTreatmentStatusTransitionExceptionMapper.java`, `ConcurrentMedicalRecordUpdateExceptionMapper.java`, `ConcurrentTreatmentUpdateExceptionMapper.java`, `UnexpectedExceptionMapper.java`, `SecurityExceptionMapper.java`
- Test: `src/test/java/it/zoo/health/infrastructure/rest/MedicalRecordResourceIT.java`

**Interfaces:**
- Consumes: all five use case interfaces, `MedicalRecordDetail`, `MedicalRecordPage`, `SecurityIdentity`.
- Produces: the HTTP surface described in the spec. `ZooRoles.ADMIN`/`VET`/`KEEPER` are `"zoo-admin"`/`"zoo-vet"`/`"zoo-keeper"`.

- [ ] **Step 1: Write the failing integration test**

`src/test/java/it/zoo/health/infrastructure/rest/MedicalRecordResourceIT.java`:

```java
package it.zoo.health.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "dr-rossi", roles = {ZooRoles.VET})
class MedicalRecordResourceIT {

    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();
        });
    }

    private String recordJson(String reason) {
        return "{"
                + "  \"animalId\": \"" + ANIMAL_ID + "\","
                + "  \"reason\": \"" + reason + "\","
                + "  \"diagnosis\": \"Sprained paw\","
                + "  \"examinedOn\": \"2026-09-01\","
                + "  \"veterinarian\": \"Dr Rossi\""
                + "}";
    }

    private String postRecord(String reason) {
        return given()
                .contentType(ContentType.JSON)
                .body(recordJson(reason))
            .when()
                .post("/medical-records")
            .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    void shouldCreateMedicalRecord() {
        given()
            .contentType(ContentType.JSON)
            .body(recordJson("Limping"))
        .when()
            .post("/medical-records")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("animalId", equalTo(ANIMAL_ID.toString()))
            .body("reason", equalTo("Limping"))
            .body("diagnosis", equalTo("Sprained paw"))
            .body("veterinarian", equalTo("Dr Rossi"));
    }

    @Test
    void shouldRejectMedicalRecordWithBlankReason() {
        given()
            .contentType(ContentType.JSON)
            .body(recordJson(""))
        .when()
            .post("/medical-records")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnRecordWithItsTreatments() {
        String recordId = postRecord("Limping");
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\": \"Antibiotics\"}")
        .when()
            .post("/medical-records/" + recordId + "/treatments")
        .then()
            .statusCode(201)
            .body("status", equalTo("PRESCRIBED"));

        given()
        .when()
            .get("/medical-records/" + recordId)
        .then()
            .statusCode(200)
            .body("id", equalTo(recordId))
            .body("treatments.size()", equalTo(1))
            .body("treatments[0].description", equalTo("Antibiotics"));
    }

    @Test
    void shouldReturn404WhenRecordDoesNotExist() {
        given()
        .when()
            .get("/medical-records/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("message", containsString("Medical record not found"));
    }

    @Test
    void shouldReturn404WhenIdIsNotAUuid() {
        // 404, not 400: an unparseable @PathParam UUID never reaches the resource,
        // so JAX-RS treats it as an unmatched path. AnimalResourceIT asserts the same.
        given()
        .when()
            .get("/medical-records/not-a-uuid")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldListRecordsWithPageEnvelope() {
        postRecord("Limping");
        postRecord("Checkup");

        given()
        .when()
            .get("/medical-records")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(2))
            .body("page", equalTo(0))
            .body("size", equalTo(20))
            .body("total", equalTo(2));
    }

    @Test
    void shouldFilterListByAnimalId() {
        postRecord("Limping");

        given()
            .queryParam("animalId", UUID.randomUUID().toString())
        .when()
            .get("/medical-records")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(0))
            .body("total", equalTo(0));
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        given()
            .queryParam("size", 101)
        .when()
            .get("/medical-records")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn404WhenPrescribingOnMissingRecord() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\": \"Antibiotics\"}")
        .when()
            .post("/medical-records/" + UUID.randomUUID() + "/treatments")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldActivateThenCompleteTreatment() {
        String recordId = postRecord("Limping");
        String treatmentId = given()
                .contentType(ContentType.JSON)
                .body("{\"description\": \"Antibiotics\"}")
            .when()
                .post("/medical-records/" + recordId + "/treatments")
            .then()
                .statusCode(201)
                .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ACTIVE\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("ACTIVE"))
            .body("startedOn", notNullValue());

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"COMPLETED\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("endedOn", notNullValue());
    }

    @Test
    void shouldReturn422OnIllegalTransition() {
        String recordId = postRecord("Limping");
        String treatmentId = given()
                .contentType(ContentType.JSON)
                .body("{\"description\": \"Antibiotics\"}")
            .when()
                .post("/medical-records/" + recordId + "/treatments")
            .then()
                .statusCode(201)
                .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"COMPLETED\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(422)
            .body("message", containsString("Cannot transition"));
    }

    @Test
    void shouldReturn404WhenTreatmentDoesNotExist() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ACTIVE\"}")
        .when()
            .put("/treatments/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(404);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd zms-be/health-service && ./mvnw -B verify -Dit.test=MedicalRecordResourceIT
```

Expected: compilation failure — `ZooRoles` and the REST classes do not exist.

- [ ] **Step 3: Write the role constants and DTOs**

`infrastructure/security/ZooRoles.java`:

```java
package it.zoo.health.infrastructure.security;

public final class ZooRoles {

    public static final String ADMIN = "zoo-admin";
    public static final String VET = "zoo-vet";
    public static final String KEEPER = "zoo-keeper";

    private ZooRoles() {}
}
```

`infrastructure/rest/dto/ErrorResponse.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

public record ErrorResponse(String message) {}
```

`infrastructure/rest/dto/CreateMedicalRecordRequest.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMedicalRecordRequest(
        @NotNull UUID animalId,
        @NotBlank @Size(max = 200) String reason,
        @NotBlank @Size(max = 1000) String diagnosis,
        @NotNull LocalDate examinedOn,
        @NotBlank @Size(max = 100) String veterinarian
) {}
```

`infrastructure/rest/dto/PrescribeTreatmentRequest.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescribeTreatmentRequest(
        @NotBlank @Size(max = 500) String description
) {}
```

`infrastructure/rest/dto/UpdateTreatmentStatusRequest.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import it.zoo.health.domain.enums.TreatmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTreatmentStatusRequest(
        @NotNull TreatmentStatus status
) {}
```

`infrastructure/rest/dto/MedicalRecordResponse.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID animalId,
        String reason,
        String diagnosis,
        LocalDate examinedOn,
        String veterinarian,
        String createdBy,
        String updatedBy
) {}
```

`infrastructure/rest/dto/TreatmentResponse.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import it.zoo.health.domain.enums.TreatmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TreatmentResponse(
        UUID id,
        UUID medicalRecordId,
        String description,
        TreatmentStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        String createdBy,
        String updatedBy
) {}
```

`infrastructure/rest/dto/MedicalRecordDetailResponse.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicalRecordDetailResponse(
        UUID id,
        UUID animalId,
        String reason,
        String diagnosis,
        LocalDate examinedOn,
        String veterinarian,
        String createdBy,
        String updatedBy,
        List<TreatmentResponse> treatments
) {}
```

`infrastructure/rest/dto/MedicalRecordPageResponse.java`:

```java
package it.zoo.health.infrastructure.rest.dto;

import java.util.List;

public record MedicalRecordPageResponse(
        List<MedicalRecordResponse> items,
        int page,
        int size,
        long total
) {}
```

- [ ] **Step 4: Write the MapStruct mapper**

`infrastructure/rest/mapper/HealthDtoMapper.java`:

```java
package it.zoo.health.infrastructure.rest.mapper;

import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.infrastructure.rest.dto.MedicalRecordResponse;
import it.zoo.health.infrastructure.rest.dto.TreatmentResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface HealthDtoMapper {
    MedicalRecordResponse toResponse(MedicalRecord record);
    List<MedicalRecordResponse> toResponseList(List<MedicalRecord> records);
    TreatmentResponse toResponse(Treatment treatment);
    List<TreatmentResponse> toTreatmentResponseList(List<Treatment> treatments);
}
```

`MedicalRecordDetailResponse` is assembled by hand in the resource, because it flattens a record and a list from two different aggregates.

- [ ] **Step 5: Write the exception mappers**

Six domain mappers, all the same shape. `infrastructure/rest/MedicalRecordNotFoundExceptionMapper.java`:

```java
package it.zoo.health.infrastructure.rest;

import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MedicalRecordNotFoundExceptionMapper implements ExceptionMapper<MedicalRecordNotFoundException> {

    @Override
    public Response toResponse(MedicalRecordNotFoundException exception) {
        return Response.status(404)
                .entity(new ErrorResponse(exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

`TreatmentNotFoundExceptionMapper.java` — same, with `TreatmentNotFoundException` and status `404`.

`InvalidMedicalDataExceptionMapper.java` — same, with `InvalidMedicalDataException` and status `400`.

`InvalidTreatmentStatusTransitionExceptionMapper.java` — same, with `InvalidTreatmentStatusTransitionException` and status `422`.

`ConcurrentMedicalRecordUpdateExceptionMapper.java` — same, with `ConcurrentMedicalRecordUpdateException` and status `409`.

`ConcurrentTreatmentUpdateExceptionMapper.java` — same, with `ConcurrentTreatmentUpdateException` and status `409`.

`infrastructure/rest/UnexpectedExceptionMapper.java`:

```java
package it.zoo.health.infrastructure.rest;

import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Last resort. Mapping Exception rather than RuntimeException leaves the
 * framework's own WebApplicationException handling in place, so a malformed
 * path parameter or an unparseable body still answers 400/404 instead of 500.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled exception while serving request", exception);
        return Response.status(500)
                .entity(new ErrorResponse("Internal server error"))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

`infrastructure/rest/SecurityExceptionMapper.java`:

```java
package it.zoo.health.infrastructure.rest;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import it.zoo.health.infrastructure.rest.dto.ErrorResponse;
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
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

- [ ] **Step 6: Write OpenApiConfig and the resources**

`infrastructure/rest/OpenApiConfig.java`:

```java
package it.zoo.health.infrastructure.rest;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

@OpenAPIDefinition(
        info = @Info(title = "Health Service API", version = "1.0.0")
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

`infrastructure/rest/MedicalRecordResource.java`:

```java
package it.zoo.health.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.*;
import it.zoo.health.infrastructure.rest.dto.*;
import it.zoo.health.infrastructure.rest.mapper.HealthDtoMapper;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@ApplicationScoped
@Path("/medical-records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class MedicalRecordResource {

    private final CreateMedicalRecordUseCase createMedicalRecord;
    private final GetMedicalRecordUseCase getMedicalRecord;
    private final ListMedicalRecordsUseCase listMedicalRecords;
    private final PrescribeTreatmentUseCase prescribeTreatment;
    private final HealthDtoMapper mapper;
    private final SecurityIdentity identity;

    public MedicalRecordResource(CreateMedicalRecordUseCase createMedicalRecord,
                                 GetMedicalRecordUseCase getMedicalRecord,
                                 ListMedicalRecordsUseCase listMedicalRecords,
                                 PrescribeTreatmentUseCase prescribeTreatment,
                                 HealthDtoMapper mapper,
                                 SecurityIdentity identity) {
        this.createMedicalRecord = createMedicalRecord;
        this.getMedicalRecord = getMedicalRecord;
        this.listMedicalRecords = listMedicalRecords;
        this.prescribeTreatment = prescribeTreatment;
        this.mapper = mapper;
        this.identity = identity;
    }

    @POST
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public Response create(@Valid CreateMedicalRecordRequest request) {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                request.animalId(), request.reason(), request.diagnosis(),
                request.examinedOn(), request.veterinarian(), currentActor()
        );
        MedicalRecord record = createMedicalRecord.create(cmd);
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(record))
                .build();
    }

    @GET
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public MedicalRecordPageResponse list(@QueryParam("animalId") UUID animalId,
                                          @QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size) {
        MedicalRecordPage result = listMedicalRecords.list(animalId, page, size);
        return new MedicalRecordPageResponse(
                mapper.toResponseList(result.items()),
                result.page(),
                result.size(),
                result.total()
        );
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ZooRoles.ADMIN, ZooRoles.VET, ZooRoles.KEEPER})
    public MedicalRecordDetailResponse getById(@PathParam("id") UUID id) {
        MedicalRecordDetail detail = getMedicalRecord.getById(id);
        MedicalRecord record = detail.record();
        return new MedicalRecordDetailResponse(
                record.getId(),
                record.getAnimalId(),
                record.getReason(),
                record.getDiagnosis(),
                record.getExaminedOn(),
                record.getVeterinarian(),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                mapper.toTreatmentResponseList(detail.treatments())
        );
    }

    @POST
    @Path("/{id}/treatments")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public Response prescribe(@PathParam("id") UUID id,
                              @Valid PrescribeTreatmentRequest request) {
        Treatment treatment = prescribeTreatment.prescribe(
                new PrescribeTreatmentCommand(id, request.description(), currentActor()));
        return Response.status(Response.Status.CREATED)
                .entity(mapper.toResponse(treatment))
                .build();
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
```

`infrastructure/rest/TreatmentResource.java`:

```java
package it.zoo.health.infrastructure.rest;

import io.quarkus.security.identity.SecurityIdentity;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.infrastructure.rest.dto.TreatmentResponse;
import it.zoo.health.infrastructure.rest.dto.UpdateTreatmentStatusRequest;
import it.zoo.health.infrastructure.rest.mapper.HealthDtoMapper;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@ApplicationScoped
@Path("/treatments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class TreatmentResource {

    private final UpdateTreatmentStatusUseCase updateTreatmentStatus;
    private final HealthDtoMapper mapper;
    private final SecurityIdentity identity;

    public TreatmentResource(UpdateTreatmentStatusUseCase updateTreatmentStatus,
                             HealthDtoMapper mapper,
                             SecurityIdentity identity) {
        this.updateTreatmentStatus = updateTreatmentStatus;
        this.mapper = mapper;
        this.identity = identity;
    }

    @PUT
    @Path("/{id}/status")
    @RolesAllowed({ZooRoles.VET, ZooRoles.ADMIN})
    public TreatmentResponse updateStatus(@PathParam("id") UUID id,
                                          @Valid UpdateTreatmentStatusRequest request) {
        return mapper.toResponse(
                updateTreatmentStatus.updateStatus(id, request.status(), currentActor()));
    }

    private String currentActor() {
        return identity.getPrincipal().getName();
    }
}
```

- [ ] **Step 7: Run the integration test to verify it passes**

Run:

```bash
cd zms-be/health-service && ./mvnw -B verify -Dit.test=MedicalRecordResourceIT
```

Expected: 12 integration tests PASS. Docker must be running — Dev Services starts PostgreSQL through Testcontainers.

- [ ] **Step 8: Commit**

```bash
git add zms-be/health-service/src
git commit -m "feat(health-service): add the REST surface

One @Provider per exception type, matching the shape animal-service settled
on. UnexpectedExceptionMapper maps Exception rather than RuntimeException so a
malformed UUID still answers 400 instead of 500."
```

---

### Task 10: Authorization matrix

**Files:**
- Test: `src/test/java/it/zoo/health/infrastructure/rest/HealthSecurityIT.java`

**Interfaces:**
- Consumes: the full REST surface from Task 9.
- Produces: proof that keeper is read-only and that anonymous access is rejected.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/zoo/health/infrastructure/rest/HealthSecurityIT.java`:

```java
package it.zoo.health.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.infrastructure.persistence.MedicalRecordEntity;
import it.zoo.health.infrastructure.persistence.TreatmentEntity;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthSecurityIT {

    private static final UUID RECORD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TREATMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private static final String RECORD_JSON = "{"
            + "  \"animalId\": \"550e8400-e29b-41d4-a716-446655440000\","
            + "  \"reason\": \"Limping\","
            + "  \"diagnosis\": \"Sprained paw\","
            + "  \"examinedOn\": \"2026-09-01\","
            + "  \"veterinarian\": \"Dr Rossi\""
            + "}";

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();

            MedicalRecordEntity record = new MedicalRecordEntity();
            record.setId(RECORD_ID);
            record.setAnimalId(ANIMAL_ID);
            record.setReason("Limping");
            record.setDiagnosis("Sprained paw");
            record.setExaminedOn(LocalDate.of(2026, 9, 1));
            record.setVeterinarian("Dr Rossi");
            record.setCreatedBy("system");
            em.persist(record);

            TreatmentEntity treatment = new TreatmentEntity();
            treatment.setId(TREATMENT_ID);
            treatment.setMedicalRecordId(RECORD_ID);
            treatment.setDescription("Antibiotics");
            treatment.setStatus(TreatmentStatus.PRESCRIBED);
            treatment.setCreatedBy("system");
            em.persist(treatment);
        });
    }

    @Test
    void shouldRejectAnonymousRead() {
        given().when().get("/medical-records").then().statusCode(401);
    }

    @Test
    void shouldRejectAnonymousWrite() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToList() {
        given().when().get("/medical-records").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToReadOneRecord() {
        given().when().get("/medical-records/" + RECORD_ID).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromCreatingRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records")
            .then().statusCode(403).body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromPrescribing() {
        given().contentType(ContentType.JSON).body("{\"description\": \"Antibiotics\"}")
            .when().post("/medical-records/" + RECORD_ID + "/treatments")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromChangingTreatmentStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"ACTIVE\"}")
            .when().put("/treatments/" + TREATMENT_ID + "/status")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToCreateRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToChangeTreatmentStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"ACTIVE\"}")
            .when().put("/treatments/" + TREATMENT_ID + "/status")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToCreateRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldRecordTheActingPrincipalAsCreator() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records")
            .then().statusCode(201).body("createdBy", equalTo("admin"));
    }
}
```

- [ ] **Step 2: Run the test**

Run:

```bash
cd zms-be/health-service && ./mvnw -B verify -Dit.test=HealthSecurityIT
```

Expected: 11 tests PASS. If any keeper write returns 201 instead of 403, the `@RolesAllowed` on that method in Task 9 is wrong — fix the annotation, not the test.

- [ ] **Step 3: Run the full suite**

Run:

```bash
cd zms-be/health-service && ./mvnw -B --no-transfer-progress verify
```

Expected: 41 unit tests and 23 integration tests PASS.

- [ ] **Step 4: Commit**

```bash
git add zms-be/health-service/src
git commit -m "test(health-service): cover the authorization matrix

Keeper reads clinical history but never writes it, and the acting principal
is asserted to land in created_by so the audit trail is not decorative."
```

---

### Task 11: Local infrastructure and CI

**Files:**
- Modify: `zms-be/infrastructure/docker-compose.yml`
- Modify: `zms-be/infrastructure/env.example`
- Modify: `zms-be/infrastructure/keycloak/realm-export.json`
- Modify: `.github/workflows/backend-ci.yml`
- Modify: `zms-be/CLAUDE.md`

**Interfaces:**
- Consumes: the module from Tasks 1-10.
- Produces: `docker compose up` serving `postgres-health` on 5433 and a Keycloak client `health-service`; CI covering both modules.

- [ ] **Step 1: Add the database to docker-compose**

In `zms-be/infrastructure/docker-compose.yml`, add a service after `postgres-animal`:

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

Add one entry to the `keycloak` service's `environment` block:

```yaml
      HEALTH_SERVICE_CLIENT_SECRET: ${HEALTH_OIDC_CLIENT_SECRET:?copy env.example to .env first}
```

And extend the `volumes` block at the bottom:

```yaml
volumes:
  animal_data:
  health_data:
```

- [ ] **Step 2: Add the new variables to `infrastructure/env.example`**

Append:

```bash
# health-service database — must match DB_USERNAME / DB_PASSWORD in health-service/.env
POSTGRES_HEALTH_DB=health_db
POSTGRES_HEALTH_USER=zoo
POSTGRES_HEALTH_PASSWORD=change-me-locally

# Must match OIDC_CLIENT_SECRET in health-service/.env
HEALTH_OIDC_CLIENT_SECRET=change-me-locally
```

- [ ] **Step 3: Add the Keycloak client**

In `zms-be/infrastructure/keycloak/realm-export.json`, find the `clients` array and add an entry alongside `animal-service`, copying that entry's shape exactly and changing only the three values below:

```json
    {
      "clientId": "health-service",
      "enabled": true,
      "publicClient": false,
      "serviceAccountsEnabled": true,
      "standardFlowEnabled": false,
      "secret": "${HEALTH_SERVICE_CLIENT_SECRET}"
    }
```

Read the existing `animal-service` entry first and mirror any additional fields it carries — the realm import fails on a malformed client and the container then serves an empty realm.

- [ ] **Step 4: Verify the stack starts**

Run:

```bash
cd zms-be/infrastructure && docker compose up -d && docker compose ps
```

Expected: `postgres-animal`, `postgres-health` and `keycloak` all running. Confirm the realm imported:

```bash
curl -s http://localhost:8081/realms/zoo/.well-known/openid-configuration | head -c 200
```

Expected: a JSON document, not a 404.

- [ ] **Step 5: Verify health-service reaches its database**

Copy the env file and start dev mode:

```bash
cd zms-be/health-service && cp env.example .env && ./mvnw quarkus:dev
```

Expected: Flyway applies `V1` and the service listens on 8082. Stop it with Ctrl-C. Confirm the tables exist:

```bash
docker exec -i $(docker ps -qf name=postgres-health) psql -U zoo -d health_db -c "\dt"
```

Expected: `medical_records`, `treatments` and `flyway_schema_history`.

- [ ] **Step 6: Extend CI to both modules**

In `.github/workflows/backend-ci.yml`, replace the `verify` job's hardcoded directory with a matrix:

```yaml
jobs:
  verify:
    # Docker is available on this runner, which the *IT suite needs:
    # Quarkus Dev Services starts Postgres through Testcontainers.
    runs-on: ubuntu-latest

    strategy:
      fail-fast: false
      matrix:
        module: [animal-service, health-service]

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Verify
        working-directory: zms-be/${{ matrix.module }}
        run: ./mvnw -B --no-transfer-progress verify

      - name: Publish test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports-${{ matrix.module }}
          path: |
            zms-be/${{ matrix.module }}/target/surefire-reports/
            zms-be/${{ matrix.module }}/target/failsafe-reports/
          if-no-files-found: ignore
```

The artifact name gains the module suffix because two matrix jobs cannot upload to the same artifact name.

- [ ] **Step 7: Update the project rules**

In `zms-be/CLAUDE.md`, under "Stato attuale e prossimi passi", change the module list so `health-service` reads `[completato]` instead of `[non iniziato]`, and reduce "Prossime fasi" to:

```markdown
### Prossime fasi
- `infrastructure/event/` — Kafka producer per eventi animale
- Servizi restanti: `feeding-service`, `notification-service`
```

Also add the health credentials to the "Credenziali locali" bullet: copying `zms-be/health-service/env.example` to `.env`, with `POSTGRES_HEALTH_PASSWORD` matching that file's `DB_PASSWORD` and `HEALTH_OIDC_CLIENT_SECRET` matching its `OIDC_CLIENT_SECRET`.

- [ ] **Step 8: Confirm both modules still verify**

Run:

```bash
cd zms-be/animal-service && ./mvnw -B --no-transfer-progress verify
cd ../health-service && ./mvnw -B --no-transfer-progress verify
```

Expected: both `BUILD SUCCESS` — 38+25 for animal, 41+23 for health.

- [ ] **Step 9: Commit**

```bash
git add zms-be/infrastructure .github/workflows/backend-ci.yml zms-be/CLAUDE.md
git commit -m "chore(health-service): wire local infrastructure and CI

postgres-health runs as its own container on 5433 so either service can be
stopped without touching the other. CI becomes a matrix because the workflow
would otherwise stay blind to the new module."
```

---

## Verification checklist

Run after Task 11. Every line maps to the spec's Definition of Done.

- [ ] `zms-be/pom.xml` lists both modules; both `./mvnw verify` runs are green.
- [ ] `grep -rE "import (jakarta|io\.quarkus|org\.hibernate|org\.mapstruct)" zms-be/health-service/src/main/java/it/zoo/health/domain/` returns nothing (also asserted by `DomainPurityTest`).
- [ ] All five endpoints answer with the documented status codes and RBAC (`MedicalRecordResourceIT`, `HealthSecurityIT`).
- [ ] `GET /medical-records` returns `{items, page, size, total}`.
- [ ] `docker compose up` starts all three containers; `health-service` reaches `health_db` on 5433 and Keycloak on 8081.
- [ ] `git status` shows no `.env` file staged or tracked; both `env.example` files list every variable their module needs.
