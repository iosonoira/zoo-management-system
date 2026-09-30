# health-service

Medical records and treatments of the zoo's animals. Dev port 8082, database `health_db` on :5433. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `medical_records` table: animal id, reason, diagnosis, examination date, veterinarian, audit columns (`V1__create_medical_records_and_treatments.sql`, `MedicalRecordEntity`).
- The `treatments` table: medical record id (foreign key), description, status, start and end dates, audit columns (same migration, `TreatmentEntity`).
- The `deceased_animals` table: a local read model of animals reported dead by `animal-service`, keyed by animal id, with the id and `occurredAt` of the event that recorded it (`V2__create_deceased_animals.sql`, `DeceasedAnimalEntity`). Nothing reads it over REST; `PrescribeTreatmentService`, `UpdateTreatmentStatusService` and `HandleAnimalEventService` read it through `DeceasedAnimalRepository.existsByAnimalId`.

Does not:
- Know anything about animals beyond their id and whether that id has appeared in a `DECEASED` event. There is no REST call to `animal-service` (`CreateMedicalRecordService`, `pom.xml`).
- Verify that an animal id exists at all. `CreateMedicalRecordService` accepts any UUID, and `PrescribeTreatmentService` only rejects an id already present in `deceased_animals`.
- Produce events. `health-service` only consumes `zoo.animal.events`; there is no outbox and no `@Outgoing` channel. See [docs/events.md](../../docs/events.md).
- Expose the `deceased_animals` read model, or edit or delete a medical record, or edit a treatment's description. No endpoint exists for these.

## 2. Domain rules

| Rule | Enforced in | Result when violated |
|---|---|---|
| A new treatment starts as `PRESCRIBED` | `PrescribeTreatmentService` | — |
| Allowed transitions: `PRESCRIBED` → `ACTIVE` or `CANCELLED`; `ACTIVE` → `COMPLETED` or `CANCELLED` | `Treatment.canTransitionTo` | 422 (`InvalidTreatmentStatusTransitionException`) |
| `COMPLETED` and `CANCELLED` are terminal | `Treatment.canTransitionTo` | 422 |
| A transition to the current status, or to null, is rejected | `Treatment.canTransitionTo` | 422 |
| `startedOn` is set to today the first time a treatment becomes `ACTIVE`; `endedOn` is set to today on `COMPLETED` or `CANCELLED` | `UpdateTreatmentStatusService` | — |
| The examination date must not be in the future in every time zone: it may be at most today's date at UTC+14, so a vet ahead of the server's zone can still enter their own today | `CreateMedicalRecordService.EARLIEST_ZONE` | 400 (`InvalidMedicalDataException`) |
| Animal id, reason, diagnosis, examination date, veterinarian and acting user are required | `CreateMedicalRecordService`, `CreateMedicalRecordRequest` | 400 |
| Reason ≤ 200, diagnosis ≤ 1000, veterinarian ≤ 100, treatment description ≤ 500 characters | `CreateMedicalRecordRequest`, `PrescribeTreatmentRequest` | 400 |
| A treatment needs an existing medical record | `PrescribeTreatmentService`, foreign key in `V1` | 404 (`MedicalRecordNotFoundException`) |
| Page ≥ 0, 1 ≤ size ≤ 100 | `ListMedicalRecordsService`, `ListMedicalRecordsUseCase.MAX_PAGE_SIZE` | 400 |
| A treatment cannot be prescribed for an animal already recorded as deceased | `PrescribeTreatmentService`, `DeceasedAnimalRepository.existsByAnimalId` | 422 (`AnimalDeceasedException`, `AnimalDeceasedExceptionMapper`) |
| A treatment of an animal already recorded as deceased cannot be moved to `ACTIVE`. A move to `COMPLETED` or `CANCELLED` is still allowed. The transition rules above are checked first | `UpdateTreatmentStatusService` | 422 (`AnimalDeceasedException`) |
| A medical record can still be created for an animal recorded as deceased: `CreateMedicalRecordService` does not check `deceased_animals` | `CreateMedicalRecordService`, `MedicalRecordResourceIT.shouldCreateMedicalRecordForDeceasedAnimal` | — |
| Concurrent writes to the same treatment: the second one fails. The animal lock (section 5) already serializes the code paths that write treatments, so this is a safety net | `@Version`, `TreatmentJpaRepository` | 409 (`ConcurrentTreatmentUpdateException`) |

`veterinarian` is free text supplied by the client. It is stored separately from `createdBy`, which is the authenticated user (`MedicalRecordResource.currentActor`), and nothing compares the two.

## 3. Contracts

All endpoints need a bearer token. Roles are Keycloak realm roles read from `realm_access/roles` (`application.properties`, `ZooRoles`). OIDC is off in the test profile.

