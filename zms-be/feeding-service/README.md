# feeding-service

Feeding plans and feedings for the zoo's animals. Dev port 8084, database `feeding_db` on :5435. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `feeding_plans` table: animal id, food, quantity in grams, feeding times, notes, status, started/ended dates, audit columns, optimistic-lock version (`V1__create_feeding_tables.sql`, `FeedingPlanEntity`).
- The `feedings` table: plan id (foreign key), time fed, quantity in grams, notes, recording user (same migration, `FeedingEntity`).
- The `deceased_animals` table: a local read model of animals reported dead by `animal-service`, keyed by animal id (same migration, `DeceasedAnimalEntity`). It exists only to support the Kafka consumer below; nothing reads it over REST.

Does not:
- Know anything about animals beyond their id and whether that id has appeared in a `DECEASED` event. There is no REST call to `animal-service` (`CreateFeedingPlanService`, `pom.xml`).
- Verify that an animal id exists at all. `CreateFeedingPlanService` only rejects an id already present in `deceased_animals`; any other UUID is accepted.
- Produce events. `feeding-service` only consumes `zoo.animal.events`; there is no outbox and no `@Outgoing` channel.
- Expose the `deceased_animals` read model, or edit or delete a feeding plan, a plan's fields, or a feeding record. No endpoint exists for these.

## 2. Domain rules

| Rule | Enforced in | Result when violated |
|---|---|---|
| Actor (`performedBy`), animal id, food, quantity and at least one feeding time are required to create a plan | `CreateFeedingPlanService` | 400 (`InvalidFeedingDataException`) |
| Food ≤ 100, notes ≤ 500 characters | `CreateFeedingPlanRequest` | 400 |
| Quantity must be greater than zero when creating a plan | `CreateFeedingPlanService` | 400 |
| Feeding times: no null elements, no repeated times, whole minutes only (seconds and nanos must be zero), at most 6 | `CreateFeedingPlanService` | 400 |
| Feeding times are sorted before the plan is saved | `CreateFeedingPlanService` | — |
| A new plan starts as `ACTIVE`, with `startedOn` set to today | `CreateFeedingPlanService` | — |
| A plan cannot be created for an animal already recorded as deceased | `CreateFeedingPlanService`, `DeceasedAnimalRepository.existsByAnimalId` | 422 (`AnimalDeceasedException`) |
| Allowed plan transitions: `ACTIVE` → `SUSPENDED` or `ENDED`; `SUSPENDED` → `ACTIVE` or `ENDED` | `FeedingPlan.canTransitionTo`, `PlanStatusTransitionTest` | 422 (`InvalidPlanStatusTransitionException`) |
| `ENDED` is terminal; a transition to the current status, or to null, is rejected | `FeedingPlan.canTransitionTo` | 422 |
| `endedOn` is set to today when a plan is moved to `ENDED` through `PUT /status` | `UpdateFeedingPlanStatusService` | — |
| A feeding can be recorded only on an `ACTIVE` plan | `RecordFeedingService` | 422 (`FeedingPlanNotActiveException`) |
| Feeding quantity must not be negative; zero is allowed | `RecordFeedingService`, `RecordFeedingRequest` | 400 |
| `fedAt` defaults to now when omitted, and must not be more than one minute in the future | `RecordFeedingService` | 400 |
| Actor is required to record a feeding or change a plan's status | `RecordFeedingService`, `UpdateFeedingPlanStatusService` | 400 |
| A feeding needs an existing plan | `RecordFeedingService`, foreign key in `V1` | 404 (`FeedingPlanNotFoundException`) |
| Page ≥ 0, 1 ≤ size ≤ 100 | `ListFeedingPlansService`, `ListFeedingsService`, `ListFeedingPlansUseCase.MAX_PAGE_SIZE`, `ListFeedingsUseCase.MAX_PAGE_SIZE` | 400 |
| Concurrent writes to the same plan: the second one fails | `@Version`, `FeedingPlanJpaRepository.save` | 409 (`ConcurrentFeedingPlanUpdateException`) |

## 3. Contracts

All endpoints need a bearer token. Roles are Keycloak realm roles read from `realm_access/roles` (`application.properties`, `ZooRoles`). OIDC is off in the test profile.

