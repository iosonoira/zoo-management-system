# Events

Single source of truth for the asynchronous contracts between ZMS services. Service READMEs link here and do not repeat payloads.

All events are produced by `animal-service`. No other event exists in the code: `AnimalEvent` is a sealed interface that permits exactly `AnimalRegistered`, `AnimalStatusChanged` and `AnimalTransferred` (`AnimalEvent`). `health-service` has no Kafka dependency and neither produces nor consumes events (`health-service/pom.xml`).

`zoo.animal.events` has two independent consumers, `notification-service` and `feeding-service`. They read the same topic with their own consumer group and their own dead-letter topic; neither knows about the other.

## Topics

| Topic | Producer | Consumers | Message key | Value |
|---|---|---|---|---|
| `zoo.animal.events` | `animal-service`, channel `animal-events-out` (`OutboxRelay`) | `notification-service`, channel `animal-events-in`, group `notification-service` (`AnimalEventConsumer`); `feeding-service`, channel `animal-events-in`, group `feeding-service` (`AnimalEventConsumer`) | animal id, as a UUID string (`OutboxRelay`) | JSON envelope below, as a string (`StringSerializer` / `StringDeserializer`, `application.properties` of all three services) |
| `zoo.animal.events.dlq` | `notification-service`, SmallRye `dead-letter-queue` failure strategy (`notification-service/.../application.properties`) | None in this repo | Same as the failed record | Same as the failed record |
| `zoo.animal.events.feeding.dlq` | `feeding-service`, SmallRye `dead-letter-queue` failure strategy (`feeding-service/.../application.properties`) | None in this repo | Same as the failed record | Same as the failed record |

None of the three topics is created explicitly. Locally the Kafka broker auto-creates them (`zms-be/infrastructure/docker-compose.yml`). The partition count is not configured anywhere in the repo.

