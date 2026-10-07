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

## D10. Health and feeding live inside the animal page

- **Date**: 2026-09-29 (commit `c9ed49e`)
- **Service(s)**: zms-fe
- **Context**: `health-service` and `feeding-service` had no UI (`docs/STATE.md`, Frontend). `PRODUCT.md` asked the navigation to "leave room" for them but did not say where they go.
- **Alternatives considered**:
  - Global sections `/health` and `/feeding` in a new navigation, with lists across animals.
  - Both: sections in the animal page plus global lists.
  - Inside the animal page, as tabs (Overview, Feeding, Health) rather than stacked sections. Both layouts were drawn as a mockup before any code.
- **Decision**:
  - Feeding and Health are sections of `/animals/:id`, stacked with Status, Location and Record. There is no new navigation.
  - The order depends on the role: keepers see Feeding first; vets and admins see Status and Location first, then Health and Feeding side by side from 60rem.
- **Rationale** (given by the maintainer, 2026-09-29, who adopted the reasons proposed during planning and chose the stacked layout from the mockup):
  - The product is centred on the animal (`PRODUCT.md`, "Product Purpose"), and both APIs filter by `animalId` without a cross-animal list by status, so global sections would be unfiltered lists.
  - Stacked sections keep the page's existing grammar and show a reviewer the whole feature without a click; Feeding leads for keepers because recording a meal is their most frequent action.
- **Consequences**:
  - The bearer token is now attached to all three service origins in `environment.api` (`keycloak-providers.ts`, `bearerTokenConditions`). This replaces the D7 consequence that named `environment.apiBaseUrl`.
  - When `health-service` or `feeding-service` is down in live mode, only its section shows an error; the rest of the page keeps working.
  - There is still no view across animals, for example all feedings due now.

## D11. No new treatments for a deceased animal, frontend only

- **Date**: 2026-09-29 (commit `3426352`)
- **Service(s)**: zms-fe
- **Context**: `health-service` does not check the animal at all: a medical record can be created, and a treatment prescribed or started, for an animal that is `DECEASED` or does not exist (`docs/STATE.md`, health-service, Open).
- **Alternatives considered**:
  - Follow the backend: allow everything in the UI and leave the gap documented.
  - Make Health read-only for a deceased animal: no new records, treatments or status changes.
- **Decision**:
  - For a deceased animal the UI still allows a new medical record, but does not offer prescribing a treatment or starting one (moving it to `ACTIVE`). Completing or cancelling an open treatment stays possible.
  - Vets and admins see the reason as a lock note in the Health section.
- **Rationale** (given by the maintainer, 2026-09-29, who chose this option as proposed during planning): a post-mortem examination is a legitimate record for a deceased animal, a new treatment is not.
- **Consequences**:
  - The rule lives only in the frontend (`HealthSection`, `TreatmentStatusSheet`). A direct call to `health-service` can still prescribe or start a treatment for a deceased animal.
  - Enforcing it in the backend needs `health-service` to know which animals are deceased, for example with the event consumer and read model that D9 introduced in `feeding-service`.

## D12. health-service enforces D11 and cancels a deceased animal's open treatments

- **Date**: 2026-09-30 (commits `df3735b`..`ca603fa`)
- **Service(s)**: health-service, zms-fe
- **Context**: D11 kept the "no new treatment for a deceased animal" rule in the frontend only, and named the consumer and read model of D9 as the way to enforce it in the backend. `docs/STATE.md` listed "consume animal events, for example cancelling treatments when an animal becomes DECEASED" as decided but not built.
- **Alternatives considered**:
  - On `DECEASED`, cancel only `PRESCRIBED` treatments and leave `ACTIVE` ones for a vet to complete or cancel.
  - On `DECEASED`, change no treatment: record the animal and only refuse new treatments and starts.
  - Keep `health-service` unchanged and the rule frontend-only.
- **Decision**:
  - `health-service` consumes `zoo.animal.events` (consumer group `health-service`, dead-letter topic `zoo.animal.events.health.dlq`). On `ANIMAL_STATUS_CHANGED` with `newStatus = DECEASED` it records the animal in its own `deceased_animals` table and moves every `PRESCRIBED` and `ACTIVE` treatment of the animal to `CANCELLED`.
  - Prescribing a treatment, or moving one to `ACTIVE`, for an animal in `deceased_animals` returns 422. Creating a medical record, and completing or cancelling a treatment, stay allowed, as in D11.
  - The event handler, prescribing and every treatment status change take the same per-animal advisory lock before reading treatments.
  - The frontend shows fixed copy for the new 422s, and its demo mock imitates the consumer.
