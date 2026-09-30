# notification-service

Turns animal events into stored notifications, lists them over REST and records who acknowledged them. Dev port 8083, database `notification_db` on :5434. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `notifications` table: event id (unique), animal id, event type, severity, message, event time, creation time (`V1__create_notifications_table.sql`, `NotificationEntity`).
- The event's structured fields, stored next to the message: author (`performed_by`), animal name, species, dangerous flag, previous and new status, from and to enclosure id. All are nullable, and `V2` does not backfill them, so a row stored before `V2` has nulls in them and only its `message` (`V2__add_event_fields_and_acknowledgement.sql`, `NotificationEntity`).
- The acknowledgement of a notification: `acknowledged_by` and `acknowledged_at`, one pair per notification, shared by all staff (same migration, [D13](../../docs/decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation)).
- The rules that turn an event into a severity and a message (`NotificationRule`).
- The REST API over the table: list and acknowledge (`NotificationResource`).

Does not:
- Create, edit or delete a notification over REST. `NotificationResource` has one `GET` and one `PUT`; rows come only from the Kafka consumer.
- Deliver notifications to people. There is no email, push or websocket code, and no notion of recipient. Besides the table and the REST responses, the only output is an `INFO` log line per stored notification (`HandleAnimalEventService`) and per first acknowledgement (`AcknowledgeNotificationService`).
- Keep read state per user. The only state is the shared acknowledgement.
- Call `animal-service` or validate animal ids.

## 2. Domain rules

| Rule | Enforced in |
|---|---|
| One notification per `eventId`: a repeated event is skipped | `HandleAnimalEventService` (existence check), unique constraint `uq_notifications_event_id` |
| `eventId`, `eventType`, `animalId` and `occurredAt` are required | `HandleAnimalEventService` (`InvalidAnimalEventException`) |
| `eventType` must be one of `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED`, `ANIMAL_TRANSFERRED` | `AnimalEventMessageMapper`, `AnimalEventType` |
| Severity: `ANIMAL_REGISTERED` → `INFO`; `ANIMAL_STATUS_CHANGED` → `CRITICAL` for `DECEASED`, `WARNING` for `UNDER_OBSERVATION` or `IN_TREATMENT`, else `INFO`; `ANIMAL_TRANSFERRED` → `WARNING` if dangerous, else `INFO` | `NotificationRule.severityOf` |
| Message text per event type | `NotificationRule.messageOf` |
| Structured fields per event type. `ANIMAL_REGISTERED`: author, name, species, dangerous, and `toEnclosureId` from the payload's `enclosureId`. `ANIMAL_STATUS_CHANGED`: author, name, species, previous and new status. `ANIMAL_TRANSFERRED`: author, name, species, dangerous, `fromEnclosureId`, `toEnclosureId`. Every other field is null | `AnimalEventMessageMapper`, `HandleAnimalEventService`, `AnimalEventConsumerIT.shouldPersistTheStructuredEventFieldsAndLeaveAcknowledgementEmpty` |
| A new notification is open: `acknowledged_by` and `acknowledged_at` are null | `HandleAnimalEventService` |
| An acknowledgement sets `acknowledgedBy` to the caller's principal name and `acknowledgedAt` to the current time, only while the notification is open. A later one changes nothing and returns the stored notification. Two at the same time: one conditional `UPDATE ... WHERE acknowledged_at IS NULL` wins | `AcknowledgeNotificationService`, `NotificationJpaRepository.acknowledge`, `NotificationResource.currentActor`, `NotificationAcknowledgementIT` |
| Any notification can be acknowledged, whatever its severity or event type. No rule restricts it | `AcknowledgeNotificationService` |
| `page` ≥ 0 and 1 ≤ `size` ≤ 100 on the list, else 400 | `ListNotificationsService`, `ListNotificationsUseCase.MAX_PAGE_SIZE` |
| A `severity` filter value must be `INFO`, `WARNING` or `CRITICAL`, in upper case; surrounding spaces are ignored. Anything else is 400 | `ListNotificationsService.parseSeverity` |

Payload fields (name, species, statuses, enclosure ids) are not validated. A missing one is stored as null in its column and appears as `null` in the message text (`AnimalEventMessageMapper`, `HandleAnimalEventService`, `NotificationRule`).

## 3. Contracts

All endpoints need a bearer token. Roles are Keycloak realm roles read from `realm_access/roles` (`application.properties`, `ZooRoles`). OIDC is off in the test profile and on in `%dev` and `%prod`, with client id `notification-service` (`application.properties`). In `%dev` the secret is `OIDC_CLIENT_SECRET` and Keycloak is http://localhost:8081/realms/zoo; `%prod` reads `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_SECRET` and, optionally, `OIDC_CLIENT_ID`. CORS: `%dev` allows the origin http://localhost:4200; `%prod` allows origins only when `CORS_ENABLED` is true, from `CORS_ORIGINS`. Methods are `GET`, `PUT` and `OPTIONS` in both (`application.properties`).

| Method and path | Roles (`@RolesAllowed`) | Success | Class |
|---|---|---|---|
| `GET /notifications?animalId=&severity=&open=&page=&size=` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, page envelope `{items, page, size, total}` | `NotificationResource.list` |
| `PUT /notifications/{id}/acknowledge` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, notification. No request body | `NotificationResource.acknowledge` |

