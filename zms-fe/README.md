# zms-fe

Frontend of the [Zoo Management System](../README.md): Angular 22 with SSR, standalone components and signals. Staff browse animals by enclosure, open an animal's record, change its status and transfer it to another enclosure, and admins register new animals. What each user can do depends on their role.

## Two modes

The mode is chosen at build time, through Angular's `fileReplacements` swapping `src/environments/environment.ts` for `environment.live.ts`.

| | Demo (default) | Live |
|---|---|---|
| Command | `pnpm start` | `pnpm start:live` |
| Data | In memory (`MockAnimalApi`), 19 sample animals and 7 enclosures | `animal-service` on :8080 (`HttpAnimalApi`), animals and enclosures from `/animals` and `/enclosures` |
| Who you are | Role switcher in the header (`DemoSession`) | Keycloak login with PKCE (`KeycloakSession`) |
| Needs a backend | No | Yes, see the [root README](../README.md#live-mode-frontend--backend--keycloak) |

Demo is the default so that someone opening the app cold can use it locally with no backend and no account. No demo build is published. The mock follows the same contract as `/animals`, including the 400/403/404/422 errors, so the UI behaves the same in both modes.

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
│   ├── data/      AnimalApi port + HttpAnimalApi / MockAnimalApi adapters, AnimalStore; enclosure-directory.ts is demo-only data
│   ├── session/   Session port + DemoSession / KeycloakSession
│   ├── auth/      Keycloak providers, loaded only in live mode
│   ├── models/    Animal types, labels, role permissions
│   └── ui/        Icon, role selector
└── features/animals/
    ├── animal-list/     List grouped by enclosure
    ├── animal-detail/   One animal's record
    ├── status-sheet/    Change status (vet, admin)
    ├── transfer-sheet/  Move to another enclosure (keeper, admin)
    └── register-sheet/  Register a new animal (admin)
```

Components depend on the `AnimalApi` and `Session` ports only; which adapter runs depends on the mode. The permission matrix in `core/models/permissions.ts` mirrors the `@RolesAllowed` annotations in `animal-service`.

## Design

The visual direction is "Glasshouse Register": cool glass surfaces, a greenhouse green accent, one color per habitat and per status, Atkinson Hyperlegible, and a graphite dark mode. The design system is in [`DESIGN.md`](DESIGN.md); who the app is for is in [`PRODUCT.md`](../PRODUCT.md).
