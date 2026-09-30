# Project state

Snapshot of what exists, what is decided but not built, and what is still open. Updated at the end of every phase.

Last updated: 2026-09-30, at commit `82bd994`.

The three labels never mix:
- **Implemented**: in the code on `main`. Every line cites a class or file.
- **Decided, not built**: the maintainer recorded it as planned, but there is no code yet. Every line cites where the plan is recorded.
- **Open**: nobody has decided yet, or the code leaves the scenario unhandled.

Contracts and failure behaviour per service are in the service READMEs. Events are in [events.md](events.md), and decisions are in [decisions.md](decisions.md).

## animal-service

### Implemented
- Register, get, paged list, status change and transfer of animals (`RegisterAnimalService`, `GetAnimalService`, `ListAnimalsService`, `UpdateAnimalStatusService`, `TransferAnimalService`, `AnimalResource`).
- Status rules: `DECEASED` is terminal, a change to the same status is rejected, and a `DECEASED` animal cannot be transferred (`Animal.canTransitionTo`, `Animal.canBeTransferred`).
- Paging on `GET /animals`: `page` ≥ 0, `size` 1–100, ordered by name then id (`ListAnimalsService`, `AnimalJpaRepository.findPage`).
- Enclosures as a persisted, read-only table, listed by `GET /enclosures` (`V5__create_enclosures_table.sql`, `EnclosureResource`, `ListEnclosuresService`).
- Unknown enclosure rejected on register and transfer (`UnknownEnclosureException`, `EnclosureRepository.existsById`).
- Dev seed data for animals and enclosures (`db/dev/R__seed_demo_animals.sql`, `db/dev/R__seed_demo_enclosures.sql`).
- Role-based authorization per endpoint (`@RolesAllowed` on `AnimalResource`, `EnclosureResource`; `ZooRoles`; `AnimalSecurityIT`).
- Audit of the acting user: `createdBy`, `updatedBy` (`V2__add_audit_columns.sql`, `AnimalResource.currentActor`).
- Optimistic locking, with 409 on a conflict (`V3__add_version_column.sql`, `AnimalJpaRepository.save`, `ConcurrentAnimalUpdateExceptionMapper`).
- JSON error body on every error path (`ErrorResponse` and the `*ExceptionMapper` classes in `infrastructure/rest/`).
- Transactional outbox and Kafka relay for three event types (`OutboxAnimalEventPublisher`, `OutboxRelay`, `V4__create_outbox_table.sql`). Details: [events.md](events.md).
- Prod profile configured from environment variables (`application.properties`, `%prod.*`).
- OpenAPI with a bearer scheme (`OpenApiConfig`).

### Decided, not built
- Cleanup of published `outbox_event` rows (next-phases list in `zms-be/CLAUDE.md` at commit `febdaf6`).

