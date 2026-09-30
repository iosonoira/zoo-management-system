# zms-fe

Frontend of the [Zoo Management System](../README.md): Angular 22 with SSR, standalone components and signals. Staff browse animals by enclosure, open an animal's record, change its status and transfer it to another enclosure, and admins register new animals. The animal page also holds its medical records and treatments (Health) and its feeding plans and feedings (Feeding). Notifications show in a bell in the top bar, on a page of their own and in an Activity section at the top of the animal page. What each user can do depends on their role.

## Two modes

The mode is chosen at build time, through Angular's `fileReplacements` swapping `src/environments/environment.ts` for `environment.live.ts`.

| | Demo (default) | Live |
|---|---|---|
| Command | `pnpm start` | `pnpm start:live` |
| Data | In memory (`MockAnimalApi`, `MockHealthApi`, `MockFeedingApi`, `MockNotificationApi`): 19 sample animals and 7 enclosures, 10 medical records with 8 treatments, 20 feeding plans with 22 feedings, 10 notifications | `animal-service` on :8080 (`HttpAnimalApi`), `health-service` on :8082 (`HttpHealthApi`), `feeding-service` on :8084 (`HttpFeedingApi`) and `notification-service` on :8083 (`HttpNotificationApi`) |
| Who you are | Role switcher in the header (`DemoSession`) | Keycloak login with PKCE (`KeycloakSession`) |
| Needs a backend | No | Yes, see the [root README](../README.md#live-mode-frontend--backend--keycloak) |

Demo is the default so that someone opening the app cold can use it locally with no backend and no account. No demo build is published. The mocks follow the same contracts as the four services, including the 400/403/404/422 errors, so the UI behaves the same in both modes. The demo medical records cover 8 of the 19 animals, and the demo feeding plans cover 19 animals, with plans that are active, suspended and ended. The 22 demo feedings all belong to four plans. `MockFeedingApi` ends the active and suspended plans of a deceased animal when its plans are listed or a plan is created for it, as the Kafka consumer in `feeding-service` does. `MockHealthApi` cancels the `PRESCRIBED` and `ACTIVE` treatments of a deceased animal when its records are listed or read or a treatment is prescribed or changed, and refuses prescribing and starting with a 422, as the Kafka consumer in `health-service` does. `MockAnimalApi` publishes an event to `DemoEventFeed` for every registration, status change and transfer, and `MockNotificationApi` turns each new event into a notification the next time notifications are read, with the same severity rules as `notification-service`. The 10 demo notifications are seed data (`demo-notifications.ts`), and acknowledgements are kept in memory.

In live mode the Health, Feeding and Activity sections load on their own. If `health-service`, `feeding-service` or `notification-service` is down, only that section shows an error with a "Try again" button, and the rest of the animal page keeps working. The notifications page loads its two lists on their own and each shows its own error, and the bell keeps its last count, or shows none, when the count cannot be read (`NotificationStore.refreshCount`). The bearer token is sent to the four service origins in `environment.api` and to no other host.

In live mode, sign in as `admin.rossi`, `vet.bianchi` or `keeper.conti` ([users and roles](../README.md#users-and-roles)). The Keycloak client `zms-fe` only accepts redirects to http://localhost:4200, so keep that port.

## Scripts

Requires Node and pnpm (`packageManager` pins pnpm 10).

```bash
pnpm install
pnpm start        # demo mode, http://localhost:4200
pnpm start:live   # live mode against the local backend
pnpm test         # unit tests (Vitest)
pnpm build        # production build (demo mode, SSR)
```

## Structure

```
src/app/
├── core/
│   ├── data/      Ports AnimalApi, HealthApi, FeedingApi, NotificationApi, each with an Http* and a Mock* adapter, and the stores AnimalStore, HealthStore, FeedingStore, NotificationStore, ActivityStore; api-error.ts and page.ts are shared, api-errors.ts holds the error copy, notification-copy.ts the notification sentences; DemoEventFeed stands in for the Kafka topic in demo mode; enclosure-directory.ts, demo-data.ts, demo-health.ts, demo-feeding.ts and demo-notifications.ts are demo data (notification-copy.ts also reads enclosure-directory.ts in live mode)
│   ├── session/   Session port + DemoSession / KeycloakSession
│   ├── auth/      Keycloak providers, bearer-token scope and route guard, loaded only in live mode
│   ├── models/    Animal, health and feeding types, labels, role permissions
│   └── ui/        Icon, role selector
└── features/
    ├── animals/
    │   ├── animal-list/     List grouped by enclosure
    │   ├── animal-detail/   One animal's page: Activity, Status, Location, Health, Feeding, Record
    │   ├── status-sheet/    Change status (vet, admin)
    │   ├── transfer-sheet/  Move to another enclosure (keeper, admin)
    │   └── register-sheet/  Register a new animal (admin)
    ├── health/
    │   ├── health-section/         Medical records, newest first, each opening onto its treatments
    │   ├── record-sheet/           Add a medical record (vet, admin)
    │   ├── treatment-sheet/        Prescribe a treatment (vet, admin)
    │   └── treatment-status-sheet/ Change a treatment's status (vet, admin)
    ├── feeding/
    │   ├── feeding-section/   Today's meals, the plan behind them, recent feedings, earlier plans
    │   ├── meal-track/        The meal track and the rules that give each meal its state (meal-slots.ts)
    │   ├── feeding-sheet/     Record a feeding (keeper, admin)
    │   ├── plan-sheet/        Start a feeding plan (vet, admin)
    │   └── plan-status-sheet/ Suspend, resume or end a plan (vet, admin)
    └── notifications/
        ├── notifications-page/ The /notifications page: Needs attention, then Everything else
        ├── activity-section/   One animal's notifications, newest first, at the top of its page
        ├── notification-text/  The sentence of a notification
        ├── notification-meta/  Severity word, species, author and time under a notification
        ├── acknowledged-note/  Who acknowledged a notification, and when
        └── severity-tile/      A notification's severity as a tile
```

Components depend on the `AnimalApi`, `HealthApi`, `FeedingApi`, `NotificationApi` and `Session` ports only; which adapter runs depends on the mode. The permission matrix in `core/models/permissions.ts` mirrors the `@RolesAllowed` annotations on the write endpoints of `AnimalResource` (`animal-service`), `MedicalRecordResource` and `TreatmentResource` (`health-service`), `FeedingPlanResource` (`feeding-service`) and `NotificationResource` (`notification-service`). In live mode, `signedInGuard` sends a visitor who is not signed in to Keycloak.

The animal page starts with Activity for every role, then orders its sections by role. Keepers then see Feeding, Status, Location, Health, Record. Vets and admins see Status and Location, then Health and Feeding, then Record. For a deceased animal the Health section still lets a vet or admin add a medical record, but not prescribe a treatment or start one; `health-service` enforces the same rule with a 422 ([D11](../docs/decisions.md), [D12](../docs/decisions.md#d12-health-service-enforces-d11-and-cancels-a-deceased-animals-open-treatments)).

The bell in the top bar (`App`) counts open `WARNING` and `CRITICAL` notifications. It is ink, and red while an open `CRITICAL` exists. It links to `/notifications` (`NotificationsPage`), which lists what needs attention first and everything else below it. The count is read again on every navigation and after an acknowledgement; there is no polling or push. The Activity section (`ActivitySection`) lists one animal's notifications and reloads after a status change or a transfer made on that page. All three roles can acknowledge a notification, and the first acknowledgement wins ([D13](../docs/decisions.md#d13-notifications-shared-acknowledgement-structured-fields-triage-navigation)).

## Design

The visual direction is "Glasshouse Register": cool glass surfaces, a greenhouse green accent, one color per habitat and per status, Atkinson Hyperlegible, and a graphite dark mode. The design system is in [`DESIGN.md`](DESIGN.md); who the app is for is in [`PRODUCT.md`](../PRODUCT.md).
