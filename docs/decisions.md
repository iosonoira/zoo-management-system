# Decisions

Append-only log of design decisions. Add new entries at the bottom and never edit old ones. When a decision is reversed, append a new entry that names the one it replaces.

Only the maintainer adds decisions. If a reason is not written down anywhere, the entry says `Rationale: not recorded` rather than guessing one.

Each entry has: Date · Service(s) · Context · Alternatives considered · Decision · Consequences. The date is the first commit where the decision shows up in code or docs.

The entries below were seeded on 2026-09-24 from decisions already stated in `zms-be/CLAUDE.md`, `zms-fe/README.md`, `PRODUCT.md` and the code.

---

## D1. Hexagonal (ports and adapters) layout

- **Date**: 2026-06-28 (first recorded in `zms-be/CLAUDE.md`, commit `0c78602`)
- **Service(s)**: animal-service, health-service, notification-service
- **Context**: Every backend service is split into `domain/`, `application/` and `infrastructure/` (`zms-be/CLAUDE.md`, "Architecture").
- **Alternatives considered**: not recorded.
- **Decision**: Dependencies point one way only, `infrastructure → application → domain`. `domain/` has no framework annotations. There is one application service per use case, and the application layer knows only the `port/in` and `port/out` interfaces (`zms-be/CLAUDE.md`).
- **Rationale**: not recorded.
- **Consequences**:
  - `DomainPurityTest` fails the build of each service if a file under `domain/` imports `jakarta.`, `io.quarkus`, `org.hibernate` or `org.mapstruct`.
  - Application services still use `@ApplicationScoped` and `@Transactional`, which `zms-be/CLAUDE.md` explicitly allows.

## D2. Repository pattern instead of Active Record

- **Date**: 2026-06-28 (commits `cf10ae6` and `ed04eca`; rule recorded in `zms-be/CLAUDE.md`, commit `0c78602`)
- **Service(s)**: animal-service, health-service, notification-service
- **Context**: The domain defines its own repository ports (`port/out`), whose methods take and return domain objects, such as `Optional<Animal> findById(UUID)`. Quarkus Panache offers two styles: Active Record (`extends PanacheEntity`) and Repository (`implements PanacheRepository<E>`).
- **Alternatives considered**:
  - Active Record (`extends PanacheEntity`): puts persistence on the entity (`zms-be/CLAUDE.md`).
  - An adapter that implements both `PanacheRepositoryBase<E, ID>` and the domain port. Tried first (`cf10ae6`) and dropped in `ed04eca`.
  - Renaming the port methods to avoid the clash. Not taken: the domain would change shape to suit the framework.
- **Decision**: Use the Repository pattern. JPA entities live only in `infrastructure/persistence/`, and the application layer works on domain objects. Each repository adapter implements only the domain port and works through an injected `EntityManager` (`{Entity}JpaRepository`).
- **Rationale**: Panache's `findById(ID)` returns the entity and `findAll()` returns a `PanacheQuery`, while the port's methods with the same names and parameters return domain types. A class cannot declare both, so an adapter implementing both interfaces does not compile (commit message of `ed04eca`).
- **Consequences**:
  - No class extends `PanacheEntity`.
  - The five adapters (`AnimalJpaRepository`, `EnclosureJpaRepository`, `MedicalRecordJpaRepository`, `TreatmentJpaRepository`, `NotificationJpaRepository`) write their own JPQL instead of using Panache helpers.
  - `OutboxEventRepository` implements no domain port, so it uses `PanacheRepositoryBase`. It is the only Panache user, so only `animal-service` depends on `quarkus-hibernate-orm-panache`. `health-service` and `notification-service` depend on `quarkus-hibernate-orm`.
  - Each entity has a hand-written mapper to and from the domain model (`*EntityMapper`).

## D3. Transactional outbox for animal events

- **Date**: 2026-09-23 (commit `7a7417a`)
- **Service(s)**: animal-service, notification-service
- **Context**: Write use cases in `animal-service` emit domain events that other services consume through Kafka.
- **Alternatives considered**: sending through a Kafka emitter directly from `application/`, which `zms-be/CLAUDE.md` forbids.
- **Decision**: Events are published only through the `AnimalEventPublisher` port. Its adapter writes to `outbox_event` in the caller's transaction, and a scheduled relay sends the rows to Kafka (`zms-be/CLAUDE.md`; `OutboxAnimalEventPublisher`, `OutboxRelay`).
- **Rationale**: not recorded.
- **Consequences**:
  - Delivery is at-least-once, so every consumer must be idempotent (`zms-be/CLAUDE.md`). `notification-service` deduplicates by `eventId`.
  - Published rows are never deleted.
  - Details and unhandled cases are in [events.md](events.md).

