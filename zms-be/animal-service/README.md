# animal-service

Registry of the zoo's animals and read-only directory of enclosures. Publishes animal events. Dev port 8080, database `animal_db` on :5432. How to run it: [zms-be/README.md](../README.md). Design decisions: [docs/decisions.md](../../docs/decisions.md).

## 1. Responsibility and boundaries

Owns:
- The `animals` table: identity, name, species, dangerous flag, habitat, current enclosure, arrival date, status, audit columns (`V1`, `V2`, `V3` migrations, `AnimalEntity`).
- The `enclosures` table: id, name, habitat (`V5__create_enclosures_table.sql`, `EnclosureEntity`).
- The `outbox_event` table and the publication of animal events to Kafka (`OutboxAnimalEventPublisher`, `OutboxRelay`).

Does not:
- Store medical data. That is `health-service`, which is not called by this service.
- Create, rename or delete enclosures. Rows come only from Flyway scripts: `db/dev/R__seed_demo_enclosures.sql` in the dev profile and `db/test/R__seed_test_enclosures.sql` in the test profile. The prod profile loads neither (`application.properties`).
- Delete animals or edit name, species, habitat, dangerous flag or arrival date after registration. No endpoint exists for these.
- Search or filter animals. `GET /animals` only pages (`ListAnimalsService`).

## 2. Domain rules

| Rule | Enforced in | Result when violated |
|---|---|---|
| A new animal starts as `HEALTHY` | `RegisterAnimalService` | — |
| `DECEASED` is terminal: no further status change | `Animal.canTransitionTo` | 422 (`InvalidStatusTransitionException`) |
| A status change to the current status is rejected | `Animal.canTransitionTo` | 422 |
| Any other status change is allowed, in any direction (e.g. `IN_TREATMENT` → `HEALTHY`) | `Animal.canTransitionTo` | — |
| A `DECEASED` animal cannot be transferred | `Animal.canBeTransferred`, `TransferAnimalService` | 400 (`InvalidAnimalDataException`) |
| The enclosure must exist on register and transfer | `RegisterAnimalService`, `TransferAnimalService` via `EnclosureRepository` | 400 (`UnknownEnclosureException`) |
| Name, species, habitat, enclosure id, arrival date and acting user are required | `RegisterAnimalService` (explicit checks), `RegisterAnimalRequest` (Bean Validation) | 400 |
| Name and species are at most 100 characters | `RegisterAnimalRequest` | 400 |
| Page ≥ 0, 1 ≤ size ≤ 100 | `ListAnimalsService`, `ListAnimalsUseCase.MAX_PAGE_SIZE` | 400 |
| Concurrent writes to the same animal: the second one fails | `@Version` on `AnimalEntity`, `AnimalPanacheRepository` | 409 (`ConcurrentAnimalUpdateException`) |

Not checked: the animal's habitat against the enclosure's habitat; a transfer to the enclosure the animal is already in (accepted and emits an event); the arrival date being in the future.

## 3. Contracts

All endpoints need a bearer token. Roles are Keycloak realm roles read from `realm_access/roles` (`application.properties`, `ZooRoles`). OIDC is off in the test profile.

| Method and path | Roles (`@RolesAllowed`) | Success | Class |
|---|---|---|---|
| `POST /animals` | `zoo-admin` | 201, animal | `AnimalResource.register` |
| `GET /animals?page=&size=` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, page envelope `{items, page, size, total}`, ordered by name then id | `AnimalResource.list` |
| `GET /animals/{id}` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, animal | `AnimalResource.getById` |
| `PUT /animals/{id}/status` | `zoo-vet`, `zoo-admin` | 200, animal | `AnimalResource.updateStatus` |
| `PUT /animals/{id}/transfer` | `zoo-keeper`, `zoo-admin` | 200, animal | `AnimalResource.transfer` |
| `GET /enclosures` | `zoo-admin`, `zoo-vet`, `zoo-keeper` | 200, full list ordered by name, no paging | `EnclosureResource.list` |

Errors share the body `{"message": ...}` (`ErrorResponse`). 401 and 403 come from `SecurityExceptionMapper`, `UnauthorizedExceptionMapper`, `ForbiddenExceptionMapper`. The acting user (`createdBy`, `updatedBy`, event `performedBy`) is the token's principal name (`AnimalResource.currentActor`).

Events produced on `zoo.animal.events`: `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED`, `ANIMAL_TRANSFERRED`. Payloads, emission conditions and delivery semantics: [docs/events.md](../../docs/events.md).

Events consumed: none.

## 4. Failure behaviour

| Scenario | What the code does |
|---|---|
| Broker unavailable | REST writes still commit. Events stay in `outbox_event` and the relay retries the oldest unpublished row every interval (`OutboxRelay`). See [docs/events.md](../../docs/events.md#relay-outboxrelay) |
| Duplicate event | Not a consumer. The relay can publish the same event more than once, with the same `eventId` ([docs/events.md](../../docs/events.md#duplicates)). Duplicate REST requests are not deduplicated: two identical `POST /animals` create two animals |
| Unreadable record | Not a consumer. Unreadable request body: 400 `Malformed request body` (`JsonProcessingExceptionMapper`). Unknown enum value: 400. Path id that is not a UUID: 404 (`WebApplicationExceptionMapper`). Covered in `AnimalResourceIT` |
| DB rejection | Optimistic-lock conflict: 409 (`AnimalPanacheRepository`, `ConcurrentAnimalUpdateExceptionMapper`). Any other database error: 500 `Internal server error`, logged (`UnexpectedExceptionMapper`); the transaction rolls back, outbox row included |
| Unknown reference | Unknown animal id: 404 (`AnimalNotFoundException`). Unknown enclosure id on register or transfer: 400 (`UnknownEnclosureException`). `animals.enclosure_id` has no foreign key (`V5`), so an enclosure row removed directly in the database leaves animals pointing to it: `Not handled` |

## 5. Open questions

- How are enclosures provisioned outside the dev and test profiles? The prod profile loads no enclosure script and no endpoint creates them.
- Should a transfer to the current enclosure be rejected? Today it succeeds and emits `ANIMAL_TRANSFERRED` with equal from and to ids.
- Should the animal's habitat have to match the enclosure's habitat? Both are stored; nothing compares them.
