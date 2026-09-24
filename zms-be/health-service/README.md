# health-service

Medical records and treatments of the zoo's animals. Dev port 8082, database `health_db` on :5433. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `medical_records` table: animal id, reason, diagnosis, examination date, veterinarian, audit columns (`V1__create_medical_records_and_treatments.sql`, `MedicalRecordEntity`).
- The `treatments` table: medical record id (foreign key), description, status, start and end dates, audit columns (same migration, `TreatmentEntity`).

Does not:
- Know anything about animals beyond their id. There is no call to `animal-service` and no local copy of animal data (`CreateMedicalRecordService`).
- Produce or consume events. There is no messaging dependency (`pom.xml`). See [docs/events.md](../../docs/events.md).
- Edit or delete a medical record, or edit a treatment's description. No endpoint exists for these.

## 2. Domain rules

| Rule | Enforced in | Result when violated |
|---|---|---|
| A new treatment starts as `PRESCRIBED` | `PrescribeTreatmentService` | — |
| Allowed transitions: `PRESCRIBED` → `ACTIVE` or `CANCELLED`; `ACTIVE` → `COMPLETED` or `CANCELLED` | `Treatment.canTransitionTo` | 422 (`InvalidTreatmentStatusTransitionException`) |
| `COMPLETED` and `CANCELLED` are terminal | `Treatment.canTransitionTo` | 422 |
| A transition to the current status, or to null, is rejected | `Treatment.canTransitionTo` | 422 |
| `startedOn` is set to today the first time a treatment becomes `ACTIVE`; `endedOn` is set to today on `COMPLETED` or `CANCELLED` | `UpdateTreatmentStatusService` | — |
| The examination date must not be in the future | `CreateMedicalRecordService` | 400 (`InvalidMedicalDataException`) |
| Animal id, reason, diagnosis, examination date, veterinarian and acting user are required | `CreateMedicalRecordService`, `CreateMedicalRecordRequest` | 400 |
| Reason ≤ 200, diagnosis ≤ 1000, veterinarian ≤ 100, treatment description ≤ 500 characters | `CreateMedicalRecordRequest`, `PrescribeTreatmentRequest` | 400 |
| A treatment needs an existing medical record | `PrescribeTreatmentService`, foreign key in `V1` | 404 (`MedicalRecordNotFoundException`) |
| Page ≥ 0, 1 ≤ size ≤ 100 | `ListMedicalRecordsService`, `ListMedicalRecordsUseCase.MAX_PAGE_SIZE` | 400 |
| Concurrent writes to the same treatment: the second one fails | `@Version`, `TreatmentPanacheRepository` | 409 (`ConcurrentTreatmentUpdateException`) |

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

Errors share the body `{"message": ...}` (`ErrorResponse`); 401 and 403 come from `SecurityExceptionMapper`. The authorization matrix is covered by `HealthSecurityIT`.

Events produced: none. Events consumed: none.

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | Not applicable: the service does not use Kafka |
| Duplicate event | Not applicable. Duplicate REST requests are not deduplicated: two identical `POST /medical-records` create two records |
| Unreadable record | Path id that is not a UUID: 404 (`MedicalRecordResourceIT.shouldReturn404WhenIdIsNotAUuid`). Other `WebApplicationException`s keep the framework's response (`UnexpectedExceptionMapper`). Malformed JSON body: there is no `JsonProcessingException` mapper, unlike `animal-service`, and no test covers it |
| DB rejection | Optimistic-lock conflict on a treatment: 409. Any other database error: 500 `Internal server error`, logged (`UnexpectedExceptionMapper`); the transaction rolls back |
| Unknown reference | Unknown medical record id: 404 on read and on prescribe. Unknown treatment id: 404. Unknown `animalId`: `Not handled`. A record is created for any UUID, including an animal that does not exist or is `DECEASED`; listing by an unknown `animalId` returns an empty page |

## 5. Open questions

- Should creating a medical record, or prescribing or activating a treatment, be refused for an unknown or `DECEASED` animal? Nothing checks it today.
- What should happen to open treatments when an animal becomes `DECEASED`? `health-service` does not consume `ANIMAL_STATUS_CHANGED`.
- What does a malformed JSON body return? In `animal-service` the same case needed a dedicated mapper to avoid a 500; here there is none and no test.
- Treatments in `GET /medical-records/{id}` are ordered by id, which is a random UUID (`TreatmentPanacheRepository`). Is a date order expected?
- `ConcurrentMedicalRecordUpdateException` is mapped to 409, but no use case updates a medical record. Is a record update planned?
