# CLAUDE.md — Zoo Management System

Regole di progetto vincolanti per qualsiasi agente AI che lavora su questo codebase.  
Leggere integralmente prima di toccare qualsiasi file.

---

## Panoramica del progetto

**Zoo Management System** (`zms-be`) — sistema di gestione zoo composto da 4 microservizi Quarkus in un mono-repo Maven.

```
zoo-management-system/
├── zms-be/
│   ├── pom.xml                  ← parent POM (gestisce TUTTE le versioni)
│   ├── animal-service/          ← Core: anagrafica animali [IN SVILUPPO]
│   ├── health-service/          ← Cartelle cliniche [non iniziato]
│   ├── feeding-service/         ← Piani alimentari [non iniziato]
│   ├── notification-service/    ← Notifiche Kafka consumer [non iniziato]
│   └── infrastructure/
│       ├── docker-compose.yml
│       └── keycloak/
└── docs/
```

**Stack**: Java 21, Quarkus 3.20.0, Maven multi-module, PostgreSQL, Kafka, Keycloak.  
**Ambiente**: Windows 11, PowerShell. Java via JBang. Maven in `C:\tools\apache-maven`.

---

## Architettura: Esagonale (Ports & Adapters)

### Struttura package — `animal-service` come riferimento

```
src/main/java/it/zoo/animal/
├── domain/
│   ├── model/          ← POJO puri — ZERO annotazioni framework
│   ├── enums/          ← Enum di dominio — ZERO annotazioni framework
│   ├── port/
│   │   ├── in/         ← Interfacce Use Case + record Command
│   │   └── out/        ← Interfacce Repository / Event Port
│   └── exception/      ← extends RuntimeException — ZERO annotazioni framework
│
├── application/        ← Implementazioni Use Case
│   └── {Nome}Service.java
│
└── infrastructure/
    ├── persistence/    ← Adapter JPA (entity, panache repository)
    ├── rest/           ← Adapter REST (resource, DTO, mapper, exception handler)
    └── event/          ← Adapter Kafka (producer, consumer)
```

### Regola di dipendenza — NON VIOLARE MAI

```
infrastructure → application → domain
```

- `domain` non dipende da nessuno
- `application` dipende solo da `domain`
- `infrastructure` dipende da `application` e `domain`
- **Nessun layer dipende da un layer esterno a sé stesso**

---

## Regole per layer

### domain/ — Regole assolute

- **ZERO annotazioni framework**: niente `@Entity`, `@Inject`, `@ApplicationScoped`, `@NotNull`, niente Jakarta, niente Quarkus
- I model sono classi Java con costruttori, getter, setter e logica di dominio pura
- Gli enum stanno in `domain/enums/`, **mai** annidati dentro i model
- Le eccezioni estendono `RuntimeException` direttamente, senza annotazioni
- I `record` Command stanno in `domain/port/in/`, non in `application/`
- La logica di transizione di stato (es. `canTransitionTo`) appartiene al model, non ai service

### application/ — Regole

- Una operazione = una classe (es. `RegisterAnimalService`, non `AnimalService` con 5 metodi)
- Ogni service implementa **una sola** interfaccia Use Case
- Annotazioni permesse: `@ApplicationScoped`, `@Transactional`
- `@Transactional` **solo sul metodo** che scrive su DB, **mai** sulla classe
- Injection **solo tramite costruttore** — mai `@Inject` su field
- I service conoscono solo le interfacce `port/in` e `port/out` — mai classi JPA, mai JAX-RS, mai Kafka
- La validazione è logica esplicita (if + throw), **non** Bean Validation con annotazioni

### infrastructure/ — Regole

- Le `@Entity` JPA stanno in `infrastructure/persistence/` — **mai** nel domain
- Il Panache Repository pattern è **Repository** (`implements PanacheRepository<E>`), **non** Active Record (`extends PanacheEntity`)
- I DTO di request/response stanno in `infrastructure/rest/` — mai nel domain né nell'application
- Bean Validation (`@NotNull`, `@NotBlank`, `@Valid`) è permessa **solo** sui DTO in `infrastructure/rest/`
- I mapper MapStruct stanno in `infrastructure/rest/mapper/`
- Gli adapter Kafka (producer/consumer) stanno in `infrastructure/event/`

---

## Convenzioni di codice stabilite

### Injection
```java
// CORRETTO — costruttore
public class RegisterAnimalService implements RegisterAnimalUseCase {
    private final AnimalRepository repository;

    public RegisterAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }
}

// SBAGLIATO — field injection
@Inject
AnimalRepository repository;
```

### @Transactional
```java
// CORRETTO — solo sul metodo
@ApplicationScoped
public class RegisterAnimalService implements RegisterAnimalUseCase {
    @Override
    @Transactional
    public Animal register(RegisterAnimalCommand cmd) { ... }
}

// SBAGLIATO — sulla classe
@ApplicationScoped
@Transactional
public class RegisterAnimalService implements RegisterAnimalUseCase { ... }
```

### Validazione nel domain
```java
// CORRETTO — logica esplicita
if (cmd.name() == null || cmd.name().isBlank()) {
    throw new InvalidAnimalDataException("Animal name must not be blank");
}

// SBAGLIATO — annotazioni Bean Validation nel domain o nell'application
@NotBlank
private String name;
```