| Method and path | Roles (`@RolesAllowed`) | Success | Class |
|---|---|---|---|
| `POST /feeding-plans` | `zoo-vet`, `zoo-admin` | 201, plan | `FeedingPlanResource.create` |
| `GET /feeding-plans?animalId=&page=&size=` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, page envelope `{items, page, size, total}`, newest `startedOn` first; `animalId` optional | `FeedingPlanResource.list` |
| `GET /feeding-plans/{id}` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, plan | `FeedingPlanResource.getById` |
| `PUT /feeding-plans/{id}/status` | `zoo-vet`, `zoo-admin` | 200, plan | `FeedingPlanResource.updateStatus` |
| `POST /feeding-plans/{id}/feedings` | `zoo-keeper`, `zoo-admin` | 201, feeding | `FeedingPlanResource.recordFeeding` |
| `GET /feeding-plans/{id}/feedings?page=&size=` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, page envelope, newest `fedAt` first | `FeedingPlanResource.listFeedings` |

Vets and admins define plans and change their status; keepers and admins record feedings (`FeedingSecurityIT`).

Plan request/response shape (`CreateFeedingPlanRequest`, `FeedingPlanResponse`): `animalId`, `food`, `quantityGrams`, `feedingTimes` — a list of `"HH:mm"` strings, both accepted on the way in and serialized on the way out (`@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")` on the response; the request has no explicit format annotation and relies on Quarkus's default Jackson `LocalTime` handling, exercised by `FeedingPlanResourceIT.shouldCreateFeedingPlanWithSortedFeedingTimes`) —, `notes`, plus on the response `id`, `status`, `startedOn`, `endedOn`, `createdBy`, `updatedBy`. The entity's `version` is never exposed.

Feeding request/response shape (`RecordFeedingRequest`, `FeedingResponse`): `fedAt` (optional on input), `quantityGrams`, `notes`, plus on the response `id`, `planId`, `recordedBy`.

Errors share the body `{"message": ...}` (`ErrorResponse`). Unlike `health-service`, which relies on a single `SecurityExceptionMapper` for both 401 and 403, `feeding-service` has dedicated mappers for each (`UnauthorizedExceptionMapper`, `ForbiddenExceptionMapper`), plus a `SecurityExceptionMapper` that only catches other `SecurityException` subtypes (JAX-RS picks the most specific mapper, so the dedicated ones win for `UnauthorizedException` and `ForbiddenException`). The authorization matrix is covered by `FeedingSecurityIT`.

Events produced: none. Events consumed: `zoo.animal.events` (`ANIMAL_STATUS_CHANGED` only; see below).

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | `Not handled` in code. No reconnect, health or retry setting is configured for the channel beyond the SmallRye Kafka connector's defaults, and no test covers it |
| Duplicate event | A `DECEASED` `ANIMAL_STATUS_CHANGED` event for an animal already present in `deceased_animals` is skipped: no write, no plan updated, no log line (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldPersistExactlyOneDeceasedRowWhenEventDeliveredTwiceAndNothingLandsInDlq`). Two copies processed at the same time no longer race on the `deceased_animals` primary key: `HandleAnimalEventService` takes the per-animal `AnimalLock` before the `existsByAnimalId` check, so the second copy waits for the first to commit and then finds the animal already recorded and skips (see "Concurrency" below) |
| Unreadable record | Malformed JSON, or a null `eventId`, `eventType`, `animalId`, `occurredAt`, or a null `newStatus` on an `ANIMAL_STATUS_CHANGED` event: the exception propagates and the record goes to `zoo.animal.events.feeding.dlq`; consumption continues (`AnimalEventConsumer`, `HandleAnimalEventService`, `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards`) |
| DB rejection | Any database error while writing `deceased_animals` or ending plans, including the database being unavailable, sends the record to the DLQ. There is no retry (`failure-strategy=dead-letter-queue`) |
| Unknown reference | Unknown feeding plan id: 404 on read, status change and recording a feeding (`FeedingPlanNotFoundException`). Unknown `animalId` on plan creation: `Not handled`. A plan is created for any UUID not already in `deceased_animals`, including an animal that does not exist in `animal-service`; listing by an unknown `animalId` returns an empty page |
| Malformed REST JSON body | There is no dedicated `JsonProcessingException` mapper, unlike `animal-service`. Falls through to `UnexpectedExceptionMapper`'s 500 unless RESTEasy Reactive's own Jackson provider answers first; no test covers this case |

### Kafka consumer

- Consumes `zoo.animal.events`, channel `animal-events-in`, consumer group `feeding-service`, from the earliest offset when the group has no committed offset (`application.properties`).
- Only `ANIMAL_STATUS_CHANGED` is acted on. `ANIMAL_REGISTERED` and `ANIMAL_TRANSFERRED` are read and ignored — `HandleAnimalEventService.handle` returns immediately once envelope validation passes and `eventType` isn't `ANIMAL_STATUS_CHANGED` (`HandleAnimalEventServiceTest.shouldIgnoreNonStatusChangedEventTypes`, `AnimalEventConsumerIT.shouldNotChangeAnythingForNonDeceasedEvents`).
- Within `ANIMAL_STATUS_CHANGED`, only `newStatus = DECEASED` triggers action; any other new status is read and ignored (`HandleAnimalEventServiceTest.shouldIgnoreStatusChangeToNonDeceasedStatus`).
- On a genuine, not-yet-seen `DECEASED` event: the animal id, event id and `occurredAt` are written to `deceased_animals`, then every `ACTIVE` or `SUSPENDED` plan for that animal is moved to `ENDED`, with `endedOn` set to the date of the event's `occurredAt` in the JVM's default time zone, the same zone that sets `startedOn`, and never earlier than the plan's `startedOn` and `updatedBy` set to the event's `performedBy`, or the literal string `"animal-service"` if `performedBy` is null or blank (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldEndActiveAndSuspendedPlansWhenAnimalDies`). Plans already `ENDED` are left untouched, including their original `endedOn`.
- Idempotency does not key on `eventId` the way `notification-service` does. `deceased_animals.animal_id` is the primary key, and once the event is confirmed to be a `DECEASED` status change, the handler's first step is to acquire the per-animal `AnimalLock`, then check `existsByAnimalId`. Because `animal-service`'s `Animal.canTransitionTo` makes `DECEASED` terminal (one status transition per animal, ever — see [docs/STATE.md](../../docs/STATE.md)), keying on the animal id also absorbs redelivery of that one event, not only literal duplicates of the same `eventId`.
- Dead-letter topic: `zoo.animal.events.feeding.dlq`, its own topic, separate from `notification-service`'s `zoo.animal.events.dlq` and `health-service`'s `zoo.animal.events.health.dlq` — the three consumers read the same `zoo.animal.events` topic independently, each with its own consumer group and DLQ (`application.properties`).

## 5. Concurrency

- Per-animal advisory lock: `AnimalLock` (`domain/port/out/AnimalLock.java`), implemented by `PostgresAnimalLock` as `SELECT pg_advisory_xact_lock(:key)`, held for the rest of the current transaction. `CreateFeedingPlanService` acquires it, keyed on `animalId`, right after validation and before `existsByAnimalId`; `HandleAnimalEventService` acquires it right after confirming the event is a `DECEASED` status change and before its own `existsByAnimalId` check. This serializes "create a plan for this animal" against "record this animal as deceased" for the same animal id, so a plan can no longer be committed for an animal whose `DECEASED` event is mid-flight, and two concurrent copies of the same `DECEASED` event can no longer both pass the `existsByAnimalId` check (see "Duplicate event" above).
- Row locks on `feeding_plans`: `FeedingPlanRepository.findByIdForUpdate` and `findByAnimalIdAndStatusInForUpdate` (`FeedingPlanJpaRepository`, `LockModeType.PESSIMISTIC_WRITE`). `RecordFeedingService` and `UpdateFeedingPlanStatusService` use `findByIdForUpdate`; the Kafka consumer's `HandleAnimalEventService` uses `findByAnimalIdAndStatusInForUpdate`. This serializes a feeding-plan status change (`PUT /feeding-plans/{id}/status`) against both a feeding being recorded on the same plan and the consumer ending that same plan for a deceased animal, so `RecordFeedingService` can no longer save a feeding against a plan that a concurrent status change or the consumer just moved out of `ACTIVE`, and `HandleAnimalEventService` can no longer lose a plan to a status change that commits between its read and its own update.
- With both locks in place, `ConcurrentFeedingPlanUpdateException` (`@Version` on `FeedingPlanEntity`) should no longer occur between these code paths; the `@Version` check and its 409 mapping (`ConcurrentFeedingPlanUpdateExceptionMapper`) stay in place as a safety net for any write pattern that does not go through the locked paths.
- Covered by `FeedingConcurrencyIT` (deterministic with `CountDownLatch`es and bounded `Future#get` timeouts, one scenario per lock).

## 6. Open questions

- Is a REST read of `deceased_animals`, or of plans a consumer has auto-ended, in scope? No endpoint exists today.
- What does a malformed JSON body on a feeding-plan write return? No dedicated mapper, unlike `animal-service`, and no test covers it.
- Should creating a feeding plan be refused for an animal id that does not exist at all in `animal-service` (as opposed to one recorded as deceased)? Nothing checks it; any UUID not in `deceased_animals` is accepted.
- Should a wrong feeding record be correctable? Feedings are append-only: no endpoint edits or deletes one.
