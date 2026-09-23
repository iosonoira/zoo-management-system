# Fase 9 — Eventi Kafka: producer con outbox in `animal-service` + `notification-service` minimo

## Context

La roadmap (`zms-be/CLAUDE.md` → "Prossime fasi") prevede un Kafka producer per gli eventi animale in `infrastructure/event/`. Oggi i cambi di stato e i trasferimenti vengono solo salvati (`UpdateAnimalStatusService`, `TransferAnimalService`) e non esiste nessuna porta per gli eventi: `notification-service` è bloccato perché un consumer non può venire prima del producer (vedi spec health-service del 2026-09-20). Anche `.planning/codebase/CONCERNS.md` ("No domain events for animal lifecycle") propone una porta di pubblicazione con transactional outbox.

Scelte di Matteo (2026-09-22):
- **Scope**: producer più un `notification-service` minimo che consuma gli eventi e li registra, così l'integrazione è provata da un capo all'altro.
- **Dual-write**: transactional outbox. Consegna at-least-once, consumer idempotente.

Branch: `feature/kafka-events`. Esecuzione **sequenziale**, mai due agenti che committano nello stesso working tree. Review per task e review finale. Nessuna riga Co-Authored-By nei commit.

## Contratto evento

- Topic `zoo.animal.events`, **key = animalId**, così l'ordine è garantito per singolo animale. DLQ `zoo.animal.events.dlq` lato consumer.
- Envelope JSON: `eventId` (UUID), `eventType`, `occurredAt` (ISO-8601), `animalId`, `performedBy`, `payload`.
- Tre tipi di evento:
  - `ANIMAL_REGISTERED`: payload `name`, `species`, `dangerous`, `habitat`, `enclosureId`
  - `ANIMAL_STATUS_CHANGED`: payload `previousStatus`, `newStatus`, più `name`, `species`
  - `ANIMAL_TRANSFERRED`: payload `fromEnclosureId`, `toEnclosureId`, più `name`, `species`, `dangerous`
- Nessun modulo condiviso: ogni servizio possiede il proprio DTO. Lo stesso fixture JSON per tipo sta in `src/test/resources/contract/` di entrambi i moduli e un test su ciascun lato lo serializza o deserializza. È un contract test leggero ed evita l'accoppiamento a compile time.

## Parte A — `animal-service` (producer)

**Domain** (Java puro, nessun framework):
- `domain/event/AnimalEvent.java`: sealed interface più tre record (`AnimalRegistered`, `AnimalStatusChanged`, `AnimalTransferred`) con `eventId`, `occurredAt`, `animalId`, `performedBy` e i campi del payload.
- `domain/port/out/AnimalEventPublisher.java`: `void publish(AnimalEvent event)`.
- Aggiungere a `animal-service` un `DomainPurityTest` copiato da `health-service/src/test/java/it/zoo/health/domain/DomainPurityTest.java`.

**Application**: `RegisterAnimalService`, `UpdateAnimalStatusService` e `TransferAnimalService` ricevono `AnimalEventPublisher` nel costruttore e pubblicano **dopo** `repository.save(...)`, dentro il metodo `@Transactional` già esistente. Lo stato e l'enclosure precedenti vanno letti prima dei setter. Aggiornare i tre `*ServiceTest` (Mockito): l'evento esce con i campi giusti e non esce mai sui percorsi di errore (validazione, not found, transizione non valida, deceased).

**Infrastructure `event/`**:
- `V4__create_outbox_table.sql`: tabella `outbox_event` con `id uuid pk`, `aggregate_id uuid`, `event_type varchar(64)`, `payload jsonb`, `occurred_at timestamptz`, `published_at timestamptz null`, `attempts int default 0`, più un indice parziale `WHERE published_at IS NULL`.
- `OutboxEventEntity` + `OutboxEventRepository` (Panache).
- `OutboxAnimalEventPublisher implements AnimalEventPublisher`: serializza con Jackson (`AnimalEventMessage` DTO + mapper) e fa persist nella stessa transazione JTA del service. Se il salvataggio va in rollback, la riga non esiste.
- `OutboxRelay`: `@Scheduled(every = "${zoo.outbox.relay.interval:2s}")` → `publishPending()` `@Transactional`. Seleziona fino a 100 righe non pubblicate `ORDER BY occurred_at FOR UPDATE SKIP LOCKED` (native query) e le invia con `MutinyEmitter<String>` sul canale `animal-events-out` usando `OutgoingKafkaRecordMetadata` con key = aggregateId (`sendMessageAndAwait`), poi imposta `published_at`. Al primo errore incrementa `attempts`, logga e interrompe il batch per non rompere l'ordine.

**Dipendenze** (`animal-service/pom.xml`): `quarkus-messaging-kafka`, `quarkus-scheduler`; in test `quarkus-test-kafka-companion`.

**Config** (`application.properties`):
- `mp.messaging.outgoing.animal-events-out.connector=smallrye-kafka`, `topic=zoo.animal.events`, serializer `StringSerializer`
- `%dev.kafka.bootstrap.servers=localhost:9092`: `%dev.quarkus.devservices.enabled=false` è già attivo, quindi in dev il broker viene dal compose
- `%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}`
- `%test`: Kafka Dev Services, che parte automaticamente via Testcontainers; `quarkus.scheduler.enabled=false`, così i test chiamano `publishPending()` direttamente e restano deterministici

