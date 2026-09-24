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

- **Date**: 2026-06-28 (commit `e4adb15`; rule recorded in `zms-be/CLAUDE.md`, commit `0c78602`)
- **Service(s)**: animal-service, health-service, notification-service
- **Context**: Quarkus Panache supports both Active Record (`extends PanacheEntity`) and Repository.
- **Alternatives considered**: Active Record (`extends PanacheEntity`) (`zms-be/CLAUDE.md`).
- **Decision**: Use the Repository pattern. JPA entities live only in `infrastructure/persistence/`, and the application layer works on domain objects (`zms-be/CLAUDE.md`).
- **Rationale**: not recorded.
- **Consequences**:
  - No class extends `PanacheEntity`.
  - The domain repository adapters (`AnimalPanacheRepository`, `EnclosurePanacheRepository`, `MedicalRecordPanacheRepository`, `TreatmentPanacheRepository`, `NotificationPanacheRepository`) implement the domain port with an injected `EntityManager`, not `PanacheRepository<E>` as the text in `zms-be/CLAUDE.md` says. `OutboxEventRepository` is the only `PanacheRepositoryBase`.
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