- **Rationale** (given by the maintainer, 2026-09-30, who adopted the reasons proposed during planning):
  - A treatment that was still open when the animal died was not completed, so `CANCELLED` is its accurate end state, and no vet has to close it by hand. It is the same effect `feeding-service` has on feeding plans (D9).
  - A rule that lives only in the UI can be bypassed by any direct API call; the read model introduced by D9 makes the backend check cheap and keeps the services independent at runtime.
  - One lock taken by every writer, before any read, avoids the stale-version failure that would send a `DECEASED` event to the dead-letter topic, the problem `feeding-service` fixed with row locks (`0cec3ae`).
- **Consequences**:
  - `health-service` now needs Kafka at runtime; in prod it reads `KAFKA_BOOTSTRAP_SERVERS`. On its first start the new consumer group reads the topic from the earliest offset, so animals that died earlier are recorded and their open treatments cancelled.
  - An animal id that does not exist in `animal-service` is still accepted: only ids already in `deceased_animals` are refused.
  - Idempotency is per animal id and relies on `DECEASED` being terminal, as in `feeding-service` (`docs/events.md`).
  - The frontend does not reload a deceased animal's treatments after its status changes, so the Health section can show cancelled treatments as open until the page or animal is reloaded (`docs/STATE.md`).

## D13. Notifications: shared acknowledgement, structured fields, triage navigation

- **Date**: 2026-09-30 (commits `19b0f22`..`2efa5e2` and the frontend commits that follow)
- **Service(s)**: notification-service, zms-fe
- **Context**: `notification-service` stored one notification per animal event with a pre-formatted message only, and had no REST API; `docs/STATE.md` listed "REST API and UI for notifications" as decided but not built, and `PRODUCT.md` asked the navigation to leave room for them. The stored message contained enclosure UUIDs and not the acting user, and no recipient concept exists (`docs/STATE.md`, notification-service, Open).
- **Alternatives considered**:
  - API: read-only list; or per-user read/unread state with a personal unread count.
  - Data: expose the stored `message` as it is.
  - UI: a global page only; or an Activity section in the animal page only, with no new navigation (as D10 did for health and feeding). For the page, a dense list grouped by day ("Register") and a split between what needs action and the rest ("Triage") were both drawn as a mockup before any code.
  - Count badge: always ink, as the mockup proposed; or always red.
- **Decision**:
  - `GET /notifications` (paged, filtered by animal, severity and open state) and `PUT /notifications/{id}/acknowledge`, both open to `zoo-admin`, `zoo-vet` and `zoo-keeper`. Acknowledging is shared by all staff and the first acknowledgement wins: a later one returns the existing `acknowledgedBy` and `acknowledgedAt` unchanged. There is no per-user read state and no recipient.
  - Each notification also stores the event's structured fields (author, animal name, species, dangerous flag, statuses, enclosure ids). The frontend writes its own sentence from them, with enclosure names; `message` is kept and used for rows stored before these fields existed.
  - The frontend follows the "Triage" direction: a bell in the top bar leading to `/notifications`, a page split into "Needs attention" (open `WARNING` and `CRITICAL`) and "Everything else", and an Activity section at the top of the animal page for every role. This adds the first navigation entry beyond the animal list, which D10 had avoided for health and feeding.
  - The bell's count shows open `WARNING` and `CRITICAL` notifications. It is ink, and turns red when at least one open `CRITICAL` exists. Severity itself uses no new hue: weight, icon and a visible word.
- **Rationale** (given by the maintainer, 2026-09-30, who adopted the reasons proposed during planning and chose the direction from the mockup):
  - The staff work as one team on a shared board: what matters is whether someone has taken a notification in charge, and who, not whether each person has read it. Every write carries its author (`PRODUCT.md`, "Positioning"), and per-user state would have introduced recipients, which are still an open question.
  - The stored text showed enclosure UUIDs and not who acted; structured fields let the frontend write readable sentences and keep future filters possible.
  - The keeper on a phone is the primary user (`PRODUCT.md`, "Users"): the page opens on what needs action, and Activity at the top of the animal page shows its open news first.
  - A death has to stand out from ordinary warnings at a glance; keeping the badge ink otherwise leaves red to danger, as `zms-fe/DESIGN.md` reserves it.
