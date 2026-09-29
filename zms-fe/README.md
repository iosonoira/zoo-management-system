# zms-fe

Frontend of the [Zoo Management System](../README.md): Angular 22 with SSR, standalone components and signals. Staff browse animals by enclosure, open an animal's record, change its status and transfer it to another enclosure, and admins register new animals. The animal page also holds its medical records and treatments (Health) and its feeding plans and feedings (Feeding). What each user can do depends on their role.

## Two modes

The mode is chosen at build time, through Angular's `fileReplacements` swapping `src/environments/environment.ts` for `environment.live.ts`.

| | Demo (default) | Live |
|---|---|---|
| Command | `pnpm start` | `pnpm start:live` |
| Data | In memory (`MockAnimalApi`, `MockHealthApi`, `MockFeedingApi`): 19 sample animals and 7 enclosures, 10 medical records with 8 treatments, 20 feeding plans with 22 feedings | `animal-service` on :8080 (`HttpAnimalApi`), `health-service` on :8082 (`HttpHealthApi`) and `feeding-service` on :8084 (`HttpFeedingApi`) |
| Who you are | Role switcher in the header (`DemoSession`) | Keycloak login with PKCE (`KeycloakSession`) |
| Needs a backend | No | Yes, see the [root README](../README.md#live-mode-frontend--backend--keycloak) |

Demo is the default so that someone opening the app cold can use it locally with no backend and no account. No demo build is published. The mocks follow the same contracts as the three services, including the 400/403/404/422 errors, so the UI behaves the same in both modes. The demo medical records cover 8 of the 19 animals, and the demo feeding plans cover 19 animals, with plans that are active, suspended and ended. The 22 demo feedings all belong to four plans. `MockFeedingApi` ends the active and suspended plans of a deceased animal when its plans are listed or a plan is created for it, as the Kafka consumer in `feeding-service` does.

In live mode the Health and Feeding sections load on their own. If `health-service` or `feeding-service` is down, only that section shows an error with a "Try again" button, and the rest of the animal page keeps working. The bearer token is sent to the three service origins in `environment.api` and to no other host.

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
│   ├── data/      Ports AnimalApi, HealthApi, FeedingApi, each with an Http* and a Mock* adapter, and the stores AnimalStore, HealthStore, FeedingStore; api-error.ts and page.ts are shared, api-errors.ts holds the error copy; enclosure-directory.ts, demo-data.ts, demo-health.ts and demo-feeding.ts are demo-only data
│   ├── session/   Session port + DemoSession / KeycloakSession
│   ├── auth/      Keycloak providers, bearer-token scope and route guard, loaded only in live mode
│   ├── models/    Animal, health and feeding types, labels, role permissions
│   └── ui/        Icon, role selector
└── features/
    ├── animals/
    │   ├── animal-list/     List grouped by enclosure
    │   ├── animal-detail/   One animal's page: Status, Location, Health, Feeding, Record
    │   ├── status-sheet/    Change status (vet, admin)
    │   ├── transfer-sheet/  Move to another enclosure (keeper, admin)
    │   └── register-sheet/  Register a new animal (admin)
    ├── health/
    │   ├── health-section/         Medical records, newest first, each opening onto its treatments
    │   ├── record-sheet/           Add a medical record (vet, admin)
    │   ├── treatment-sheet/        Prescribe a treatment (vet, admin)
    │   └── treatment-status-sheet/ Change a treatment's status (vet, admin)
    └── feeding/
        ├── feeding-section/   Today's meals, the plan behind them, recent feedings, earlier plans
        ├── meal-track/        The meal track and the rules that give each meal its state (meal-slots.ts)
        ├── feeding-sheet/     Record a feeding (keeper, admin)
        ├── plan-sheet/        Start a feeding plan (vet, admin)
        └── plan-status-sheet/ Suspend, resume or end a plan (vet, admin)
```

Components depend on the `AnimalApi`, `HealthApi`, `FeedingApi` and `Session` ports only; which adapter runs depends on the mode. The permission matrix in `core/models/permissions.ts` mirrors the `@RolesAllowed` annotations on the write endpoints of `AnimalResource` (`animal-service`), `MedicalRecordResource` and `TreatmentResource` (`health-service`) and `FeedingPlanResource` (`feeding-service`). In live mode, `signedInGuard` sends a visitor who is not signed in to Keycloak.

The animal page orders its sections by role. Keepers see Feeding, Status, Location, Health, Record. Vets and admins see Status and Location, then Health and Feeding, then Record. For a deceased animal the Health section still lets a vet or admin add a medical record, but not prescribe a treatment or start one; that rule exists only in the frontend ([D11](../docs/decisions.md)).

## Design

The visual direction is "Glasshouse Register": cool glass surfaces, a greenhouse green accent, one color per habitat and per status, Atkinson Hyperlegible, and a graphite dark mode. The design system is in [`DESIGN.md`](DESIGN.md); who the app is for is in [`PRODUCT.md`](../PRODUCT.md).