**IT** (`infrastructure/event/AnimalEventOutboxIT.java`, `@QuarkusTest` + `@QuarkusTestResource(KafkaCompanionResource.class)`):
1. POST animale → una riga outbox `ANIMAL_REGISTERED` non pubblicata.
2. Transizione non valida (409/422) → nessuna nuova riga outbox (rollback).
3. `publishPending()` → `KafkaCompanion` consuma un record su `zoo.animal.events` con key = animalId e envelope uguale al fixture; la riga ha `published_at` valorizzato.
4. Due `publishPending()` di fila → il secondo non invia nulla.
- `AnimalResourceIT` e `AnimalSecurityIT` puliscono anche `OutboxEventEntity` nel `@BeforeEach`.

## Parte B — `notification-service` (consumer minimo)

Nuovo modulo Maven con la stessa struttura di `health-service`: `pom.xml` e `mvnw` copiati e adattati, `mvnw` con permesso 100755 in git. Porta HTTP `%dev` 8083. **Nessuna REST API e nessun OIDC in questa fase.**

- **Domain**: `Notification` (id, eventId, animalId, severity `INFO/WARNING/CRITICAL`, message, createdAt), `NotificationRule` per la logica pura: registered → INFO; status → SICK = WARNING; DECEASED = CRITICAL; altri = INFO; transferred → WARNING se `dangerous`, altrimenti INFO. Poi `port/in/HandleAnimalEventUseCase` + un record command, `port/out/NotificationRepository` (`save`, `existsByEventId`) e `DomainPurityTest`.
- **Application**: `HandleAnimalEventService` con `@Transactional` sul metodo. Se `existsByEventId` è vero ritorna subito (idempotenza at-least-once), altrimenti applica la regola, salva e logga.
- **Infrastructure**:
  - `persistence/`: `NotificationEntity` con vincolo `UNIQUE(event_id)` come seconda difesa, `V1__create_notifications_table.sql`
  - `event/AnimalEventConsumer`: `@Incoming("animal-events-in")`, deserializza in `AnimalEventMessage` (DTO proprio), mappa nel command e chiama lo use case
  - Config: `group.id=notification-service`, `failure-strategy=dead-letter-queue`, `dead-letter-queue.topic=zoo.animal.events.dlq`
- **Test**: unit test per `NotificationRule` (tutti i rami) e per `HandleAnimalEventService` (evento duplicato → nessun save); contract test sui fixture; IT con `KafkaCompanion` che produce i tre fixture → notifiche persistite con la severity giusta, lo stesso evento inviato due volte → una sola riga, JSON malformato → finisce in DLQ.

## Parte C — Infrastruttura, CI, documentazione

- `zms-be/pom.xml`: aggiungere `<module>notification-service</module>`.
- `infrastructure/docker-compose.yml`: `kafka` con `apache/kafka:3.9.0` in KRaft single-node, listener `PLAINTEXT://localhost:9092`, e `auto.create.topics` attivo solo per il dev locale. Aggiungere `postgres-notification` su 5434 con il volume `notification_data`. `env.example` riceve le variabili `POSTGRES_NOTIFICATION_*`.
- `.github/workflows/backend-ci.yml`: matrice `[animal-service, health-service, notification-service]`.
- `zms-be/CLAUDE.md`: stato dei servizi e "Prossime fasi" aggiornati. Documentare la regola: *gli eventi si pubblicano solo tramite `AnimalEventPublisher`, mai direttamente su Kafka dall'application*.
- A fine lavoro, nel wiki: ADR `0010-transactional-outbox-animal-events`, nota di sessione, `hot.md`, memoria `project_state`.

**Fuori scope** (da annotare): pulizia delle righe outbox già pubblicate, REST e UI delle notifiche, `health-service` come consumer (es. DECEASED → cancellare i trattamenti), schema registry e Avro, `feeding-service`.

## Ordine dei task (un commit per task, TDD)

1. Domain: eventi + porta + `DomainPurityTest` (animal)
2. Application: pubblicazione nei tre service + unit test
3. Outbox: migration V4, entity, `OutboxAnimalEventPublisher`, pulizia negli IT esistenti
4. Relay + dipendenze Kafka + config + `AnimalEventOutboxIT` + fixture di contratto
5. Compose (Kafka + postgres-notification) + env.example
6. Scaffold `notification-service` (pom, mvnw, parent module, CI matrix)
7. Domain + application di notification + unit test
8. Persistence + consumer + DLQ + IT
9. Docs: `zms-be/CLAUDE.md`

## Verifica

- Da `zms-be/animal-service` e poi da `zms-be/notification-service`: `./mvnw verify`, con Docker attivo per Dev Services Postgres e Kafka. Prima del merge vanno riportati i conteggi reali di unit test e IT, non quelli previsti.
- `./mvnw verify` di `health-service` di nuovo verde, perché il parent POM cambia.
- E2E manuale (dopo che Matteo ha completato `.env`):
  1. `docker compose up -d` in `infrastructure`
  2. `animal-service` e `notification-service` avviati in `quarkus:dev`
  3. Un cambio di stato a SICK via REST, con un token Keycloak o con il FE in modalità live
  4. Controllare il record con `kafka-console-consumer.sh` nel container
  5. Controllare la riga WARNING in `notification_db` e il log del consumer
  6. Fermare Kafka, fare un altro cambio di stato, riavviare Kafka: l'evento deve arrivare, perché è rimasto nell'outbox
