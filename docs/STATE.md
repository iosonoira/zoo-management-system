# Project state

Snapshot of what exists, what is decided but not built, and what is still open. Updated at the end of every phase.

Last updated: 2026-09-24, at commit `febdaf6`.

The three labels never mix:
- **Implemented**: in the code on `main`. Every line cites a class or file.
- **Decided, not built**: the maintainer recorded it as planned, but there is no code yet. Every line cites where the plan is recorded.
- **Open**: nobody has decided yet, or the code leaves the scenario unhandled.

Contracts and failure behaviour per service are in the service READMEs. Events are in [events.md](events.md), and decisions are in [decisions.md](decisions.md).

## animal-service

### Implemented
- Register, get, paged list, status change and transfer of animals (`RegisterAnimalService`, `GetAnimalService`, `ListAnimalsService`, `UpdateAnimalStatusService`, `TransferAnimalService`, `AnimalResource`).
- Status rules: `DECEASED` is terminal, a change to the same status is rejected, and a `DECEASED` animal cannot be transferred (`Animal.canTransitionTo`, `Animal.canBeTransferred`).
- Paging on `GET /animals`: `page` ≥ 0, `size` 1–100, ordered by name then id (`ListAnimalsService`, `AnimalPanacheRepository.findPage`).
- Enclosures as a persisted, read-only table, listed by `GET /enclosures` (`V5__create_enclosures_table.sql`, `EnclosureResource`, `ListEnclosuresService`).
- Unknown enclosure rejected on register and transfer (`UnknownEnclosureException`, `EnclosureRepository.existsById`).
- Dev seed data for animals and enclosures (`db/dev/R__seed_demo_animals.sql`, `db/dev/R__seed_demo_enclosures.sql`).
- Role-based authorization per endpoint (`@RolesAllowed` on `AnimalResource`, `EnclosureResource`; `ZooRoles`; `AnimalSecurityIT`).
- Audit of the acting user: `createdBy`, `updatedBy` (`V2__add_audit_columns.sql`, `AnimalResource.currentActor`).
- Optimistic locking, with 409 on a conflict (`V3__add_version_column.sql`, `AnimalPanacheRepository.save`, `ConcurrentAnimalUpdateExceptionMapper`).
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
- Create, get and paged list of medical records, with an optional `animalId` filter; the list is ordered by examination date, newest first (`CreateMedicalRecordService`, `GetMedicalRecordService`, `ListMedicalRecordsService`, `MedicalRecordResource`, `MedicalRecordPanacheRepository`).
- Prescribing treatments and changing their status (`PrescribeTreatmentService`, `UpdateTreatmentStatusService`, `TreatmentResource`).
- Treatment lifecycle: `PRESCRIBED` → `ACTIVE` or `CANCELLED`, `ACTIVE` → `COMPLETED` or `CANCELLED`, with the last two terminal (`Treatment.canTransitionTo`, `TreatmentStatusTransitionTest`).
- Examination date not in the future (`CreateMedicalRecordService`).
- Role-based authorization per endpoint (`@RolesAllowed` on `MedicalRecordResource`, `TreatmentResource`; `HealthSecurityIT`).
- Audit columns and optimistic locking (`V1__create_medical_records_and_treatments.sql`, `TreatmentPanacheRepository.save`).

### Decided, not built
- Consume animal events, for example cancelling treatments when an animal becomes `DECEASED` (next-phases list in `zms-be/CLAUDE.md` at commit `febdaf6`).

### Open
- Eventual consistency with `animal-service`. Confirmed in code: nothing checks that the animal exists or is not `DECEASED` before a medical record is created or a treatment is prescribed or activated (`CreateMedicalRecordService`, `PrescribeTreatmentService`, `UpdateTreatmentStatusService`). There is no call to `animal-service` and no local copy of animal data.
- Malformed JSON body: there is no `JsonProcessingException` mapper and no test for it.
- Treatments in a record's detail are ordered by a random UUID (`TreatmentPanacheRepository.findByMedicalRecordId`).
- No update or delete of a medical record, although `ConcurrentMedicalRecordUpdateException` is mapped to 409.

## notification-service