## D4. No foreign key from animals to enclosures

- **Date**: 2026-09-23 (commit `1a6a3bd`)
- **Service(s)**: animal-service
- **Context**: Enclosures became a persisted table (`V5__create_enclosures_table.sql`) while `animals.enclosure_id` already existed.
- **Alternatives considered**: a foreign key on `animals.enclosure_id`, implied by the note "no FK, intentional" (Italian original: "nessuna FK intenzionale") in `zms-be/CLAUDE.md` at commit `febdaf6`.
- **Decision**: No foreign key. Unknown enclosures are rejected in the application layer through the `EnclosureRepository` port, with a 400 (`RegisterAnimalService`, `TransferAnimalService`, `UnknownEnclosureException`).
- **Rationale** (`zms-be/CLAUDE.md` at commit `febdaf6`): V5 runs before the repeatable seed scripts, and existing dev databases already contain animals.
- **Consequences**:
  - The database does not enforce the reference. An enclosure row removed by hand leaves animals pointing to a missing enclosure.
  - Integration tests need a separate Flyway location with test enclosures (`db/test/R__seed_test_enclosures.sql`).

## D5. Role matrix

- **Date**: 2026-09-19 for animal-service (commit `243d77e`); 2026-09-21 for health-service (commit `e6adca7`)
- **Service(s)**: animal-service, health-service, zms-fe
- **Context**: There are three Keycloak realm roles: `zoo-admin`, `zoo-vet` and `zoo-keeper` (`ZooRoles`, `realm-export.json`).
- **Alternatives considered**: not recorded.
- **Decision**: Authorization is set per method with `@RolesAllowed`.
  - animal-service: register is admin only; reads are open to all three roles; status change is vet and admin; transfer is keeper and admin (`AnimalResource`; `zms-be/CLAUDE.md` at commit `febdaf6`).
  - health-service: reads are open to all three roles; writes are vet and admin (`MedicalRecordResource`, `TreatmentResource`).
- **Rationale** (`PRODUCT.md`, "Positioning"): "permissions follow the job (keepers transfer, vets change clinical status, admins register)". No rationale is recorded for the health-service matrix.
- **Consequences**:
  - The frontend repeats the animal-service matrix by hand in `core/models/permissions.ts`; nothing checks that the two copies stay in sync.
  - The matrices are covered by `AnimalSecurityIT` and `HealthSecurityIT`.

## D6. Demo mode as the frontend default

- **Date**: 2026-09-21 (build-time mode flag, commit `31d5dae`)
- **Service(s)**: zms-fe
- **Context**: The frontend can run on in-memory data or against the real backend and Keycloak.
- **Alternatives considered**: live mode as the default (the other build configuration, `angular.json` `live`).
- **Decision**: `pnpm start` and `pnpm build` produce the demo mode. Live mode is selected with the `live` configuration, which swaps `environment.ts` for `environment.live.ts` (`angular.json`, `zms-fe/README.md`).
- **Rationale** (`zms-fe/README.md`): the app must work for someone opening it cold, with no backend and no account. The original wording referred to a "portfolio build"; no build is published.
- **Consequences**:
  - `MockAnimalApi` has to follow the backend contract, including error statuses, so the UI behaves the same in both modes (`zms-fe/README.md`).
  - Demo mode uses a role switcher (`DemoSession`) instead of a login.

## D7. Keycloak login with PKCE in the frontend

- **Date**: 2026-09-21 (commit `94a7e27`)
- **Service(s)**: zms-fe, infrastructure
- **Context**: In live mode the frontend must obtain a token for the backend services.
- **Alternatives considered**: not recorded.
- **Decision**:
  - The frontend signs in through the Keycloak public client `zms-fe`, using the authorization code flow with PKCE `S256` (`keycloak-providers.ts`, `realm-export.json`).
  - It uses `onLoad: 'check-sso'`, so an anonymous visitor reaches the shell and the route guard triggers the login (comment in `keycloak-providers.ts`).
- **Rationale**: not recorded for PKCE. The reason for `check-sso` is the code comment above.
- **Consequences**:
  - The `zms-fe` client accepts redirects only to `http://localhost:4200/*` (`realm-export.json`).
  - The bearer token is attached only to requests to `environment.apiBaseUrl` (`keycloak-providers.ts`).
  - Keycloak code is loaded only in the browser and only in live mode (`main.ts`, `app.routes.ts`).