| Method and path | Roles (`@RolesAllowed`) | Success | Class |
|---|---|---|---|
| `POST /medical-records` | `zoo-vet`, `zoo-admin` | 201, record | `MedicalRecordResource.create` |
| `GET /medical-records?animalId=&page=&size=` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, page envelope `{items, page, size, total}`, newest examination first; `animalId` optional | `MedicalRecordResource.list` |
| `GET /medical-records/{id}` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, record with its treatments | `MedicalRecordResource.getById` |
| `POST /medical-records/{id}/treatments` | `zoo-vet`, `zoo-admin` | 201, treatment | `MedicalRecordResource.prescribe` |
| `PUT /treatments/{id}/status` | `zoo-vet`, `zoo-admin` | 200, treatment | `TreatmentResource.updateStatus` |

Errors share the body `{"message": ...}` (`ErrorResponse`); 401 and 403 come from `SecurityExceptionMapper`, `UnauthorizedExceptionMapper`, `ForbiddenExceptionMapper`. The authorization matrix is covered by `HealthSecurityIT`.

The 422 for a deceased animal has the body `{"message": "Animal <animalId> is deceased"}` (`AnimalDeceasedException`, `AnimalDeceasedExceptionMapper`). It applies to `POST /medical-records/{id}/treatments` and to `PUT /treatments/{id}/status` when the new status is `ACTIVE`; it is covered by `MedicalRecordResourceIT` (`shouldReturn422WhenPrescribingForDeceasedAnimal`, `shouldReturn422WhenActivatingTreatmentOfDeceasedAnimal`, `shouldCompleteActiveTreatmentOfDeceasedAnimal`).