### Implemented
- Kafka consumer of `zoo.animal.events` that stores one notification per event (`AnimalEventConsumer`, `HandleAnimalEventService`, `V1__create_notifications_table.sql`).
- Idempotency by `eventId`: an existence check plus a unique constraint (`NotificationRepository.existsByEventId`, `uq_notifications_event_id`, `AnimalEventConsumerIT`).
- Severity and message rules per event type (`NotificationRule`, `NotificationRuleTest`).
- Dead-letter queue for any failed record (`application.properties`, `failure-strategy=dead-letter-queue`).
- Wire-format contract test against shared fixtures (`AnimalEventMessageContractTest`).

### Decided, not built
- REST API and UI for notifications (next-phases list in `zms-be/CLAUDE.md` at commit `febdaf6`).

### Open
- Notification recipients and delivery channel. No recipient concept exists in code.
- A transient database failure sends a valid event to the DLQ with no retry.
- Nothing reads, alerts on or replays the DLQ.
- Two copies of the same event processed at once: the second fails on the unique constraint and goes to the DLQ instead of being skipped.
- Payload fields are not validated, so missing ones appear as `null` in the message.

## feeding-service

### Implemented
- Nothing. `zms-be/feeding-service/` is an empty folder, and the module is not listed in `zms-be/pom.xml`.

### Decided, not built
- Feeding plans, to be implemented from scratch (overview and next-phases list in `zms-be/CLAUDE.md` at commit `febdaf6`).

### Open
- Scope: no entity, endpoint, event or role for feeding is defined in any document.

## Cross-cutting

### Implemented
- Hexagonal layout, checked by `DomainPurityTest` in all three services (no Jakarta, Quarkus, Hibernate or MapStruct imports under `domain/`).
- Local infrastructure: three Postgres databases, Keycloak with realm `zoo`, single-node Kafka (`zms-be/infrastructure/docker-compose.yml`, `keycloak/realm-export.json`).
- Backend CI: `mvnw verify` for the three modules on push to `main` and on pull requests touching `zms-be/` (`.github/workflows/backend-ci.yml`).

### Open
- Local env setup: `infrastructure/`, `animal-service/` and `health-service/` no longer contain an `env.example` (removed in `15ba49f`), but `docker-compose.yml` errors and the READMEs still tell users to copy one.
- No frontend CI: the workflow only covers `zms-be/`.

## Frontend (zms-fe)

### Implemented
- Two build-time modes: demo (default) and live (`src/environments/environment.ts`, `environment.live.ts`, `angular.json`).
- Demo mode: in-memory API with the same error statuses as the backend (`MockAnimalApi`, `demo-data.ts`, `enclosure-directory.ts`), and a role switcher persisted in `localStorage` (`DemoSession`).
- Live mode: Keycloak login with PKCE S256 and `check-sso`, token auto-refresh, bearer token sent only to the API origin (`keycloak-providers.ts`, `KeycloakSession`).
- Animal list grouped by enclosure, with client-side search by name, species or 4-character tag and a status filter (`AnimalList`).
- Animal detail, status change, transfer, and registration for admins (`AnimalDetail`, `StatusSheet`, `TransferSheet`, `RegisterSheet`).
- Role-aware actions, using the same matrix as `AnimalResource` (`core/models/permissions.ts`).
- Full roster loaded by walking every page of `GET /animals` at size 100 (`HttpAnimalApi.listAll`); enclosures loaded from `GET /enclosures` (`HttpAnimalApi.listEnclosures`).
- HTTP status mapped to fixed user-facing error copy (`http-animal-api.ts` `toApiError`, `api-errors.ts`).
- Unit tests with Vitest (`*.spec.ts` under `src/app/`).

### Decided, not built
- Italian UI through Angular i18n ("prepared for Italian via Angular i18n", `PRODUCT.md`). No i18n setup exists in the code.
- Navigation that leaves room for health, feeding and notifications (`PRODUCT.md`).

### Open
- No UI for `health-service` or notifications. The frontend calls only `/animals` and `/enclosures`.
- Search and filtering run in the browser over the full roster; the backend has no search endpoint.
- In live mode, any 400 on a transfer is shown as the "deceased animal" message, although the backend also returns 400 for an unknown enclosure (`toApiError`).
- In live mode, any 422 on a status change is shown as the "same status" message, although the backend also returns 422 for a change from `DECEASED` (`toApiError`).
- Dashboard or home content (`PRODUCT.md`, "Open decisions").