Both consumers start from the earliest offset when their group has no committed offset (`auto.offset.reset=earliest`, in each service's `application.properties`).

## Envelope

Every event on `zoo.animal.events` has the same envelope. The producer and each consumer define their own record for it; there is no shared module (`AnimalEventMessage` in `animal-service`, `AnimalEventMessage` in `notification-service`, `AnimalEventMessage` in `feeding-service`).

| Field | JSON type | Content |
|---|---|---|
| `eventId` | string (UUID) | Random UUID created by the application service when the change happens. Also the primary key of the outbox row (`OutboxAnimalEventPublisher`) |
| `eventType` | string | `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED` or `ANIMAL_TRANSFERRED` (`AnimalEventMessageMapper`) |
| `occurredAt` | string (ISO-8601 instant, e.g. `2026-09-23T10:15:30Z`) | `Instant.now()` in the application service, before commit |
| `animalId` | string (UUID) | Id of the animal. Same value as the Kafka key |
| `performedBy` | string | Principal name of the authenticated caller (`AnimalResource`, `identity.getPrincipal().getName()`) |
| `payload` | object | Event-specific fields, below |

Each consumer ignores unknown envelope fields (`@JsonIgnoreProperties(ignoreUnknown = true)` on its own `AnimalEventMessage`) and reads `payload` as a raw JSON tree, picking only the fields it needs (`AnimalEventMessageMapper` in `notification-service`; `AnimalEventMessageMapper` in `feeding-service` reads only `payload.newStatus`, and only for `ANIMAL_STATUS_CHANGED`).

The envelope has no schema version field.

### Contract fixtures

The wire shape is pinned by three JSON fixtures, copied in all three services under `src/test/resources/contract/`: `animal-registered.json`, `animal-status-changed.json`, `animal-transferred.json`. `animal-service` checks that its serialization equals the fixture; `notification-service` and `feeding-service` each check that they can map the fixture to their own command (`AnimalEventMessageContractTest` in each service). Each service reads its own copy; nothing checks that the three copies stay identical.

## Event catalogue

### `ANIMAL_REGISTERED`

| | |
|---|---|
| Domain class | `AnimalRegistered` |
| Payload class (producer) | `AnimalEventMessage.AnimalRegisteredPayload` |
| Emitted by | `RegisterAnimalService`, after the animal is saved, in the same transaction |
| Emitted when | Every successful `POST /animals`. Not emitted when validation fails or the enclosure is unknown (`UnknownEnclosureException` is thrown before the save) |
| Consumed by | `notification-service` (`HandleAnimalEventService`). Also delivered to `feeding-service` (same topic, consumer group `feeding-service`), whose `HandleAnimalEventService` reads it and returns immediately: it only acts on `ANIMAL_STATUS_CHANGED` |

| Payload field | JSON type | Content |
|---|---|---|
| `name` | string | Animal name |
| `species` | string | Species |
| `dangerous` | boolean | Dangerous flag |
| `habitat` | string | `TERRESTRIAL`, `AQUATIC` or `AMPHIBIOUS` (`Habitat`) |
| `enclosureId` | string (UUID) | Enclosure the animal was registered into |

Notification: severity `INFO`, message `"<name> (<species>) was registered"` (`NotificationRule`).

### `ANIMAL_STATUS_CHANGED`

| | |
|---|---|
| Domain class | `AnimalStatusChanged` |
| Payload class (producer) | `AnimalEventMessage.AnimalStatusChangedPayload` |
| Emitted by | `UpdateAnimalStatusService`, after the animal is saved, in the same transaction |
| Emitted when | `Animal.canTransitionTo` returns true: the current status is not `DECEASED` and the new status differs from the current one. A rejected transition throws `InvalidStatusTransitionException` and writes no outbox row (`AnimalEventOutboxIT.shouldNotWriteOutboxRowWhenStatusTransitionIsRejected`) |
| Consumed by | `notification-service` (`HandleAnimalEventService`), for every status change. `feeding-service` (`HandleAnimalEventService`) only acts when `payload.newStatus` is `DECEASED`: it records the animal in its `deceased_animals` table and moves every `ACTIVE` or `SUSPENDED` feeding plan for that animal to `ENDED`. Any other `newStatus` (`HEALTHY`, `UNDER_OBSERVATION`, `IN_TREATMENT`) is read by `feeding-service` and ignored |

| Payload field | JSON type | Content |
|---|---|---|
| `previousStatus` | string | `HEALTHY`, `UNDER_OBSERVATION`, `IN_TREATMENT` or `DECEASED` (`AnimalStatus`) |
| `newStatus` | string | Same set of values |
| `name` | string | Animal name |
| `species` | string | Species |

The payload has no `dangerous` field.

Notification (`NotificationRule`): severity `CRITICAL` if `newStatus` is `DECEASED`, `WARNING` if it is `UNDER_OBSERVATION` or `IN_TREATMENT`, otherwise `INFO`. Message `"<name> (<species>) status changed from <previousStatus> to <newStatus>"`.

### `ANIMAL_TRANSFERRED`

| | |
|---|---|
| Domain class | `AnimalTransferred` |
| Payload class (producer) | `AnimalEventMessage.AnimalTransferredPayload` |
| Emitted by | `TransferAnimalService`, after the animal is saved, in the same transaction |
| Emitted when | The animal is not `DECEASED` (`Animal.canBeTransferred`) and the target enclosure exists (`EnclosureRepository.existsById`). A transfer to the enclosure the animal is already in is not rejected and emits an event with `fromEnclosureId` equal to `toEnclosureId` |
| Consumed by | `notification-service` (`HandleAnimalEventService`). Also delivered to `feeding-service`, which reads it and returns immediately, the same as for `ANIMAL_REGISTERED` |

| Payload field | JSON type | Content |
|---|---|---|
| `fromEnclosureId` | string (UUID) | Enclosure before the transfer |
| `toEnclosureId` | string (UUID) | Enclosure after the transfer |
| `name` | string | Animal name |
| `species` | string | Species |
| `dangerous` | boolean | Dangerous flag |

Notification (`NotificationRule`): severity `WARNING` if `dangerous` is true, otherwise `INFO`. Message `"<name> (<species>) was transferred from enclosure <from> to enclosure <to>"`. The message contains enclosure ids, not names.

## Delivery semantics

### Outbox write

- Application services publish through the `AnimalEventPublisher` port. Its only implementation, `OutboxAnimalEventPublisher`, is `@Transactional(MANDATORY)`: it must run inside the caller's transaction and fails if there is none.
- It serializes the envelope once and inserts a row into `outbox_event` (`V4__create_outbox_table.sql`):

  | Column | Type | Content |
  |---|---|---|
  | `id` | uuid, PK | `eventId` |
  | `aggregate_id` | uuid | `animalId`, used as the Kafka key |
  | `event_type` | varchar(64) | `eventType` |
  | `payload` | jsonb | Full serialized envelope |
  | `occurred_at` | timestamptz | `occurredAt` |
  | `published_at` | timestamptz, nullable | Null until the relay sends the row |
  | `attempts` | int | Failed send attempts |

  A partial index `idx_outbox_event_unpublished` on `occurred_at WHERE published_at IS NULL` serves the relay query.
- The animal change and the outbox row commit or roll back together. If serialization fails, `IllegalStateException` propagates and the whole operation rolls back (`OutboxAnimalEventPublisher`).

### Relay (`OutboxRelay`)

- Scheduled every `zoo.outbox.relay.interval` (default `2s`). A run is skipped while the previous one is still running (`ConcurrentExecution.SKIP`) and while the application is not yet running (`ApplicationNotRunning`). The scheduler is disabled in the test profile (`%test.quarkus.scheduler.enabled=false`); tests call `publishPending()` directly.
- Each run is one transaction. It selects up to 100 rows with `published_at IS NULL`, ordered by `occurred_at`, with `FOR UPDATE SKIP LOCKED`.
- Rows are sent one at a time and synchronously (`sendMessageAndAwait`). The value is the `payload` column as stored; the key is `aggregate_id`.
- On success, `published_at` is set to the current time.
- On the first failure, `attempts` is incremented, a warning is logged, and the batch stops. The transaction still commits, so rows already sent in that run stay marked as published. The failed row and everything after it are retried on the next run.
- Producer timeouts are shortened so a send fails before the 60 s JTA timeout: `request.timeout.ms=10000`, `delivery.timeout.ms=15000`, `max.block.ms=10000` (`animal-service/.../application.properties`).
- Broker unavailable: REST writes still commit, rows accumulate in `outbox_event`, and every run fails on the oldest row and retries it at the next interval.

### Ordering

- Within one relay, rows are sent in `occurred_at` order, and a failure stops the batch, so a later row is never sent before an earlier unpublished one (`OutboxRelay`).
- All events for one animal share the Kafka key, so they land in the same partition and the consumer group reads them in order.
- Ordering across animals is not guaranteed and not needed by any consumer in the code.
- Multiple `animal-service` instances: `SKIP LOCKED` lets a second relay skip rows locked by the first and send later rows of the same animal. Per-animal ordering across instances: `Not handled`.
- On the consumer side, a record that fails goes to the DLQ and consumption continues, so the next event for the same animal is processed even though an earlier one was dead-lettered (`AnimalEventConsumer`, `failure-strategy=dead-letter-queue`).

### Duplicates

Delivery is at-least-once. The relay can send the same row more than once when:

- the send succeeded but the relay transaction did not commit (crash, DB error), so `published_at` stays null;
- the send timed out on the client while the broker had already written the record.

Every resend carries the same `eventId`, because the envelope is serialized once when the row is written and the relay sends the stored `payload` unchanged (`OutboxAnimalEventPublisher`, `OutboxRelay`).

### Consumer idempotency (`notification-service`)

- `HandleAnimalEventService.handle` runs in one transaction. It validates `eventId`, `eventType`, `animalId` and `occurredAt` are not null, then checks `NotificationRepository.existsByEventId`. If a notification with that `eventId` exists, it returns without writing or logging. Covered by `AnimalEventConsumerIT.shouldPersistExactlyOneRowWhenTheSameEventIsDeliveredTwice`.
- The `notifications` table has a unique constraint on `event_id` (`uq_notifications_event_id`, `V1__create_notifications_table.sql`). If two copies of the same event pass the existence check at the same time, the second insert fails on the constraint, the exception propagates, and that record goes to the DLQ instead of being skipped.
- Payload fields are not validated. A missing `name`, `species`, status or enclosure id is stored as the text `null` in the notification message (`AnimalEventMessageMapper`, `NotificationRule`).

### Consumer idempotency (`feeding-service`)

- `HandleAnimalEventService.handle` runs in one transaction. It validates `eventId`, `eventType`, `animalId` and `occurredAt` are not null, and (for `ANIMAL_STATUS_CHANGED`) that `newStatus` is not null; it then returns without doing anything for any `eventType` other than `ANIMAL_STATUS_CHANGED`, and for any `newStatus` other than `DECEASED`.
- Unlike `notification-service`, which dedupes per event with a `notifications` row keyed by `eventId`, `feeding-service` dedupes per **animal**: once the event is confirmed to be a `DECEASED` status change, it acquires the per-animal `AnimalLock` (`PostgresAnimalLock`, a `pg_advisory_xact_lock` held for the rest of the transaction), then checks `DeceasedAnimalRepository.existsByAnimalId(animalId)` and, if a row already exists for that animal, returns without writing anything or logging. The row it writes on the first `DECEASED` event, `deceased_animals`, is a read model keyed by `animal_id` (primary key), not by `eventId` — the `eventId` and `occurredAt` of that first event are stored in the row but are not part of the key. Covered by `AnimalEventConsumerIT.shouldPersistExactlyOneDeceasedRowWhenEventDeliveredTwiceAndNothingLandsInDlq`.
- This works because `animal-service` makes `DECEASED` terminal (`Animal.canTransitionTo`, see [docs/STATE.md](STATE.md)): there is at most one `ANIMAL_STATUS_CHANGED`/`DECEASED` event per animal, ever, so "have I already recorded this animal as deceased" and "have I already processed this event" coincide. The mechanism would not dedupe correctly if an animal could become `DECEASED` more than once.
- The `deceased_animals` table has a primary key on `animal_id`, but two copies of the same `DECEASED` event no longer race on it: the `AnimalLock` acquired before `existsByAnimalId` serializes them, so the second copy waits for the first to commit and then finds the animal already recorded and skips, instead of racing the insert and dead-lettering (`FeedingConcurrencyIT`). The same lock also serializes a `DECEASED` event against a concurrent `POST /feeding-plans` for the same animal, closing the window where a plan could be created for an animal whose death is mid-flight.
- When a `DECEASED` event is processed for the first time, every `ACTIVE` or `SUSPENDED` feeding plan for that animal is moved to `ENDED`, read with a pessimistic row lock (`FeedingPlanRepository.findByAnimalIdAndStatusInForUpdate`) in the same transaction as the `deceased_animals` insert, so the two never disagree. The row lock also serializes this step against a concurrent `PUT /feeding-plans/{id}/status` on the same plan (`FeedingConcurrencyIT`), instead of one of the two losing to a stale `@Version` and rolling back the whole event, including the `deceased_animals` insert.

### Dead-letter queue

- `notification-service`: topic `zoo.animal.events.dlq`, configured on its own `animal-events-in` channel (`notification-service/.../application.properties`). `AnimalEventConsumer` lets every exception propagate, so any failure dead-letters the record and the consumer moves on to the next one. This includes:
  - malformed JSON (`InvalidAnimalEventException`, "Malformed animal event JSON");
  - null or unknown `eventType` (`InvalidAnimalEventException`);
  - null `eventId`, `animalId` or `occurredAt` (`InvalidAnimalEventException`, from `HandleAnimalEventService`);
  - a `fromEnclosureId` / `toEnclosureId` that is not a valid UUID (`IllegalArgumentException` from `UUID.fromString`);
  - any database error while storing the notification, including the database being unavailable and the unique-constraint race above.
- `feeding-service`: topic `zoo.animal.events.feeding.dlq`, its own topic, configured on its own `animal-events-in` channel (`feeding-service/.../application.properties`). Its `AnimalEventConsumer` also lets every exception propagate. This includes:
  - malformed JSON (`InvalidAnimalEventException`, "Malformed animal event JSON");
  - null `eventId`, `eventType`, `animalId` or `occurredAt` (`InvalidAnimalEventException`, from `HandleAnimalEventService`);
  - a null `newStatus` on an `ANIMAL_STATUS_CHANGED` event (`InvalidAnimalEventException`);
  - any database error while writing `deceased_animals` or ending feeding plans, including the database being unavailable. The `deceased_animals` primary-key race described above no longer reaches this path: the `AnimalLock` serializes concurrent `DECEASED` copies before either inserts.
- Content, for both: the original record. The SmallRye DLQ strategy also adds `dead-letter-*` headers (reason, cause, source topic, partition, offset); these come from the library, not from code in this repo.
- Covered by `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards` in each service.

### Not handled

| Scenario | Status |
|---|---|
| Cleanup of published `outbox_event` rows | `Not handled`. No code deletes or archives rows |
| Maximum attempts, backoff or parking of an outbox row that always fails | `Not handled`. `attempts` is written but never read (`OutboxRelay`). A row that always fails blocks every later row, because each run restarts from the oldest unpublished row and stops at the first failure |
| Per-animal ordering with more than one relay instance | `Not handled` (see Ordering) |
| Retry of transient consumer failures (e.g. database down) | `Not handled`, in both consumers. The record goes straight to the respective DLQ |
| Reading, alerting on or replaying either DLQ | `Not handled`. No consumer of `zoo.animal.events.dlq` or `zoo.animal.events.feeding.dlq` exists |
| Envelope schema versioning | `Not handled`. No version field |
| Checking that the three copies of the contract fixtures stay identical | `Not handled` |
| Events consumed by `health-service` | `Not handled`. `health-service` has no messaging code |
| `feeding-service` deduping correctly if an animal could become `DECEASED` more than once | `Not handled`. The animal-keyed idempotency check relies entirely on `animal-service`'s `DECEASED` being terminal; nothing in `feeding-service` itself would stop a second legitimate `DECEASED` event for the same animal from being silently skipped |