- **Consequences**:
  - `notification-service` now opens an HTTP port and needs Keycloak: a new confidential client `notification-service` in the realm, whose secret comes from `NOTIFICATION_OIDC_CLIENT_SECRET` in `zms-be/infrastructure/.env` and `OIDC_CLIENT_SECRET` in `zms-be/notification-service/.env`. An existing Keycloak must be recreated to import it.
  - The bearer token is attached to a fourth origin, `environment.api.notification` (`keycloak-providers.ts`).
  - The red badge is an exception to the danger-only use of red in `zms-fe/DESIGN.md`.
  - The count is refreshed on navigation and after an acknowledgement; there is no push or polling, so a notification created while the page stays open appears only after the next navigation.
  - "Everything else" in "all" mode is the full history minus the open `WARNING`/`CRITICAL` rows, filtered in the browser, because the API has no filter for acknowledged rows.

## D14. Quarkus for the backend services

- **Date**: 2026-06-26 (backend scaffold, commit `df122c7`)
- **Service(s)**: animal-service, health-service, feeding-service, notification-service
- **Context**: The backend is written in Java 21. The framework had to be chosen before the first service.
- **Alternatives considered**: Spring Boot.
- **Decision**: Every backend service is a Quarkus application, on one Quarkus platform version set in the parent POM (`zms-be/pom.xml`, `quarkus.platform.version` 3.20.0).
- **Rationale** (given by the maintainer, 2026-10-07): to learn Quarkus before using it at work. It is a framework designed for the cloud, younger than Spring Boot but at the leading edge.
- **Consequences**:
  - Panache's repository methods clash with the domain ports' methods of the same name, which led to the Repository pattern with `EntityManager` adapters (D2).
  - Quarkus registers its own mappers for `UnauthorizedException` and `ForbiddenException`, which answer with an empty body. Every service needs its own `UnauthorizedExceptionMapper` and `ForbiddenExceptionMapper` to keep the `{"message": ...}` contract (commits `f446251`, `683449e`).
  - The Kafka consumers run on SmallRye Reactive Messaging and are marked `@Blocking`, because their use cases call the database through JDBC (`AnimalEventConsumer` in health-service, feeding-service and notification-service).

## D15. Angular for the frontend

- **Date**: 2026-09-19 (commit `7be7b2b`)
- **Service(s)**: zms-fe
- **Context**: The staff use the system through a web frontend that calls the backend services.
- **Alternatives considered**: not recorded.
- **Decision**: The frontend is an Angular application with server-side rendering (`zms-fe/`; Angular 22 in `package.json`), built with standalone components, signals and Signal Forms (`PRODUCT.md`).
- **Rationale** (given by the maintainer, 2026-10-07): a complete framework, with router, forms and HTTP calls already included, and uniform conventions: an imposed structure helps as the project grows.
- **Consequences**:
  - Pages that need a token in live mode are rendered in the browser (`RenderMode.Client` for `animals`, `animals/:id` and `notifications` in `app.routes.server.ts`), and Keycloak code is loaded only in the browser (D7).
  - The forms with input fields (register, medical record, treatment, feeding plan, feeding) use Signal Forms (`@angular/forms/signals`); no reactive forms are used.

## D16. Microservices instead of a modular monolith

- **Date**: 2026-06-28 (four services planned in `zms-be/CLAUDE.md`, commit `0c78602`)
- **Service(s)**: animal-service, health-service, feeding-service, notification-service
- **Context**: The domain splits into animals, health, feeding and notifications. At this size a single deployable split into modules would have been enough.
- **Alternatives considered**: a modular monolith.
- **Decision**: Four separately deployed services, each with its own Postgres database. They share data only through the `zoo.animal.events` Kafka topic (`README.md`, Architecture).
- **Rationale** (given by the maintainer, 2026-10-07): to work on the patterns used every day at work (events, outbox, idempotency), knowing that a modular monolith would have been enough at this size. The extra cost was accepted on purpose.
- **Consequences**:
  - A write and its event need the transactional outbox (D3), and every consumer must be idempotent, with its own dead-letter topic.
  - health-service and feeding-service cannot query animals: they keep a local `deceased_animals` read model fed by events (D9, D12), and they never check that an animal id exists.
  - Each service has its own Keycloak client and secret, and running live mode locally needs a `.env` file in up to five folders (`README.md`, Run it).
  - The frontend calls four origins (`environment.api`; D10, D13), and CI builds and tests the four modules separately (`.github/workflows/backend-ci.yml`).