### Open
- Outbox failure scenarios: no maximum attempts or parking for a row that always fails, which then blocks every later row; per-animal ordering is not guaranteed with more than one instance. See [events.md, Not handled](events.md#not-handled).
- The relay can publish duplicates. Consumers must deduplicate by `eventId` ([events.md](events.md#duplicates)).
- Enclosure provisioning outside dev and test. No prod script or endpoint creates enclosures, so registration fails with 400 until rows are inserted by hand.
- A transfer to the current enclosure is accepted and emits an event (`TransferAnimalService`).
- The animal's habitat is not compared with the enclosure's habitat.
- `GET /animals` has paging but no server-side search or filter. `GET /enclosures` has no paging.

## health-service

### Implemented
- Create, get and paged list of medical records, with an optional `animalId` filter; the list is ordered by examination date, newest first (`CreateMedicalRecordService`, `GetMedicalRecordService`, `ListMedicalRecordsService`, `MedicalRecordResource`, `MedicalRecordJpaRepository`).
- Prescribing treatments and changing their status (`PrescribeTreatmentService`, `UpdateTreatmentStatusService`, `TreatmentResource`).
- Treatment lifecycle: `PRESCRIBED` → `ACTIVE` or `CANCELLED`, `ACTIVE` → `COMPLETED` or `CANCELLED`, with the last two terminal (`Treatment.canTransitionTo`, `TreatmentStatusTransitionTest`).
- Examination date not in the future in any time zone, i.e. at most today at UTC+14 (`CreateMedicalRecordService.EARLIEST_ZONE`).
- Role-based authorization per endpoint (`@RolesAllowed` on `MedicalRecordResource`, `TreatmentResource`; `HealthSecurityIT`).
- Audit columns and optimistic locking (`V1__create_medical_records_and_treatments.sql`, `TreatmentJpaRepository.save`).
- Kafka consumer of `zoo.animal.events`, own consumer group `health-service`: on an `ANIMAL_STATUS_CHANGED` event with `newStatus = DECEASED`, records the animal in `deceased_animals` and moves every `PRESCRIBED` or `ACTIVE` treatment of its medical records to `CANCELLED`; `ANIMAL_REGISTERED`, `ANIMAL_TRANSFERRED` and any other new status are read and ignored (`AnimalEventConsumer`, `HandleAnimalEventService`, `V2__create_deceased_animals.sql`). Idempotent per animal id via `deceased_animals`, not per event id. Details: [events.md](events.md#consumer-idempotency-health-service).
- A treatment cannot be prescribed for an animal recorded as deceased, and cannot be moved to `ACTIVE`; a move to `COMPLETED` or `CANCELLED` is still allowed, and a medical record can still be created. All refusals are 422 (`PrescribeTreatmentService`, `UpdateTreatmentStatusService`, `DeceasedAnimalRepository.existsByAnimalId`, `AnimalDeceasedExceptionMapper`, `MedicalRecordResourceIT`).
- Concurrency safety between the Kafka consumer, prescribing and treatment status changes: a per-animal Postgres advisory lock (`AnimalLock`, `PostgresAnimalLock`) taken by `HandleAnimalEventService`, `PrescribeTreatmentService` and `UpdateTreatmentStatusService` before they read treatments; no row locks (`HealthConcurrencyIT`).
- Dead-letter queue for any failed consumed record, on its own topic `zoo.animal.events.health.dlq` (`application.properties`, `failure-strategy=dead-letter-queue`).
- Wire-format contract test against shared fixtures (`AnimalEventMessageContractTest`).
- Prod Kafka configuration from an environment variable (`application.properties`, `%prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}`).

### Decided, not built
- Nothing currently recorded beyond what is implemented.

### Open
- Eventual consistency with `animal-service`. Confirmed in code: nothing checks that an animal id exists at all before a medical record is created or a treatment is prescribed; `PrescribeTreatmentService` only rejects an id already present in `deceased_animals` (`CreateMedicalRecordService`, `PrescribeTreatmentService`). There is no call to `animal-service`, so `health-service` learns that an animal is deceased only when it consumes the event. Creating a medical record for a deceased animal is accepted by `CreateMedicalRecordService` ([D11](decisions.md#d11-no-new-treatments-for-a-deceased-animal-frontend-only), [D12](decisions.md#d12-health-service-enforces-d11-and-cancels-a-deceased-animals-open-treatments)).
- Malformed JSON body: there is no `JsonProcessingException` mapper and no test for it.
- Treatments in a record's detail are ordered by a random UUID (`TreatmentJpaRepository.findByMedicalRecordId`).
- No update or delete of a medical record, although `ConcurrentMedicalRecordUpdateException` is mapped to 409.

## notification-service

### Implemented
- Kafka consumer of `zoo.animal.events` that stores one notification per event (`AnimalEventConsumer`, `HandleAnimalEventService`, `V1__create_notifications_table.sql`).
- The event's structured fields stored per notification: author, animal name, species, dangerous flag, statuses, enclosure ids. The registered event's `enclosureId` is stored as `toEnclosureId` (`V2__add_event_fields_and_acknowledgement.sql`, `AnimalEventMessageMapper`, `AnimalEventConsumerIT.shouldPersistTheStructuredEventFieldsAndLeaveAcknowledgementEmpty`). Details: [events.md](events.md#what-notification-service-stores).
- `GET /notifications`, paged, filtered by `animalId`, a repeatable `severity` and `open`, ordered by event time newest first, then id; an unknown severity is 400 (`NotificationResource.list`, `ListNotificationsService`, `NotificationJpaRepository.findPage`, `NotificationResourceIT`).
- `PUT /notifications/{id}/acknowledge`: one acknowledgement shared by all staff, the first one wins through a conditional `UPDATE`; an unknown id is 404 (`AcknowledgeNotificationService`, `NotificationJpaRepository.acknowledge`, `NotificationAcknowledgementIT`, [D13](decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation)).
- Role-based authorization, all three roles on both endpoints (`@RolesAllowed` on `NotificationResource`, `ZooRoles`, `NotificationSecurityIT`). OIDC in `%dev` and `%prod` with client `notification-service`, CORS, and a JSON body on 401 and 403 (`application.properties`, `SecurityExceptionMapper`, `UnauthorizedExceptionMapper`, `ForbiddenExceptionMapper`).
- The response hides `eventId` and `createdAt` (`NotificationResponse`).
- Idempotency by `eventId`: an existence check plus a unique constraint (`NotificationRepository.existsByEventId`, `uq_notifications_event_id`, `AnimalEventConsumerIT`).
- Severity and message rules per event type (`NotificationRule`, `NotificationRuleTest`).
- Dead-letter queue for any failed record (`application.properties`, `failure-strategy=dead-letter-queue`).
- Wire-format contract test against shared fixtures (`AnimalEventMessageContractTest`).

### Decided, not built
- Nothing currently recorded beyond what is implemented.

### Open
- Notification recipients and delivery channel. No recipient concept exists in code, and acknowledgement is one state shared by all staff, not per user.
- `GET /notifications` has no filter for acknowledged state, only `open` (`ListNotificationsService`).
- A transient database failure sends a valid event to the DLQ with no retry.
- Nothing reads, alerts on or replays the DLQ.
- Two copies of the same event processed at once: the second fails on the unique constraint and goes to the DLQ instead of being skipped.
- Payload fields are not validated, so missing ones appear as `null` in the message.

## feeding-service

### Implemented
- Create, get and paged list of feeding plans, with an optional `animalId` filter; the list is ordered by `startedOn`, newest first (`CreateFeedingPlanService`, `GetFeedingPlanService`, `ListFeedingPlansService`, `FeedingPlanResource`, `FeedingPlanJpaRepository`).
- Feeding plan lifecycle: `ACTIVE` → `SUSPENDED` or `ENDED`, `SUSPENDED` → `ACTIVE` or `ENDED`, with `ENDED` terminal (`FeedingPlan.canTransitionTo`, `PlanStatusTransitionTest`).
- Recording a feeding against an `ACTIVE` plan, and a paged list of a plan's feedings ordered by `fedAt`, newest first (`RecordFeedingService`, `ListFeedingsService`, `FeedingPlanResource`, `FeedingJpaRepository`).
- Feeding-time validation: at least one time, no nulls, no duplicates, whole minutes only, at most 6 (`CreateFeedingPlanService`); times are sorted before saving.
- A plan cannot be created for an animal already recorded as deceased (`CreateFeedingPlanService`, `DeceasedAnimalRepository.existsByAnimalId`).
- Role-based authorization per endpoint, with keepers recording feedings and vets/admins defining plans and their status (`@RolesAllowed` on `FeedingPlanResource`; `ZooRoles`; `FeedingSecurityIT`).
- Audit of the acting user: `createdBy`, `updatedBy` (`V1__create_feeding_tables.sql`, `FeedingPlanResource.currentActor`).
- Optimistic locking on feeding plans, with 409 on a conflict (`@Version` in `FeedingPlanEntity`, `FeedingPlanJpaRepository.save`, `ConcurrentFeedingPlanUpdateExceptionMapper`).
- Concurrency safety between plan creation, feeding recording, status changes and the Kafka consumer: a per-animal Postgres advisory lock (`AnimalLock`, `PostgresAnimalLock`) serializes `CreateFeedingPlanService` and `HandleAnimalEventService` on the same animal id, and pessimistic row locks (`FeedingPlanRepository.findByIdForUpdate`, `findByAnimalIdAndStatusInForUpdate`, `FeedingPlanJpaRepository`) serialize `RecordFeedingService` and `HandleAnimalEventService` against `UpdateFeedingPlanStatusService` on the same plan (`FeedingConcurrencyIT`).
- JSON error body on every error path (`ErrorResponse` and the `*ExceptionMapper` classes in `infrastructure/rest/`).
- Kafka consumer of `zoo.animal.events`, own consumer group `feeding-service`: on an `ANIMAL_STATUS_CHANGED` event with `newStatus = DECEASED`, records the animal in `deceased_animals` and ends every `ACTIVE`/`SUSPENDED` plan for it; `ANIMAL_REGISTERED`, `ANIMAL_TRANSFERRED` and any other new status are read and ignored (`AnimalEventConsumer`, `HandleAnimalEventService`, `V1__create_feeding_tables.sql`). Idempotent per animal id via `deceased_animals`, not per event id. Details: [events.md](events.md#consumer-idempotency-feeding-service).
- Dead-letter queue for any failed consumed record, on its own topic `zoo.animal.events.feeding.dlq` (`application.properties`, `failure-strategy=dead-letter-queue`).
- Wire-format contract test against shared fixtures (`AnimalEventMessageContractTest`).
- Prod profile configured from environment variables (`application.properties`, `%prod.*`).
- OpenAPI with a bearer scheme (`OpenApiConfig`).

### Decided, not built
- Nothing currently recorded beyond what is implemented.

### Open
- Feedings are append-only: a wrong record cannot be corrected or deleted, and no endpoint edits a plan's food, quantity or times (a new plan replaces it).
- No REST read of `deceased_animals`, or of which plans a consumer has auto-ended. No endpoint exists.
- No check that an animal id exists at all in `animal-service`: `CreateFeedingPlanService` only rejects ids already present in `deceased_animals`; any other UUID is accepted, the same eventual-consistency gap as `health-service`.
- Malformed JSON body on a feeding-plan write: there is no `JsonProcessingException` mapper and no test for it, the same open item as `health-service`.
- The animal-keyed idempotency of the Kafka consumer depends entirely on `animal-service` never allowing an animal to leave `DECEASED` (`Animal.canTransitionTo` makes it terminal). If that ever changed, a second legitimate `DECEASED` event for the same animal would be silently skipped. See [events.md, Not handled](events.md#not-handled).

## Cross-cutting

### Implemented
- Hexagonal layout, checked by `DomainPurityTest` in all four services (no Jakarta, Quarkus, Hibernate or MapStruct imports under `domain/`).
- Local infrastructure: four Postgres databases, Keycloak with realm `zoo`, single-node Kafka (`zms-be/infrastructure/docker-compose.yml`, `keycloak/realm-export.json`). The realm has a client for each of the four services and one for the frontend; the secret of `notification-service` is `${NOTIFICATION_SERVICE_CLIENT_SECRET}`, which compose fills from `NOTIFICATION_OIDC_CLIENT_SECRET` in `zms-be/infrastructure/.env`.
- Backend CI: `mvnw verify` for the four modules on push to `main` and on pull requests touching `zms-be/` (`.github/workflows/backend-ci.yml`).

### Open
- No frontend CI: the workflow only covers `zms-be/`.

## Frontend (zms-fe)

### Implemented
- Two build-time modes: demo (default) and live (`src/environments/environment.ts`, `environment.live.ts`, `angular.json`).
- Demo mode: in-memory APIs for animals, health, feeding and notifications with the same error statuses as the backend (`MockAnimalApi`, `MockHealthApi`, `MockFeedingApi`, `MockNotificationApi`, `demo-data.ts`, `demo-health.ts`, `demo-feeding.ts`, `demo-notifications.ts`, `enclosure-directory.ts`), and a role switcher persisted in `localStorage` (`DemoSession`). `MockAnimalApi` publishes each registration, status change and transfer to `DemoEventFeed`, and `MockNotificationApi` turns new events into notifications on its next read, with the severity rules of `NotificationRule` (`DemoEventFeed`).
- Live mode: Keycloak login with PKCE S256 and `check-sso`, token auto-refresh, bearer token sent only to the four service origins in `environment.api` (`animal`, `health`, `feeding`, `notification`), and a route guard that asks a visitor who is not signed in to log in (`keycloak-providers.ts` `bearerTokenConditions` and `signedInGuard`, `KeycloakSession`).
- Animal list grouped by enclosure, with client-side search by name, species or 4-character tag and a status filter (`AnimalList`).
- Animal detail, status change, transfer, and registration for admins (`AnimalDetail`, `StatusSheet`, `TransferSheet`, `RegisterSheet`).
- Health section on the animal page: medical records newest first, each opening onto its diagnosis and treatments; add a record, prescribe a treatment and change a treatment's status (`HealthSection`, `RecordSheet`, `TreatmentSheet`, `TreatmentStatusSheet`, `HealthStore`, `HttpHealthApi`).
- Feeding section on the animal page: today's meals as a meal track, the plan behind them, a log of recent feedings ten at a time with "Show earlier feedings", earlier plans; record a feeding, start a plan and suspend, resume or end it (`FeedingSection`, `MealTrack`, `FeedingSheet`, `PlanSheet`, `PlanStatusSheet`, `FeedingStore`, `HttpFeedingApi`).
- Meal states `fed`, `due`, `missed` and `later` per scheduled time, computed in the browser from the plan's times and the loaded feedings, with a one-hour window before a time and one hour of "due" after it (`meal-slots.ts`).
- Order of the sections by role: Activity first for every role; then keepers see Feeding, Status, Location, Health, Record, and vets and admins see Status and Location, then Health and Feeding, then Record ([D10](decisions.md#d10-health-and-feeding-live-inside-the-animal-page), [D13](decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation), `AnimalDetail`).
- Each of the Activity, Health and Feeding sections loads and fails on its own, with a "Try again" button (`ActivitySection`, `HealthSection`, `FeedingSection`, `ActivityStore.list`, `HealthStore.state`, `FeedingStore.state`).
- For a deceased animal, no prescribing and no starting a treatment (moving it to `ACTIVE`); a new medical record is still offered and an open treatment can still be completed or cancelled ([D11](decisions.md#d11-no-new-treatments-for-a-deceased-animal-frontend-only), `HealthSection`, `TreatmentStatusSheet`). `health-service` enforces the same rule with a 422 (see above, [D12](decisions.md#d12-health-service-enforces-d11-and-cancels-a-deceased-animals-open-treatments)), which `HttpHealthApi.toApiError` maps to fixed copy: `deceasedTreatment()` for a 422 on prescribe and `treatmentNotStartable()` for a 422 on a move to `ACTIVE` (`api-errors.ts`). In demo mode `MockHealthApi` imitates the `health-service` consumer: through `settleDeceased` it cancels a deceased animal's `PRESCRIBED` and `ACTIVE` treatments when records are listed or read or a treatment is prescribed or changed, and refuses prescribing and starting with a 422.
- For a deceased animal, the Feeding section offers neither recording a feeding nor starting a plan (`FeedingSection.canRecord`, `canStartPlan`). In demo mode `MockFeedingApi` ends the animal's active and suspended plans, as the `feeding-service` consumer does.
- Role-aware actions, using the same matrix as `AnimalResource`, `MedicalRecordResource`, `TreatmentResource`, `FeedingPlanResource` and `NotificationResource`; `acknowledgeNotification` is open to all three roles (`core/models/permissions.ts`).
- Full roster loaded by walking every page of `GET /animals` at size 100 (`HttpAnimalApi.listAll`); enclosures loaded from `GET /enclosures` (`HttpAnimalApi.listEnclosures`). An animal's medical records and feeding plans are loaded the same way (`HttpHealthApi.listRecords`, `HttpFeedingApi.listPlans`).
- HTTP status mapped to fixed user-facing error copy (`http-animal-api.ts`, `http-health-api.ts`, `http-feeding-api.ts` and `http-notification-api.ts` `toApiError`, `api-errors.ts`). A 422 on a status change and a 400 on a transfer re-read the animal to choose between the two possible messages (`HttpAnimalApi.explainRejection`).
- Notification data access: `GET /notifications` with the `animalId`, repeated `severity` and `open` filters, 20 per page, and `PUT /notifications/{id}/acknowledge` (`NotificationApi`, `HttpNotificationApi`, `NOTIFICATION_PAGE_SIZE`).
- The `/notifications` page, in two lists that load and fail on their own: Needs attention (open `WARNING` and `CRITICAL`) and Everything else. Everything else has two modes: Open (open `INFO`, asked of the server) and All (the full history, with the open `WARNING` and `CRITICAL` rows hidden in the browser). Acknowledging is offered to all three roles; the notification that comes back says who acknowledged first ([D13](decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation), `NotificationsPage`, `NotificationStore`, `NotificationList`).
- The bell in the top bar counts open `WARNING` and `CRITICAL` notifications. It is ink, and red while an open `CRITICAL` exists. The count is read again on every navigation and after an acknowledgement, and a failed read keeps the last count silently (`App.bell`, `NotificationStore.refreshCount`, `HttpNotificationApi.countOpen`, which takes the `total` of two one-item pages).
- Activity section at the top of the animal page for every role: open `WARNING` and `CRITICAL` notifications as expanded rows with Acknowledge, the rest as folded lines, 20 per page with "Show earlier". It reloads after a status change or transfer made on that page (`ActivitySection`, `ActivityStore`, `AnimalDetail.onStatusChanged`, `AnimalDetail.onMoved`).
- The sentence of a notification is written from its structured fields, with enclosure names; a row with a missing field shows the stored `message` (`notification-copy.ts`).
- Unit tests with Vitest (`*.spec.ts` under `src/app/`).

### Decided, not built
- Italian UI through Angular i18n ("prepared for Italian via Angular i18n", `PRODUCT.md`). No i18n setup exists in the code.

### Open
- The notification count has no push or polling. It is read on every navigation and after an acknowledgement, so a notification created while a page stays open shows only after the next one (`App`, `NotificationStore.refreshCount`, [D13](decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation)).
- In live mode a notification is created asynchronously: the outbox relay, Kafka and the consumer run after the change commits (`OutboxRelay`, `AnimalEventConsumer`). `ActivityStore.refresh` reads the list right after a status change or transfer, with no wait or retry (`Not handled`), so the new notification may be missing until the section is loaded again. In demo mode `MockNotificationApi` includes it at once.
- The Activity note counts only the loaded rows (`ActivityStore.needAttention`). Open `WARNING` and `CRITICAL` rows on a page not yet loaded are not counted.
- "Everything else" in All mode is filtered in the browser: the API has no filter for acknowledged rows, so the page loads the full history and hides the open `WARNING` and `CRITICAL` rows. It reads further pages when a whole page is hidden, and shows "Showing N so far" instead of a total (`NotificationStore.restItems`, `NotificationStore.fillRest`, `NotificationsPage.restCaption`).
- Enclosure names in notification sentences come from the static `ENCLOSURES` list in `enclosure-directory.ts`, in live mode too, and not from `GET /enclosures`. An id that is not in the list reads as "an unknown enclosure" (`notification-copy.ts` `enclosureName`).
- Search and filtering run in the browser over the full roster; the backend has no search endpoint.
- No view across animals, for example all feedings due now. Health and feeding are sections of one animal's page ([D10](decisions.md#d10-health-and-feeding-live-inside-the-animal-page)).
- Meal states use only the feedings loaded for a plan, which start as the newest page of 10 (`FEEDING_PAGE_SIZE`, `FeedingStore.loadFeedings`). A plan with more than 10 feedings on the current day would show the meals fed earlier that day as not recorded until "Show earlier feedings" loads them (`FeedingSection`, `mealSlots`).
- Meal states are the frontend's own reading of the plan: local time zone of the browser, fixed one-hour windows (`EARLY_MS`, `DUE_MS` in `meal-slots.ts`). `feeding-service` has no endpoint that reports missed meals (`FeedingPlanResource`).
- `FeedingStore` and `HealthStore` hold one animal and do not reload it while it stays the current one (`FeedingStore.load`, `HealthStore.load`). After a status change to `DECEASED`, nothing reloads the plans that `feeding-service` ends in response, so the section keeps showing them with their old status (without the meal track or Record feeding, which it hides for a deceased animal) until another animal is opened or the page is reloaded. `HealthStore` behaves the same way for the treatments `health-service` cancels: it does not reload the records or an already loaded record's treatments after the status change, so the section keeps showing them as `PRESCRIBED` or `ACTIVE` (without the prescribe and start actions, which it hides for a deceased animal) (`HealthStore.load`, `HealthStore.loadDetail`).
- Dashboard or home content (`PRODUCT.md`, "Open decisions").
