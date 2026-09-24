# Events

Single source of truth for the asynchronous contracts between ZMS services. Service READMEs link here and do not repeat payloads.

All events are produced by `animal-service`. No other event exists in the code: `AnimalEvent` is a sealed interface that permits exactly `AnimalRegistered`, `AnimalStatusChanged` and `AnimalTransferred` (`AnimalEvent`). `health-service` has no Kafka dependency and neither produces nor consumes events (`health-service/pom.xml`).

## Topics

| Topic | Producer | Consumers | Message key | Value |
|---|---|---|---|---|
| `zoo.animal.events` | `animal-service`, channel `animal-events-out` (`OutboxRelay`) | `notification-service`, channel `animal-events-in`, group `notification-service` (`AnimalEventConsumer`) | animal id, as a UUID string (`OutboxRelay`) | JSON envelope below, as a string (`StringSerializer` / `StringDeserializer`, `application.properties` of both services) |
| `zoo.animal.events.dlq` | `notification-service`, SmallRye `dead-letter-queue` failure strategy (`notification-service/.../application.properties`) | None in this repo | Same as the failed record | Same as the failed record |

Neither topic is created explicitly. Locally the Kafka broker auto-creates them (`zms-be/infrastructure/docker-compose.yml`). The partition count is not configured anywhere in the repo.

`notification-service` starts from the earliest offset when its group has no committed offset (`auto.offset.reset=earliest`).

## Envelope

Every event on `zoo.animal.events` has the same envelope. The producer and the consumer each define their own record for it; there is no shared module (`AnimalEventMessage` in `animal-service`, `AnimalEventMessage` in `notification-service`).

| Field | JSON type | Content |
|---|---|---|
| `eventId` | string (UUID) | Random UUID created by the application service when the change happens. Also the primary key of the outbox row (`OutboxAnimalEventPublisher`) |
| `eventType` | string | `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED` or `ANIMAL_TRANSFERRED` (`AnimalEventMessageMapper`) |
| `occurredAt` | string (ISO-8601 instant, e.g. `2026-09-23T10:15:30Z`) | `Instant.now()` in the application service, before commit |
| `animalId` | string (UUID) | Id of the animal. Same value as the Kafka key |
| `performedBy` | string | Principal name of the authenticated caller (`AnimalResource`, `identity.getPrincipal().getName()`) |
| `payload` | object | Event-specific fields, below |

The consumer ignores unknown envelope fields (`@JsonIgnoreProperties(ignoreUnknown = true)` on the consumer's `AnimalEventMessage`) and reads `payload` as a raw JSON tree (`AnimalEventMessageMapper` in `notification-service`).

The envelope has no schema version field.

### Contract fixtures

The wire shape is pinned by three JSON fixtures, copied in both services under `src/test/resources/contract/`: `animal-registered.json`, `animal-status-changed.json`, `animal-transferred.json`. `animal-service` checks that its serialization equals the fixture; `notification-service` checks that it can map the fixture to a command (`AnimalEventMessageContractTest` in each service). Each service reads its own copy; nothing checks that the two copies are identical.

## Event catalogue

### `ANIMAL_REGISTERED`

| | |
|---|---|
| Domain class | `AnimalRegistered` |
| Payload class (producer) | `AnimalEventMessage.AnimalRegisteredPayload` |
| Emitted by | `RegisterAnimalService`, after the animal is saved, in the same transaction |
| Emitted when | Every successful `POST /animals`. Not emitted when validation fails or the enclosure is unknown (`UnknownEnclosureException` is thrown before the save) |
| Consumed by | `notification-service` (`HandleAnimalEventService`) |

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
| Consumed by | `notification-service` (`HandleAnimalEventService`) |

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
| Consumed by | `notification-service` (`HandleAnimalEventService`) |

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

### Dead-letter queue

- Topic `zoo.animal.events.dlq`, configured on the `animal-events-in` channel (`notification-service/.../application.properties`).
- `AnimalEventConsumer` lets every exception propagate, so any failure dead-letters the record and the consumer moves on to the next one. This includes:
  - malformed JSON (`InvalidAnimalEventException`, "Malformed animal event JSON");
  - null or unknown `eventType` (`InvalidAnimalEventException`);
  - null `eventId`, `animalId` or `occurredAt` (`InvalidAnimalEventException`, from `HandleAnimalEventService`);
  - a `fromEnclosureId` / `toEnclosureId` that is not a valid UUID (`IllegalArgumentException` from `UUID.fromString`);
  - any database error while storing the notification, including the database being unavailable and the unique-constraint race above.
- Content: the original record. The SmallRye DLQ strategy also adds `dead-letter-*` headers (reason, cause, source topic, partition, offset); these come from the library, not from code in this repo.
- Covered by `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards`.

### Not handled

| Scenario | Status |
|---|---|
| Cleanup of published `outbox_event` rows | `Not handled`. No code deletes or archives rows |
| Maximum attempts, backoff or parking of an outbox row that always fails | `Not handled`. `attempts` is written but never read (`OutboxRelay`). A row that always fails blocks every later row, because each run restarts from the oldest unpublished row and stops at the first failure |
| Per-animal ordering with more than one relay instance | `Not handled` (see Ordering) |
| Retry of transient consumer failures (e.g. database down) | `Not handled`. The record goes straight to the DLQ |
| Reading, alerting on or replaying the DLQ | `Not handled`. No consumer of `zoo.animal.events.dlq` exists |
| Envelope schema versioning | `Not handled`. No version field |
| Checking that the two copies of the contract fixtures stay identical | `Not handled` |
| Events consumed by `health-service` | `Not handled`. `health-service` has no messaging code |