Query parameters of `GET /notifications`, all optional:
- `animalId`: only that animal's notifications.
- `severity`: `INFO`, `WARNING` or `CRITICAL`; repeat it to accept several (`severity=WARNING&severity=CRITICAL`). Unknown value: 400.
- `open`: default `false`. With `true`, only notifications with no acknowledgement; with `false`, open and acknowledged ones.
- `page` (default 0) and `size` (default 20, at most 100).

Filters combine with AND. The order is `occurredAt` newest first, then `id` (`NotificationJpaRepository.findPage`). `total` counts the rows that match the filters (`NotificationJpaRepository.count`).

Notification fields (`NotificationResponse`): `id`, `animalId`, `eventType`, `severity`, `message`, `occurredAt`, `performedBy`, `name`, `species`, `dangerous`, `previousStatus`, `newStatus`, `fromEnclosureId`, `toEnclosureId`, `acknowledgedBy`, `acknowledgedAt`. `eventId` and `createdAt` are stored but not returned. The structured fields are null where the event type does not carry them (section 2) and on rows stored before `V2`. `acknowledgedBy` and `acknowledgedAt` are null until the first acknowledgement. `message` is always set and still contains enclosure ids, not names (`NotificationRule`). Covered by `NotificationResourceIT.shouldExposeTheDocumentedFieldsAndNeitherEventIdNorCreatedAt`.

Errors share the body `{"message": ...}` (`ErrorResponse`). 400 comes from `InvalidNotificationQueryExceptionMapper`, 404 from `NotificationNotFoundExceptionMapper` (`Notification not found with id: <id>`), 401 and 403 from `SecurityExceptionMapper`, `UnauthorizedExceptionMapper` and `ForbiddenExceptionMapper` (`Authentication required`, `Insufficient role`). The authorization matrix is covered by `NotificationSecurityIT`.

Events produced: none. The only records this service writes to Kafka are dead-lettered records on `zoo.animal.events.dlq`.

Events consumed: `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED`, `ANIMAL_TRANSFERRED` from `zoo.animal.events`, consumer group `notification-service`, channel `animal-events-in` (`AnimalEventConsumer`, `application.properties`). Envelope, payloads and delivery semantics: [docs/events.md](../../docs/events.md). This service keeps its own copy of the envelope record and of the contract fixtures (`AnimalEventMessage`, `src/test/resources/contract/`).

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | `Not handled` in code. No reconnect, health or retry setting is configured for the channel; the SmallRye Kafka connector's defaults apply, and no test covers it |
| Keycloak unavailable | `Not handled` in code. No OIDC connection or retry setting is configured (`application.properties`); the extension's defaults apply, and no test covers it |
| Duplicate event | Skipped without writing or logging if a notification with the same `eventId` exists (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldPersistExactlyOneRowWhenTheSameEventIsDeliveredTwice`). Two copies processed at the same time: the second insert fails on the unique constraint and that record goes to the DLQ |
| Unreadable record | Malformed JSON, null or unknown `eventType`, missing required envelope field, or an enclosure id that is not a UUID (`enclosureId` on `ANIMAL_REGISTERED`, `fromEnclosureId` or `toEnclosureId` on `ANIMAL_TRANSFERRED`): the exception propagates and the record goes to `zoo.animal.events.dlq`; consumption continues (`AnimalEventConsumer`, `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards`) |
| DB rejection | Any database error, including the database being unavailable, sends the record to the DLQ. There is no retry (`failure-strategy=dead-letter-queue`). On a REST request, a database error returns 500 `Internal server error` and is logged (`UnexpectedExceptionMapper`) |
| Unknown reference | Unknown notification id on `PUT /notifications/{id}/acknowledge`: 404 (`AcknowledgeNotificationService`, `NotificationResourceIT.shouldReturn404WhenAcknowledgingAnUnknownNotification`). Unknown `animalId`: `Not handled`. Any `animalId` is accepted in an event, and nothing checks that the animal exists; listing by an unknown `animalId` returns an empty page |
| Acknowledging again | 200 with the stored notification, `acknowledgedBy` and `acknowledgedAt` unchanged, whoever calls (`NotificationResourceIT.shouldKeepTheFirstAcknowledgementWhenAcknowledgedAgain`, `NotificationResourceIT.shouldKeepTheKeepersAcknowledgementWhenAVetAcknowledgesAfterwards`) |
| Two acknowledgements at once | The second `UPDATE` waits for the row lock and then matches no row, so the first one stays (`NotificationAcknowledgementIT.shouldLetTheFirstOfTwoConcurrentAcknowledgementsWin`) |
| Invalid query | Unknown `severity`, negative `page`, `size` below 1 or above 100: 400 (`NotificationResourceIT`). A malformed `animalId` is not a 400: the framework answers 404 (`NotificationResourceIT.shouldRejectAMalformedAnimalIdWithAFrameworkStatus`). Other `WebApplicationException`s keep the framework's response (`UnexpectedExceptionMapper`); no test covers a malformed `{id}` on acknowledge |

Nothing reads, alerts on or replays `zoo.animal.events.dlq`.

## 5. Open questions

- Who are the recipients of a notification, and through which channel should it reach them?
- Should a transient database failure be retried instead of dead-lettering a valid event?
- How should dead-lettered records be inspected and replayed?
- Should `GET /notifications` filter by acknowledged state? It only has `open`, so a client that wants acknowledged rows reads them unfiltered and filters itself, as the frontend does ([D13](../../docs/decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation)).