Events produced: none. Events consumed: `zoo.animal.events` (`ANIMAL_STATUS_CHANGED` only; see below).

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | `Not handled` in code. No reconnect, health or retry setting is configured for the channel beyond the SmallRye Kafka connector's defaults, and no test covers it |
| Duplicate event | A `DECEASED` `ANIMAL_STATUS_CHANGED` event for an animal already present in `deceased_animals` is skipped: no write and no treatment updated (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldPersistExactlyOneDeceasedRowWhenEventDeliveredTwiceAndNothingLandsInDlq`). Two copies processed at the same time do not race on the `deceased_animals` primary key: `HandleAnimalEventService` takes the per-animal `AnimalLock` before the `existsByAnimalId` check, so the second copy waits for the first to commit and then skips (see section 5). Duplicate REST requests are not deduplicated: two identical `POST /medical-records` create two records |
| Unreadable event | Malformed JSON, or a null `eventId`, `eventType`, `animalId`, `occurredAt`, or a null `newStatus` on an `ANIMAL_STATUS_CHANGED` event: the exception propagates and the record goes to `zoo.animal.events.health.dlq`; consumption continues (`AnimalEventConsumer`, `HandleAnimalEventService`, `AnimalEventConsumerIT.shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards`). The null checks are covered by `HandleAnimalEventServiceTest` only |
| Unreadable record | Path id that is not a UUID: 404 (`MedicalRecordResourceIT.shouldReturn404WhenIdIsNotAUuid`). Other `WebApplicationException`s keep the framework's response (`UnexpectedExceptionMapper`). Malformed JSON body: there is no `JsonProcessingException` mapper, unlike `animal-service`, and no test covers it |
| DB rejection | Optimistic-lock conflict on a treatment: 409. Any other database error on a REST request: 500 `Internal server error`, logged (`UnexpectedExceptionMapper`); the transaction rolls back. Any database error while writing `deceased_animals` or cancelling treatments in the consumer, including the database being unavailable, rolls back the transaction of `HandleAnimalEventService.handle` and sends the record to the DLQ. There is no retry (`failure-strategy=dead-letter-queue`) |
| Unknown reference | Unknown medical record id: 404 on read and on prescribe. Unknown treatment id: 404. Unknown `animalId`: `Not handled`. A record is created for any UUID, including an animal that does not exist or is `DECEASED`; a treatment is refused only for an id already in `deceased_animals`; listing by an unknown `animalId` returns an empty page. A `DECEASED` event for an animal id with no medical record is still written to `deceased_animals` (`HandleAnimalEventService`) |

### Kafka consumer

- Consumes `zoo.animal.events`, channel `animal-events-in`, consumer group `health-service`, from the earliest offset when the group has no committed offset (`application.properties`). The envelope is read into `AnimalEventMessage`, defined in this service; there is no shared module with `animal-service`.
- Only `ANIMAL_STATUS_CHANGED` is acted on. `ANIMAL_REGISTERED` and `ANIMAL_TRANSFERRED` are read and ignored: `HandleAnimalEventService.handle` returns once envelope validation passes and `eventType` is not `ANIMAL_STATUS_CHANGED` (`HandleAnimalEventServiceTest.shouldIgnoreNonStatusChangedEventTypes`, `AnimalEventConsumerIT.shouldNotChangeAnythingForNonDeceasedEvents`).
- Within `ANIMAL_STATUS_CHANGED`, only `newStatus = DECEASED` triggers action; any other new status is read and ignored (`HandleAnimalEventServiceTest.shouldIgnoreStatusChangeToNonDeceasedStatus`).
- On a genuine, not-yet-seen `DECEASED` event: the animal id, event id and `occurredAt` are written to `deceased_animals`, then every `PRESCRIBED` or `ACTIVE` treatment of that animal's medical records is moved to `CANCELLED`, with `endedOn` set to the date of the event's `occurredAt` in the JVM's default time zone and never earlier than the treatment's `startedOn`, and `updatedBy` set to the event's `performedBy`, or the literal string `"animal-service"` if `performedBy` is null or blank (`HandleAnimalEventService`, `AnimalEventConsumerIT.shouldCancelPrescribedAndActiveTreatmentsWhenAnimalDies`, `HandleAnimalEventServiceTest.shouldNotEndTreatmentBeforeItStarted`, `HandleAnimalEventServiceTest.shouldDefaultUpdatedByToAnimalServiceWhenPerformedByIsBlank`). Treatments already `COMPLETED` or `CANCELLED` are left untouched, including their original `endedOn`, and so are the treatments of other animals.
- Idempotency does not key on `eventId` the way `notification-service` does. `deceased_animals.animal_id` is the primary key, and once the event is confirmed to be a `DECEASED` status change, the handler's first step is to acquire the per-animal `AnimalLock`, then check `existsByAnimalId`. Because `animal-service`'s `Animal.canTransitionTo` makes `DECEASED` terminal (see [docs/STATE.md](../../docs/STATE.md)), keying on the animal id also absorbs redelivery of that one event, not only literal duplicates of the same `eventId`.
- Dead-letter topic: `zoo.animal.events.health.dlq`, its own topic, separate from `zoo.animal.events.dlq` (`notification-service`) and `zoo.animal.events.feeding.dlq` (`feeding-service`): the three consumers read the same `zoo.animal.events` topic independently, each with its own consumer group and DLQ (`application.properties`).
- Wire shape: `AnimalEventMessageContractTest` maps the three fixtures in `src/test/resources/contract/` to `HandleAnimalEventCommand`. Field details are in [docs/events.md](../../docs/events.md).

## 5. Concurrency

- Per-animal advisory lock: `AnimalLock` (`domain/port/out/AnimalLock.java`), implemented by `PostgresAnimalLock` as `pg_advisory_xact_lock(:key)`, with the key folded from the animal id, held for the rest of the current transaction. It is taken by three writers, always before they read treatments or `deceased_animals`:
  - `HandleAnimalEventService`, once the event is a `DECEASED` status change and before `existsByAnimalId`;
  - `PrescribeTreatmentService`, after it finds the medical record and before `existsByAnimalId`;
  - `UpdateTreatmentStatusService`, after it finds the animal id of the treatment and before it loads the treatment.
- This serializes "prescribe or change a treatment of this animal" against "record this animal as deceased and cancel its treatments": a prescription either commits before the event and is cancelled by it, or waits and then finds the animal recorded as deceased; a status change never works on a treatment that a concurrent event has just cancelled.
- No row locks: unlike `feeding-service`, there is no `PESSIMISTIC_WRITE` query. `UpdateTreatmentStatusService` reads only the animal id first (`TreatmentRepository.findAnimalIdByTreatmentId`, a scalar query, `TreatmentJpaRepository`) and loads the treatment after the lock.
- `ConcurrentTreatmentUpdateException` (`@Version` on `TreatmentEntity`) should not occur between these code paths; the version check and its 409 mapping (`ConcurrentTreatmentUpdateExceptionMapper`) stay in place as a safety net.
- Covered by `HealthConcurrencyIT` (`CountDownLatch`es and bounded `Future#get` timeouts): prescribing blocked by a concurrent `DECEASED` event, starting a treatment cancelled by the event, the event after a concurrent status change, and a treatment prescribed just before the event.

## 6. Open questions

- Should creating a medical record, or prescribing a treatment, be refused for an animal id that does not exist at all in `animal-service` (as opposed to one recorded as deceased)? Nothing checks it; any UUID is accepted for a new record.
- What does a malformed JSON body return? In `animal-service` the same case needed a dedicated mapper to avoid a 500; here there is none and no test.
- Treatments in `GET /medical-records/{id}` are ordered by id, which is a random UUID (`TreatmentJpaRepository`). Is a date order expected?
- `ConcurrentMedicalRecordUpdateException` is mapped to 409, but no use case updates a medical record. Is a record update planned?