## D8. One exception mapper per exception

- **Date**: 2026-09-20 for animal-service (commit `c85635b`); 2026-09-21 for health-service (commit `e6adca7`)
- **Service(s)**: animal-service, health-service
- **Context**: Each service turns exceptions into HTTP responses with the body `{"message": ...}`. Until `c85635b`, animal-service did it with a single `ZooExceptionMapper` on `RuntimeException` (commit `042e651`), which chose the status with `instanceof` checks and returned 500 for anything else.
- **Alternatives considered**: the single `ZooExceptionMapper` on `RuntimeException`, replaced by this decision.
- **Decision**:
  - One `ExceptionMapper` per domain exception (`AnimalNotFoundExceptionMapper`, `InvalidAnimalDataExceptionMapper`, …).
  - A catch-all `UnexpectedExceptionMapper` on `Exception` that logs at ERROR and returns 500 `Internal server error`.
  - `WebApplicationException` keeps the status the framework gave it: a dedicated `WebApplicationExceptionMapper` in animal-service, a pass-through of `getResponse()` in the health-service catch-all.
- **Rationale** (commit message of `c85635b`): because `ZooExceptionMapper` mapped `RuntimeException`, it also caught the framework's `WebApplicationException`. A malformed UUID in the path answered 500 instead of 404, and an unparseable body 500 instead of 400. It also returned 500 without logging anything.
- **Consequences**:
  - Adding a domain exception means adding its mapper. Without one it reaches the catch-all and answers 500.
  - `ErrorResponse` lives in `rest/dto/` and is shared by every mapper.
  - The 404 on a malformed path id is covered in `AnimalResourceIT` and `MedicalRecordResourceIT`.
  - animal-service maps an unparseable body to 400 (`JsonProcessingExceptionMapper`). health-service has no such mapper (`zms-be/health-service/README.md`).

## D9. feeding-service scope, roles and deceased-animal read model

- **Date**: 2026-09-27 (commits `d774c6d`..`21368c7`)
- **Service(s)**: feeding-service, animal-service (as event producer)
- **Context**: `feeding-service` was planned but its scope was not defined in any document: no entity, endpoint, event or role (`docs/STATE.md` at commit `b4c99e4`). It is the second service that refers to animals owned by `animal-service`, after `health-service` stored the animal id as an opaque UUID.
- **Alternatives considered**:
  - Scope: feeding plans only; or plans, feedings and a computed "due today" list.
  - Events: no Kafka integration, with the animal id kept opaque as in `health-service`.
  - Roles: plans and feedings both written by keepers and admins; or plans by admins only and feedings by all three roles.
- **Decision**:
  - Feeding plans (food, grams, 1 to 6 daily times, `ACTIVE`/`SUSPENDED`/`ENDED`) plus an append-only feeding log. Recurrence is stored as data; there is no scheduler.
  - The service consumes `ANIMAL_STATUS_CHANGED` with `newStatus = DECEASED` from `zoo.animal.events`: it ends the animal's `ACTIVE` and `SUSPENDED` plans and rejects new plans for that animal with 422.
  - Plans are written by `zoo-vet` and `zoo-admin`; feedings by `zoo-keeper` and `zoo-admin`; reads are open to all three roles.
  - Deceased animals are kept in a local table, `deceased_animals`, filled by the consumer. There is no call to `animal-service`.
- **Rationale** (given by the maintainer, 2026-09-27, who chose these options as the most logical ones proposed during planning):
  - Plans plus a feeding log keep the service the same size as `health-service` and give keepers an action of their own; stored times avoid scheduling and time-zone logic.
  - Consuming `DECEASED` makes feeding-service the first consumer with a business effect, reusing the consumer pattern of `notification-service`.
  - Permissions follow the job: a diet is a clinical choice, feeding an animal is the keeper's work (`PRODUCT.md`, "Positioning").
  - A local read model fed by events is the direction named after the opaque-UUID choice for `health-service`, and keeps the two services independent at runtime.
- **Consequences**:
  - A plan can still be created for an animal id that does not exist in `animal-service`; only ids already in `deceased_animals` are rejected.
  - Idempotency of the consumer is per animal id, not per event id, and relies on `DECEASED` being terminal in `Animal.canTransitionTo` (`docs/events.md`).
  - The consumer has its own dead-letter topic, `zoo.animal.events.feeding.dlq`.
  - The frontend repeats no feeding matrix yet: there is no feeding UI.
