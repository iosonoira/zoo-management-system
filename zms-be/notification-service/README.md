# notification-service

Turns animal events into stored notifications. Dev port 8083, database `notification_db` on :5434. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `notifications` table: event id (unique), animal id, event type, severity, message, event time, creation time (`V1__create_notifications_table.sql`, `NotificationEntity`).
- The rules that turn an event into a severity and a message (`NotificationRule`).

Does not:
- Expose a REST API. There is no REST extension in `pom.xml` and no resource class, so stored notifications can only be read from the database.
- Deliver notifications to people. There is no email, push or websocket code, and no notion of recipient; the only output besides the table is an `INFO` log line per notification (`HandleAnimalEventService`).
- Track read or acknowledged state. The table has no such column.
- Call `animal-service` or validate animal ids.

## 2. Domain rules

| Rule | Enforced in |
|---|---|
| One notification per `eventId`: a repeated event is skipped | `HandleAnimalEventService` (existence check), unique constraint `uq_notifications_event_id` |
| `eventId`, `eventType`, `animalId` and `occurredAt` are required | `HandleAnimalEventService` (`InvalidAnimalEventException`) |
| `eventType` must be one of `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED`, `ANIMAL_TRANSFERRED` | `AnimalEventMessageMapper`, `AnimalEventType` |
| Severity: `ANIMAL_REGISTERED` → `INFO`; `ANIMAL_STATUS_CHANGED` → `CRITICAL` for `DECEASED`, `WARNING` for `UNDER_OBSERVATION` or `IN_TREATMENT`, else `INFO`; `ANIMAL_TRANSFERRED` → `WARNING` if dangerous, else `INFO` | `NotificationRule.severityOf` |
| Message text per event type | `NotificationRule.messageOf` |

Payload fields (name, species, statuses, enclosure ids) are not validated. A missing one appears as `null` in the message text (`AnimalEventMessageMapper`, `NotificationRule`).

## 3. Contracts

Endpoints: none.

Events produced: none. The only records this service writes to Kafka are dead-lettered records on `zoo.animal.events.dlq`.

Events consumed: `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED`, `ANIMAL_TRANSFERRED` from `zoo.animal.events`, consumer group `notification-service`, channel `animal-events-in` (`AnimalEventConsumer`, `application.properties`). Envelope, payloads and delivery semantics: [docs/events.md](../../docs/events.md). This service keeps its own copy of the envelope record and of the contract fixtures (`AnimalEventMessage`, `src/test/resources/contract/`).

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | `Not handled` in code. No reconnect, health or retry setting is configured for the channel; the SmallRye Kafka connector's defaults apply, and no test covers it |
| Duplicate event | Skipped without writing or logging if a notification with the same `eventId` exists (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldPersistExactlyOneRowWhenTheSameEventIsDeliveredTwice`). Two copies processed at the same time: the second insert fails on the unique constraint and that record goes to the DLQ |
| Unreadable record | Malformed JSON, null or unknown `eventType`, missing required envelope field, or an enclosure id that is not a UUID: the exception propagates and the record goes to `zoo.animal.events.dlq`; consumption continues (`AnimalEventConsumer`, `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards`) |
| DB rejection | Any database error, including the database being unavailable, sends the record to the DLQ. There is no retry (`failure-strategy=dead-letter-queue`) |
| Unknown reference | `Not handled`. Any `animalId` is accepted; nothing checks that the animal exists |

Nothing reads, alerts on or replays `zoo.animal.events.dlq`.

## 5. Open questions

- Who are the recipients of a notification, and through which channel should it reach them?
- Should a transient database failure be retried instead of dead-lettering a valid event?
- How should dead-lettered records be inspected and replayed?
- Is a REST API for notifications (list, filter by severity, mark as read) in scope? [docs/STATE.md](../../docs/STATE.md) lists it under Decided, not built.