### Operazioni read-only (nessun @Transactional)
```java
// CORRETTO — nessuna annotazione per query pure
@Override
public Animal getById(UUID id) {
    return repository.findById(id)
            .orElseThrow(() -> new AnimalNotFoundException(id));
}
```

---

## Regole di naming

| Artefatto | Pattern | Esempio |
|---|---|---|
| Use Case interface | `{Verbo}{Entità}UseCase` | `RegisterAnimalUseCase` |
| Command record | `{Verbo}{Entità}Command` | `RegisterAnimalCommand` |
| Application service | `{Verbo}{Entità}Service` | `RegisterAnimalService` |
| Repository port (out) | `{Entità}Repository` | `AnimalRepository` |
| JPA entity | `{Entità}Entity` | `AnimalEntity` |
| Panache repo adapter | `{Entità}PanacheRepository` | `AnimalPanacheRepository` |
| REST resource | `{Entità}Resource` | `AnimalResource` |
| Request DTO | `{Verbo}{Entità}Request` | `RegisterAnimalRequest` |
| Response DTO | `{Entità}Response` | `AnimalResponse` |
| MapStruct mapper | `{Entità}Mapper` | `AnimalMapper` |
| Exception handler | `{Dominio}ExceptionMapper` | `ZooExceptionMapper` |

---

## Regole di testing

### Test del domain layer
- Solo JUnit 5 — **zero Quarkus**, zero Mockito
- Package: `it.zoo.animal.domain`
- Testano la logica pura del model (es. `canTransitionTo`)

### Test dell'application layer
- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`)
- **Zero `@QuarkusTest`** — il contesto CDI non deve partire
- `@Mock` sul port/out (repository), `@InjectMocks` sul service
- Package: `it.zoo.animal.application`

### Test dell'infrastructure layer
- `@QuarkusTest` con Dev Services (Testcontainers)
- Per il REST: RestAssured integrato
- Package: `it.zoo.animal.infrastructure`

### Naming dei test
```
should{ComportamentoAtteso}[When{Condizione}]
```
Esempi: `shouldRegisterAnimalWithHealthyStatus`, `shouldThrowWhenNameIsBlank`

---

## Regole Maven / Build

- **Non modificare le versioni nel POM figlio** — tutto è gestito dal parent BOM (`quarkus-bom`)
- `mvnw` / `mvnw.cmd` per i comandi Maven (wrapper incluso per ciascun servizio)
- Non aggiungere dipendenze senza consultare il parent POM prima
- Comando dev: `cd zms-be/animal-service && mvnw quarkus:dev`
- I test non usano Keycloak Dev Services — `quarkus.devservices.enabled=false` in `application.properties`

---

## Stato attuale e prossimi passi

### Completato in `animal-service`

**Domain layer** (`domain/`)
- `Animal.java` — model con `canTransitionTo()`
- `AnimalStatus.java`, `Habitat.java` — enum in `domain/enums/`
- `RegisterAnimalUseCase`, `GetAnimalUseCase`, `ListAnimalsUseCase`, `UpdateAnimalStatusUseCase`, `TransferAnimalUseCase`
- `RegisterAnimalCommand` record
- `AnimalRepository` (port/out)
- `AnimalNotFoundException`, `InvalidAnimalDataException`, `InvalidStatusTransitionException`

**Application layer** (`application/`)
- `RegisterAnimalService`, `GetAnimalService`, `ListAnimalsService`, `UpdateAnimalStatusService`, `TransferAnimalService`

**Test**
- Test domain: `AnimalStatusTransitionTest`
- Test application: tutti i service coperti con Mockito

### Prossima fase: Infrastructure persistence

Da implementare in `infrastructure/persistence/`:
- `AnimalEntity` — `@Entity` JPA che mappa `Animal`
- `AnimalPanacheRepository` — `implements PanacheRepository<AnimalEntity>` + `implements AnimalRepository` (port/out)
- Migration Flyway: `src/main/resources/db/migration/V1__create_animals_table.sql`

### Fasi successive
- `infrastructure/rest/` — Resource, DTO, mapper MapStruct, exception handler globale
- Security — OIDC, JWT, `@RolesAllowed`
- `infrastructure/event/` — Kafka producer per eventi animale
- Servizi restanti: `health-service`, `feeding-service`, `notification-service`

---

## Cosa non fare — mai

- Non mettere `@Entity`, `@Column`, `@Id` o qualsiasi annotazione JPA nel `domain/`
- Non mettere `@Path`, `@GET`, `@POST` o annotazioni JAX-RS nell'`application/`
- Non usare Active Record pattern (`extends PanacheEntity`) — si usa il Repository pattern
- Non usare `AnimalEntity` direttamente nell'`application/` — solo `Animal` (domain object)
- Non fare logica di business nell'infrastructure layer
- Non aggiungere Kafka, REST client o altri adapter nell'`application/`
- Non modificare il `domain/` per adattarlo all'infrastructure (vale il contrario)
- Non mettere `@Transactional` a livello di classe
- Non usare field injection (`@Inject` su field)
- Non creare un unico `AnimalService` con tutti i metodi CRUD — una classe per Use Case