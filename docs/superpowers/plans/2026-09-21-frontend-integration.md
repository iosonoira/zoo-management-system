# Frontend Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect `zms-fe` to a running `animal-service` over HTTP with a real Keycloak session, while keeping the existing in-memory demo mode as the default build.

**Architecture:** Two abstract ports — `AnimalApi` (already exists) and `Session` (introduced here) — each with a demo adapter and a live adapter. One build-time flag in `src/environments/` selects the pair, so the demo bundle never contains `keycloak-js`. Feature components, `AnimalStore` and every template stay untouched except the header, which swaps the role switcher for a username and logout control in live mode.

**Tech Stack:** Angular 22 (standalone, signals, SSR via `@angular/ssr`), `@angular/common/http` with `withFetch`, `keycloak-angular@22` + `keycloak-js@26`, Vitest via `@angular/build:unit-test`, Quarkus 3 + Flyway on the backend, Keycloak 26.

**Spec:** `docs/superpowers/specs/2026-09-21-frontend-integration-design.md`

## Global Constraints

- Package manager is **pnpm** (`packageManager: pnpm@10.33.2`). Never run `npm install`.
- Every component stays split across `.ts` / `.html` / `.scss`. No inline templates.
- Angular 22 in this repo uses `@Service()` from `@angular/core`, not `@Injectable()`. It is root-provided by default — that is why `AnimalStore` and `Session` work today with no entry in `app.config.ts`.
- Abstract ports carry **no decorator** and are bound explicitly in `app.config.ts`. `AnimalApi` is the existing example; `Session` becomes the second.
- Adapters take dependencies through `inject()` in field initialisers, never through constructor parameters. `MockAnimalApi` is the pattern to follow.
- Only official `angular.dev` documentation is a source for Angular APIs (`zms-fe/CLAUDE.md`).
- Backend page size ceiling is `ListAnimalsUseCase.MAX_PAGE_SIZE = 100`. A larger `size` is rejected with 400.
- Backend error bodies are always `{"message": "..."}`. Reachable statuses: 400, 401, 403, 404, 409, 422, 500.
- Keycloak realm is `zoo`, frontend client id is `zms-fe`, realm roles are `zoo-admin`, `zoo-vet`, `zoo-keeper`, exposed at the claim path `realm_access/roles`.
- Dev origins: frontend `http://localhost:4200`, `animal-service` `http://localhost:8080`, Keycloak `http://localhost:8081`.
- Commit messages follow Conventional Commits with a scope, e.g. `feat(zms-fe): ...`. Do not add a `Co-Authored-By` trailer.
- Work happens on the branch `feature/frontend-integration`.

---

## File Structure

**Frontend — created**

| File | Responsibility |
|---|---|
| `src/environments/environment.ts` | Demo defaults. The only file `app.config.ts` imports for mode |
| `src/environments/environment.live.ts` | Live values, swapped in by `fileReplacements` |
| `src/app/core/data/enclosure-directory.ts` | `ENCLOSURES`, used by both modes |
| `src/app/core/data/api-errors.ts` | The single vocabulary of user-facing API error copy |
| `src/app/core/data/api-errors.spec.ts` | Tests for that vocabulary |
| `src/app/core/data/http-animal-api.ts` | `HttpAnimalApi extends AnimalApi` |
| `src/app/core/data/http-animal-api.spec.ts` | Paging, request shapes, status mapping |
| `src/app/core/session/demo-session.ts` | Today's `Session` body |
| `src/app/core/session/keycloak-session.ts` | Role and username from the token |
| `src/app/core/session/keycloak-session.spec.ts` | Role precedence and username extraction |
| `src/app/core/auth/keycloak-providers.ts` | Browser-only Keycloak providers and the auth guard |
| `public/silent-check-sso.html` | Silent SSO iframe target |

**Frontend — modified**

| File | Change |
|---|---|
| `src/app/core/data/animal-api.ts` | `ApiError` status union gains 401 and 409 |
| `src/app/core/data/demo-data.ts` | Loses `DEMO_ENCLOSURES`, imports `ENCLOSURES` |
| `src/app/core/data/mock-animal-api.ts` | Uses `api-errors.ts` instead of inline strings |
| `src/app/core/data/animal-store.ts` | Platform guard in `load()` |
| `src/app/core/session/session.ts` | Becomes an abstract port |
| `src/app/app.config.ts` | Binds both ports from `environment` |
| `src/app/main.ts` | Merges browser-only auth providers |
| `src/app/app.routes.ts` | Auth guard on the animal routes in live mode |
| `src/app/app.routes.server.ts` | `/animals` becomes `RenderMode.Client` |
| `src/app/app.html` | Role switcher vs. account control |
| `src/app/app.ts` | Exposes `live` and `signOut()` |
| `angular.json` | `live` build and serve configurations |
| `package.json` | Two dependencies, one script |

**Backend — modified**

| File | Change |
|---|---|
| `zms-be/animal-service/src/main/resources/db/dev/R__seed_demo_animals.sql` | Created: the demo roster |
| `zms-be/animal-service/src/main/resources/application.properties` | `%dev` Flyway locations |
| `zms-be/infrastructure/keycloak/realm-export.json` | PKCE attribute, renamed users |

---

### Task 1: Environment flag and enclosure directory

Splits the build into two modes and moves the enclosure directory out of the demo-data file, since live mode needs it too. No behaviour changes yet.

**Files:**
- Create: `zms-fe/src/environments/environment.ts`
- Create: `zms-fe/src/environments/environment.live.ts`
- Create: `zms-fe/src/app/core/data/enclosure-directory.ts`
- Create: `zms-fe/src/environments/environment.spec.ts`
- Modify: `zms-fe/src/app/core/data/demo-data.ts:1-13`
- Modify: `zms-fe/angular.json`

**Interfaces:**
- Consumes: nothing.
- Produces: `environment: { live: boolean; apiBaseUrl: string; keycloak: { url: string; realm: string; clientId: string } | null }` from `src/environments/environment`; `ENCLOSURES: readonly Enclosure[]` from `src/app/core/data/enclosure-directory`.

- [ ] **Step 1: Write the failing test**

Create `zms-fe/src/environments/environment.spec.ts`:

```ts
import { environment } from './environment';
import { ENCLOSURES } from '../app/core/data/enclosure-directory';

describe('environment', () => {
  it('defaults to demo mode', () => {
    expect(environment.live).toBe(false);
    expect(environment.keycloak).toBeNull();
  });
});

describe('ENCLOSURES', () => {
  it('lists every enclosure the demo roster references', () => {
    expect(ENCLOSURES.length).toBe(7);
    expect(ENCLOSURES.map((e) => e.name)).toContain('Quarantine Unit');
  });

  it('has unique ids', () => {
    expect(new Set(ENCLOSURES.map((e) => e.id)).size).toBe(ENCLOSURES.length);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL — cannot resolve `./environment` or `../app/core/data/enclosure-directory`.

- [ ] **Step 3: Create the environment files**

`zms-fe/src/environments/environment.ts`:

```ts
/** Build-time mode flag. Replaced by `environment.live.ts` in the `live` configuration. */
export const environment = {
  live: false,
  apiBaseUrl: '',
  keycloak: null,
} as const satisfies Environment;

export interface Environment {
  /** True when the app talks to a running backend instead of the in-memory mock. */
  readonly live: boolean;
  /** Origin of `animal-service`. Empty in demo mode; the bearer token is scoped to it. */
  readonly apiBaseUrl: string;
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}
```

`zms-fe/src/environments/environment.live.ts`:

```ts
import { Environment } from './environment';

export const environment = {
  live: true,
  apiBaseUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'zoo',
    clientId: 'zms-fe',
  },
} as const satisfies Environment;
```

Note the circular-looking import: `environment.live.ts` imports only the `Environment` type from `environment.ts`, and after replacement that file is gone. Declare `Environment` in `environment.ts` as above and have `environment.live.ts` re-declare it instead if the build complains:

```ts
// Fallback if the type import cannot survive file replacement — duplicate the shape.
export interface Environment {
  readonly live: boolean;
  readonly apiBaseUrl: string;
  readonly keycloak: { readonly url: string; readonly realm: string; readonly clientId: string } | null;
}
```

- [ ] **Step 4: Create the enclosure directory**

`zms-fe/src/app/core/data/enclosure-directory.ts`:

```ts
import { Enclosure } from '../models/animal';

/**
 * Enclosures exist in the backend only as UUIDs on `animals.enclosure_id`. Until an
 * enclosure resource exists, their names and habitats live here and both the mock and
 * the HTTP adapter read from this one list. The dev seed migration uses the same ids.
 */
export const ENCLOSURES: readonly Enclosure[] = [
  { id: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', name: 'Savanna Paddock', habitat: 'TERRESTRIAL' },
  { id: '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81', name: 'Big Cat Ridge', habitat: 'TERRESTRIAL' },
  { id: '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92', name: 'Primate Forest', habitat: 'TERRESTRIAL' },
  { id: '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3', name: 'Lagoon Pool', habitat: 'AQUATIC' },
  { id: '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4', name: 'Reptile House', habitat: 'AMPHIBIOUS' },
  { id: '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5', name: 'Wetland Marsh', habitat: 'AMPHIBIOUS' },
  { id: '6bce8f7a-9cad-4e45-9a7b-8c9d0e1f2ad6', name: 'Quarantine Unit', habitat: 'TERRESTRIAL' },
];
```

- [ ] **Step 5: Point `demo-data.ts` at the directory**

Replace lines 1-13 of `zms-fe/src/app/core/data/demo-data.ts` with:

```ts
import { Animal } from '../models/animal';
import { ENCLOSURES } from './enclosure-directory';

/** Fictional demo data. Names, staff usernames and enclosures are invented. */

const [SAVANNA, BIG_CATS, PRIMATES, LAGOON, REPTILES, WETLAND] = ENCLOSURES.map((e) => e.id);
```

The `Enclosure` type import and the `DEMO_ENCLOSURES` export both go. Then fix the two remaining references:
- `mock-animal-api.ts:6` imports `DEMO_ENCLOSURES` — change to `import { ENCLOSURES } from './enclosure-directory';` and replace both usages (`DEMO_ENCLOSURES.some(...)` at the transfer check, `[...DEMO_ENCLOSURES]` in `listEnclosures`).

- [ ] **Step 6: Run the test to verify it passes**

Run from `zms-fe/`: `pnpm test`
Expected: PASS, including the pre-existing `app.spec.ts`.

- [ ] **Step 7: Add the `live` build and serve configurations**

In `zms-fe/angular.json`, inside `projects.zms-fe.architect.build.configurations`, add a sibling of `production` and `development`:

```jsonc
"live": {
  "optimization": false,
  "extractLicenses": false,
  "sourceMap": true,
  "fileReplacements": [
    {
      "replace": "src/environments/environment.ts",
      "with": "src/environments/environment.live.ts"
    }
  ]
}
```

Inside `projects.zms-fe.architect.serve.configurations`, add:

```jsonc
"live": {
  "buildTarget": "zms-fe:build:live"
}
```

Leave `defaultConfiguration` as it is on both targets. Add to `package.json` scripts:

```json
"start:live": "ng serve --configuration live"
```

- [ ] **Step 8: Verify both builds resolve**

Run from `zms-fe/`: `pnpm build`
Expected: succeeds.

Run from `zms-fe/`: `pnpm exec ng build --configuration live`
Expected: succeeds. If the type import in `environment.live.ts` fails after replacement, apply the fallback from Step 3.

- [ ] **Step 9: Commit**

```bash
git add zms-fe/src/environments zms-fe/src/app/core/data/enclosure-directory.ts \
        zms-fe/src/app/core/data/demo-data.ts zms-fe/src/app/core/data/mock-animal-api.ts \
        zms-fe/angular.json zms-fe/package.json
git commit -m "feat(zms-fe): add a build-time mode flag and a shared enclosure directory"
```

---

### Task 2: One vocabulary for API error copy

The mock writes user-facing error copy inline. The HTTP adapter needs the same copy for the same conditions, plus two conditions the mock cannot produce. Extracting the vocabulary first means the two adapters cannot drift.

**Files:**
- Create: `zms-fe/src/app/core/data/api-errors.ts`
- Create: `zms-fe/src/app/core/data/api-errors.spec.ts`
- Modify: `zms-fe/src/app/core/data/animal-api.ts:3-11`
- Modify: `zms-fe/src/app/core/data/mock-animal-api.ts`

**Interfaces:**
- Consumes: `ApiError` from `./animal-api`.
- Produces: from `./api-errors` — `forbidden(action: 'updateStatus' | 'transfer'): ApiError`, `notFound(): ApiError`, `sameStatus(name: string): ApiError`, `deceasedStatus(name: string): ApiError`, `deceasedTransfer(name: string): ApiError`, `unknownEnclosure(): ApiError`, `conflict(name: string): ApiError`, `sessionExpired(): ApiError`, `serverError(): ApiError`.

- [ ] **Step 1: Write the failing test**

Create `zms-fe/src/app/core/data/api-errors.spec.ts`:

```ts
import { ApiError } from './animal-api';
import {
  conflict,
  deceasedStatus,
  deceasedTransfer,
  forbidden,
  notFound,
  sameStatus,
  serverError,
  sessionExpired,
  unknownEnclosure,
} from './api-errors';

describe('api-errors', () => {
  it('carries the status the backend returns', () => {
    expect(forbidden('transfer').status).toBe(403);
    expect(notFound().status).toBe(404);
    expect(sameStatus('Kibo').status).toBe(422);
    expect(deceasedStatus('Bruno').status).toBe(422);
    expect(deceasedTransfer('Bruno').status).toBe(400);
    expect(unknownEnclosure().status).toBe(400);
    expect(conflict('Kibo').status).toBe(409);
    expect(sessionExpired().status).toBe(401);
    expect(serverError().status).toBe(500);
  });

  it('is an ApiError so the store can read the message', () => {
    expect(notFound()).toBeInstanceOf(ApiError);
  });

  it('names the animal when the message is about one', () => {
    expect(conflict('Kibo').message).toContain('Kibo');
    expect(deceasedTransfer('Bruno').message).toContain('Bruno');
    expect(sameStatus('Kibo').message).toContain('Kibo');
  });

  it('tells each role what it may do', () => {
    expect(forbidden('updateStatus').message).toContain('vets');
    expect(forbidden('transfer').message).toContain('keepers');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL — cannot resolve `./api-errors`.

- [ ] **Step 3: Widen the `ApiError` status union**

In `zms-fe/src/app/core/data/animal-api.ts`, replace the class with:

```ts
/** Every status `animal-service` can return, including the two the mock never raises. */
export type ApiErrorStatus = 400 | 401 | 403 | 404 | 409 | 422 | 500;

/** Error shape mirroring the HTTP statuses `animal-service` returns. */
export class ApiError extends Error {
  constructor(
    readonly status: ApiErrorStatus,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
```

- [ ] **Step 4: Write the vocabulary**

Create `zms-fe/src/app/core/data/api-errors.ts`:

```ts
import { ApiError } from './animal-api';

/**
 * The single source of user-facing copy for backend failures. `animal-service` returns
 * `{"message": "..."}` on every error, but those strings are either generic
 * ("Insufficient role") or technical ("Cannot transfer a deceased animal"), so the
 * adapters map the status code to the copy below instead of forwarding the body.
 */

export function forbidden(action: 'updateStatus' | 'transfer'): ApiError {
  return action === 'updateStatus'
    ? new ApiError(403, 'Only vets and admins can change an animal’s status.')
    : new ApiError(403, 'Only keepers and admins can transfer animals.');
}

export function notFound(): ApiError {
  return new ApiError(404, 'No animal with this tag exists.');
}

export function sameStatus(name: string): ApiError {
  return new ApiError(422, `${name} already has this status.`);
}

export function deceasedStatus(name: string): ApiError {
  return new ApiError(422, `${name} is recorded as deceased. The status can no longer change.`);
}

export function deceasedTransfer(name: string): ApiError {
  return new ApiError(400, `${name} is recorded as deceased and can’t be transferred.`);
}

export function unknownEnclosure(): ApiError {
  return new ApiError(400, 'That enclosure doesn’t exist.');
}

/** Optimistic locking lost: another writer saved first. The mock cannot produce this. */
export function conflict(name: string): ApiError {
  return new ApiError(409, `Someone else updated ${name} first. Reload to see the latest record.`);
}

export function sessionExpired(): ApiError {
  return new ApiError(401, 'Your session expired. Sign in again to continue.');
}

export function serverError(): ApiError {
  return new ApiError(500, 'Something went wrong on our side. Check your connection and try again.');
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run from `zms-fe/`: `pnpm test`
Expected: PASS.

- [ ] **Step 6: Move the mock onto the vocabulary**

In `zms-fe/src/app/core/data/mock-animal-api.ts`, replace each inline `throw new ApiError(...)` with the matching factory. The file's imports become:

```ts
import { inject } from '@angular/core';
import { Animal, AnimalStatus, Enclosure, canBeTransferred, canTransitionTo } from '../models/animal';
import { can } from '../models/permissions';
import { Session } from '../session/session';
import { AnimalApi } from './animal-api';
import {
  deceasedStatus,
  deceasedTransfer,
  forbidden,
  notFound,
  sameStatus,
  unknownEnclosure,
} from './api-errors';
import { ENCLOSURES } from './enclosure-directory';
import { DEMO_ANIMALS } from './demo-data';
```

`updateStatus` becomes:

```ts
  async updateStatus(id: string, status: AnimalStatus): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'updateStatus')) {
      throw forbidden('updateStatus');
    }
    const animal = this.find(id);
    if (!canTransitionTo(animal, status)) {
      throw animal.status === 'DECEASED' ? deceasedStatus(animal.name) : sameStatus(animal.name);
    }
    return this.save({ ...animal, status, updatedBy: this.session.username() });
  }
```

`transfer` becomes:

```ts
  async transfer(id: string, targetEnclosureId: string): Promise<Animal> {
    await delay();
    if (!can(this.session.role(), 'transfer')) {
      throw forbidden('transfer');
    }
    const animal = this.find(id);
    if (!canBeTransferred(animal)) {
      throw deceasedTransfer(animal.name);
    }
    if (!ENCLOSURES.some((e) => e.id === targetEnclosureId)) {
      throw unknownEnclosure();
    }
    return this.save({ ...animal, enclosureId: targetEnclosureId, updatedBy: this.session.username() });
  }
```

`find` becomes:

```ts
  private find(id: string): Animal {
    const animal = this.animals.get(id);
    if (!animal) {
      throw notFound();
    }
    return animal;
  }
```

`listEnclosures` returns `[...ENCLOSURES]`.

- [ ] **Step 7: Run the tests and the build**

Run from `zms-fe/`: `pnpm test`
Expected: PASS.

Run from `zms-fe/`: `pnpm build`
Expected: succeeds with no unused-import errors.

- [ ] **Step 8: Commit**

```bash
git add zms-fe/src/app/core/data/animal-api.ts zms-fe/src/app/core/data/api-errors.ts \
        zms-fe/src/app/core/data/api-errors.spec.ts zms-fe/src/app/core/data/mock-animal-api.ts
git commit -m "refactor(zms-fe): extract API error copy into one vocabulary"
```

---

### Task 3: Turn `Session` into a port

`Session` is a concrete root-provided service. It becomes an abstract port with the demo implementation moved out, so a Keycloak implementation can take its place without touching `App` or `RoleSelect`.

**Files:**
- Modify: `zms-fe/src/app/core/session/session.ts` (whole file)
- Create: `zms-fe/src/app/core/session/demo-session.ts`
- Create: `zms-fe/src/app/core/session/demo-session.spec.ts`
- Modify: `zms-fe/src/app/app.config.ts`
- Modify: `zms-fe/src/app/app.spec.ts`

**Interfaces:**
- Consumes: `ZooRole`, `Permission`, `can` (unchanged).
- Produces: abstract `Session` with `readonly role: Signal<ZooRole>`, `readonly username: Signal<string>`, `readonly canSwitchRole: boolean`, `can(permission: Permission): boolean`, `setRole(role: ZooRole): void`, `restore(): void`, `signOut(): void`. `DemoSession extends Session` implements all of them.

- [ ] **Step 1: Write the failing test**

Create `zms-fe/src/app/core/session/demo-session.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { DemoSession } from './demo-session';
import { Session } from './session';

describe('DemoSession', () => {
  let session: Session;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [{ provide: Session, useClass: DemoSession }],
    });
    session = TestBed.inject(Session);
  });

  it('starts as a keeper', () => {
    expect(session.role()).toBe('zoo-keeper');
    expect(session.username()).toBe('keeper.conti');
  });

  it('allows switching roles', () => {
    expect(session.canSwitchRole).toBe(true);
    session.setRole('zoo-vet');
    expect(session.role()).toBe('zoo-vet');
    expect(session.username()).toBe('vet.bianchi');
  });

  it('applies the same permission matrix as the backend', () => {
    session.setRole('zoo-vet');
    expect(session.can('updateStatus')).toBe(true);
    expect(session.can('transfer')).toBe(false);
  });

  it('restores a stored role', () => {
    session.setRole('zoo-admin');
    const fresh = TestBed.runInInjectionContext(() => new DemoSession());
    fresh.restore();
    expect(fresh.role()).toBe('zoo-admin');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL — cannot resolve `./demo-session`.

- [ ] **Step 3: Rewrite `session.ts` as the port**

Replace the whole of `zms-fe/src/app/core/session/session.ts`:

```ts
import { Signal } from '@angular/core';
import { ZooRole } from '../models/animal';
import { Permission } from '../models/permissions';

/**
 * Port for the signed-in user. `DemoSession` holds a role picked from the header
 * switcher; `KeycloakSession` reads it from the access token. Components depend on
 * this shape only.
 */
export abstract class Session {
  /** The active role. The UI is built around one role at a time. */
  abstract readonly role: Signal<ZooRole>;
  /** What the backend records in `createdBy` / `updatedBy`. */
  abstract readonly username: Signal<string>;
  /** False when the role comes from a token and the header shows an account control. */
  abstract readonly canSwitchRole: boolean;

  abstract can(permission: Permission): boolean;
  /** Throws when `canSwitchRole` is false. */
  abstract setRole(role: ZooRole): void;
  /** Call from the browser only, after hydration. */
  abstract restore(): void;
  abstract signOut(): void;
}
```

- [ ] **Step 4: Move the demo body into `demo-session.ts`**

Create `zms-fe/src/app/core/session/demo-session.ts`:

```ts
import { computed, signal } from '@angular/core';
import { ZooRole } from '../models/animal';
import { Permission, can } from '../models/permissions';
import { Session } from './session';

const DEMO_USERS: Record<ZooRole, string> = {
  'zoo-keeper': 'keeper.conti',
  'zoo-vet': 'vet.bianchi',
  'zoo-admin': 'admin.rossi',
};

const STORAGE_KEY = 'zms.demo-role';

/**
 * Demo stand-in for the Keycloak session: holds the active role and the username
 * that the backend would read from the token principal.
 */
export class DemoSession extends Session {
  private readonly activeRole = signal<ZooRole>('zoo-keeper');

  readonly role = this.activeRole.asReadonly();
  readonly username = computed(() => DEMO_USERS[this.activeRole()]);
  readonly canSwitchRole = true;

  can(permission: Permission): boolean {
    return can(this.activeRole(), permission);
  }

  setRole(role: ZooRole): void {
    this.activeRole.set(role);
    try {
      localStorage.setItem(STORAGE_KEY, role);
    } catch {
      // Storage unavailable (private mode, blocked site data): role stays in memory.
    }
  }

  restore(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored && stored in DEMO_USERS) {
        this.activeRole.set(stored as ZooRole);
      }
    } catch {
      // Ignore unavailable storage.
    }
  }

  /** Nothing to sign out of in demo mode. */
  signOut(): void {}
}
```

- [ ] **Step 5: Bind the port**

In `zms-fe/src/app/app.config.ts`, add the import and the provider:

```ts
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { routes } from './app.routes';
import { AnimalApi } from './core/data/animal-api';
import { MockAnimalApi } from './core/data/mock-animal-api';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true })),
    provideClientHydration(),
    { provide: AnimalApi, useClass: MockAnimalApi },
    { provide: Session, useClass: DemoSession },
  ],
};
```

- [ ] **Step 6: Fix `app.spec.ts`**

`App` injects `Session`, which is now abstract and no longer root-provided. Add the provider to the existing test bed in `zms-fe/src/app/app.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: Session, useClass: DemoSession }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
```

- [ ] **Step 7: Run the tests and the build**

Run from `zms-fe/`: `pnpm test`
Expected: PASS, including the new `DemoSession` suite.

Run from `zms-fe/`: `pnpm build`
Expected: succeeds. `MockAnimalApi` injects `Session` and still resolves through the new provider.

- [ ] **Step 8: Commit**

```bash
git add zms-fe/src/app/core/session zms-fe/src/app/app.config.ts zms-fe/src/app/app.spec.ts
git commit -m "refactor(zms-fe): turn Session into a port with a demo adapter"
```

---

### Task 4: The HTTP adapter

The live implementation of `AnimalApi`. Built entirely against `HttpTestingController`, so it needs no backend to be finished and reviewed.

**Files:**
- Create: `zms-fe/src/app/core/data/http-animal-api.ts`
- Create: `zms-fe/src/app/core/data/http-animal-api.spec.ts`

**Interfaces:**
- Consumes: `AnimalApi`, `ApiError` from `./animal-api`; the factories from `./api-errors`; `ENCLOSURES` from `./enclosure-directory`; `environment.apiBaseUrl`.
- Produces: `HttpAnimalApi extends AnimalApi`, bound to `AnimalApi` in Task 7. Exports `PAGE_SIZE = 100`.

- [ ] **Step 1: Write the failing test**

Create `zms-fe/src/app/core/data/http-animal-api.spec.ts`:

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Animal } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';
import { HttpAnimalApi, PAGE_SIZE } from './http-animal-api';

const BASE = 'http://localhost:8080';

function animal(overrides: Partial<Animal> = {}): Animal {
  return {
    id: '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
    name: 'Kibo',
    species: 'Reticulated giraffe',
    dangerous: false,
    habitat: 'TERRESTRIAL',
    enclosureId: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
    arrivalDate: '2019-04-11',
    status: 'HEALTHY',
    createdBy: 'admin.rossi',
    updatedBy: 'keeper.conti',
    ...overrides,
  };
}

describe('HttpAnimalApi', () => {
  let api: AnimalApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AnimalApi, useClass: HttpAnimalApi },
      ],
    });
    api = TestBed.inject(AnimalApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for the first page at the server maximum', async () => {
    const pending = api.listAll();
    const request = http.expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`);
    expect(request.request.method).toBe('GET');
    request.flush({ items: [animal()], page: 0, size: PAGE_SIZE, total: 1 });
    await expect(pending).resolves.toEqual([animal()]);
  });

  it('keeps paging until it has the whole roster', async () => {
    const first = Array.from({ length: PAGE_SIZE }, (_, i) =>
      animal({ id: `page0-${i}`, name: `A${i}` }),
    );
    const pending = api.listAll();

    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .flush({ items: first, page: 0, size: PAGE_SIZE, total: PAGE_SIZE + 2 });

    const second = [animal({ id: 'page1-0' }), animal({ id: 'page1-1' })];
    http
      .expectOne(`${BASE}/animals?page=1&size=${PAGE_SIZE}`)
      .flush({ items: second, page: 1, size: PAGE_SIZE, total: PAGE_SIZE + 2 });

    await expect(pending).resolves.toHaveLength(PAGE_SIZE + 2);
  });

  it('stops paging when a page comes back short', async () => {
    const pending = api.listAll();
    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .flush({ items: [animal()], page: 0, size: PAGE_SIZE, total: 999 });
    await expect(pending).resolves.toHaveLength(1);
  });

  it('reads one animal by id', async () => {
    const pending = api.getById('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41');
    const request = http.expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41`);
    expect(request.request.method).toBe('GET');
    request.flush(animal());
    await expect(pending).resolves.toEqual(animal());
  });

  it('sends the status as the backend expects it', async () => {
    const pending = api.updateStatus('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', 'IN_TREATMENT');
    const request = http.expectOne(
      `${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'IN_TREATMENT' });
    request.flush(animal({ status: 'IN_TREATMENT' }));
    await expect(pending).resolves.toEqual(animal({ status: 'IN_TREATMENT' }));
  });

  it('sends the transfer target as the backend expects it', async () => {
    const target = '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81';
    const pending = api.transfer('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', target);
    const request = http.expectOne(
      `${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/transfer`,
    );
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ targetEnclosureId: target });
    request.flush(animal({ enclosureId: target }));
    await expect(pending).resolves.toEqual(animal({ enclosureId: target }));
  });

  it('serves enclosures from the local directory without a request', async () => {
    await expect(api.listEnclosures()).resolves.toContainEqual({
      id: '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70',
      name: 'Savanna Paddock',
      habitat: 'TERRESTRIAL',
    });
  });

  it('maps 403 to the role copy rather than the backend string', async () => {
    const pending = api.updateStatus('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', 'HEALTHY');
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`)
      .flush({ message: 'Insufficient role' }, { status: 403, statusText: 'Forbidden' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error).toBeInstanceOf(ApiError);
      expect(error.status).toBe(403);
      expect(error.message).toContain('vets');
      expect(error.message).not.toContain('Insufficient role');
      return true;
    });
  });

  it('maps 404 to the missing-animal copy', async () => {
    const pending = api.getById('missing');
    http
      .expectOne(`${BASE}/animals/missing`)
      .flush({ message: 'Animal not found: missing' }, { status: 404, statusText: 'Not Found' });
    await expect(pending).rejects.toMatchObject({ status: 404 });
  });

  it('maps 409 to the conflict copy and names the animal', async () => {
    const pending = api.transfer(
      '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
      '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81',
      'Kibo',
    );
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/transfer`)
      .flush({ message: 'Concurrent update' }, { status: 409, statusText: 'Conflict' });

    await expect(pending).rejects.toSatisfy((error: ApiError) => {
      expect(error.status).toBe(409);
      expect(error.message).toContain('Kibo');
      return true;
    });
  });

  it('maps 401 to the expired-session copy', async () => {
    const pending = api.getById('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41');
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41`)
      .flush({ message: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });
    await expect(pending).rejects.toMatchObject({ status: 401 });
  });

  it('maps 422 to the transition copy', async () => {
    const pending = api.updateStatus(
      '7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41',
      'HEALTHY',
      'Kibo',
    );
    http
      .expectOne(`${BASE}/animals/7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41/status`)
      .flush({ message: 'Same status' }, { status: 422, statusText: 'Unprocessable Entity' });
    await expect(pending).rejects.toMatchObject({ status: 422 });
  });

  it('maps an unreachable backend to the generic failure', async () => {
    const pending = api.listAll();
    http
      .expectOne(`${BASE}/animals?page=0&size=${PAGE_SIZE}`)
      .error(new ProgressEvent('network error'));
    await expect(pending).rejects.toMatchObject({ status: 500 });
  });
});
```

The tests call `updateStatus` and `transfer` with a third argument. That widens the port: see Step 3.

- [ ] **Step 2: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL — cannot resolve `./http-animal-api`.

- [ ] **Step 3: Widen the port with the animal's name**

Error copy for 400/409/422 names the animal, and the adapter only has an id. The caller has the animal. Add an optional trailing parameter to the two write methods in `zms-fe/src/app/core/data/animal-api.ts`:

```ts
export abstract class AnimalApi {
  abstract listAll(): Promise<Animal[]>;
  abstract getById(id: string): Promise<Animal>;
  /** `name` is used only to write error copy that names the animal. */
  abstract updateStatus(id: string, status: AnimalStatus, name?: string): Promise<Animal>;
  abstract transfer(id: string, targetEnclosureId: string, name?: string): Promise<Animal>;
  abstract listEnclosures(): Promise<Enclosure[]>;
}
```

`MockAnimalApi` already has the animal in hand and ignores the parameter; add `_name?: string` to both signatures so the override stays type-compatible.

In `zms-fe/src/app/core/data/animal-store.ts`, forward the name — it holds the roster:

```ts
  updateStatus(id: string, status: AnimalStatus): Promise<Animal> {
    return this.api.updateStatus(id, status, this.byId(id)?.name);
  }

  transfer(id: string, enclosureId: string): Promise<Animal> {
    return this.api.transfer(id, enclosureId, this.byId(id)?.name);
  }
```

- [ ] **Step 4: Write the adapter**

Create `zms-fe/src/app/core/data/http-animal-api.ts`:

```ts
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Animal, AnimalStatus, Enclosure } from '../models/animal';
import { AnimalApi, ApiError } from './animal-api';
import {
  conflict,
  deceasedTransfer,
  forbidden,
  notFound,
  sameStatus,
  serverError,
  sessionExpired,
} from './api-errors';
import { ENCLOSURES } from './enclosure-directory';

/** `ListAnimalsUseCase.MAX_PAGE_SIZE`; a larger value is rejected with 400. */
export const PAGE_SIZE = 100;

interface AnimalPageResponse {
  readonly items: readonly Animal[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/**
 * Live adapter for the `/animals` REST contract. `AnimalResponse` shares every field
 * name with the `Animal` interface and `arrivalDate` arrives as an ISO date string, so
 * responses are typed rather than mapped.
 *
 * Error bodies are discarded: the backend's `message` is either generic or technical,
 * so the status code is mapped to the copy in `api-errors.ts` instead.
 */
export class HttpAnimalApi extends AnimalApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/animals`;

  async listAll(): Promise<Animal[]> {
    const all: Animal[] = [];
    for (let page = 0; ; page++) {
      const response = await this.request<AnimalPageResponse>(() =>
        this.http.get<AnimalPageResponse>(`${this.base}?page=${page}&size=${PAGE_SIZE}`),
      );
      all.push(...response.items);
      // Stop on a short page as well as on a satisfied total: a roster that shrinks
      // between requests would otherwise loop forever.
      if (response.items.length < PAGE_SIZE || all.length >= response.total) {
        return all;
      }
    }
  }

  getById(id: string): Promise<Animal> {
    return this.request(() => this.http.get<Animal>(`${this.base}/${id}`));
  }

  updateStatus(id: string, status: AnimalStatus, name?: string): Promise<Animal> {
    return this.request(
      () => this.http.put<Animal>(`${this.base}/${id}/status`, { status }),
      { action: 'updateStatus', name },
    );
  }

  transfer(id: string, targetEnclosureId: string, name?: string): Promise<Animal> {
    return this.request(
      () => this.http.put<Animal>(`${this.base}/${id}/transfer`, { targetEnclosureId }),
      { action: 'transfer', name },
    );
  }

  /** No enclosure resource exists on the backend; the directory is local to the app. */
  async listEnclosures(): Promise<Enclosure[]> {
    return [...ENCLOSURES];
  }

  private async request<T>(
    send: () => import('rxjs').Observable<T>,
    context: { action?: 'updateStatus' | 'transfer'; name?: string } = {},
  ): Promise<T> {
    try {
      return await firstValueFrom(send());
    } catch (error) {
      throw toApiError(error, context);
    }
  }
}

function toApiError(
  error: unknown,
  context: { action?: 'updateStatus' | 'transfer'; name?: string },
): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return serverError();
  }
  const name = context.name ?? 'This animal';
  switch (error.status) {
    case 400:
      return deceasedTransfer(name);
    case 401:
      return sessionExpired();
    case 403:
      return forbidden(context.action ?? 'updateStatus');
    case 404:
      return notFound();
    case 409:
      return conflict(name);
    case 422:
      return sameStatus(name);
    default:
      // Includes status 0, which is what a CORS rejection or an unreachable host looks like.
      return serverError();
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run from `zms-fe/`: `pnpm test`
Expected: PASS — all thirteen `HttpAnimalApi` cases plus every earlier suite.

If `toSatisfy` is unavailable in this Vitest version, replace those two assertions with a `try/catch` that awaits the promise and asserts on the caught error.

- [ ] **Step 6: Commit**

```bash
git add zms-fe/src/app/core/data/http-animal-api.ts zms-fe/src/app/core/data/http-animal-api.spec.ts \
        zms-fe/src/app/core/data/animal-api.ts zms-fe/src/app/core/data/animal-store.ts \
        zms-fe/src/app/core/data/mock-animal-api.ts
git commit -m "feat(zms-fe): add the HTTP adapter for animal-service"
```

---

### Task 5: Keep the server out of the API

Prerendering `/animals` executes `AnimalStore.load()` at build time — today against demo data, in live mode against a backend with no token. Both stop here.

**Files:**
- Modify: `zms-fe/src/app/core/data/animal-store.ts`
- Create: `zms-fe/src/app/core/data/animal-store.spec.ts`
- Modify: `zms-fe/src/app/app.routes.server.ts:4`

**Interfaces:**
- Consumes: `AnimalApi`.
- Produces: no new exports. `AnimalStore.load()` resolves without a request when `PLATFORM_ID` is not a browser, leaving `state()` at `'idle'`.

- [ ] **Step 1: Write the failing test**

Create `zms-fe/src/app/core/data/animal-store.spec.ts`:

```ts
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Animal, AnimalStatus, Enclosure } from '../models/animal';
import { AnimalApi } from './animal-api';
import { AnimalStore } from './animal-store';
import { ENCLOSURES } from './enclosure-directory';

class CountingApi extends AnimalApi {
  calls = 0;
  async listAll(): Promise<Animal[]> {
    this.calls++;
    return [];
  }
  async getById(): Promise<Animal> {
    throw new Error('not used');
  }
  async updateStatus(): Promise<Animal> {
    throw new Error('not used');
  }
  async transfer(): Promise<Animal> {
    throw new Error('not used');
  }
  async listEnclosures(): Promise<Enclosure[]> {
    return [...ENCLOSURES];
  }
}

function storeOn(platform: string): { store: AnimalStore; api: CountingApi } {
  const api = new CountingApi();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: PLATFORM_ID, useValue: platform },
      { provide: AnimalApi, useValue: api },
      AnimalStore,
    ],
  });
  return { store: TestBed.inject(AnimalStore), api };
}

describe('AnimalStore.load', () => {
  it('does not call the API on the server', async () => {
    const { store, api } = storeOn('server');
    await store.load();
    expect(api.calls).toBe(0);
    expect(store.state()).toBe('idle');
  });

  it('loads in the browser', async () => {
    const { store, api } = storeOn('browser');
    await store.load();
    expect(api.calls).toBe(1);
    expect(store.state()).toBe('ready');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL on the first case — `api.calls` is 1, `state()` is `'ready'`.

- [ ] **Step 3: Add the platform guard**

In `zms-fe/src/app/core/data/animal-store.ts`, add the imports and the guard. One guard in the store keeps `animal-list.ts:88` and `animal-detail.ts:76` untouched:

```ts
import { PLATFORM_ID, Service, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
```

Inside the class, next to the other injected members:

```ts
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
```

And at the top of `load()`:

```ts
  async load(): Promise<void> {
    // The server has no token and nothing to hydrate from; the browser loads instead.
    if (!this.isBrowser) {
      return;
    }
    if (this.state() === 'loading' || this.state() === 'ready') {
      return;
    }
    ...
```

- [ ] **Step 4: Run the test to verify it passes**

Run from `zms-fe/`: `pnpm test`
Expected: PASS.

- [ ] **Step 5: Stop prerendering the roster**

In `zms-fe/src/app/app.routes.server.ts`, change the `/animals` entry:

```ts
import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Client-rendered: the roster needs a token in live mode, and prerendering it in
  // demo mode would bake a snapshot of the mock into the HTML.
  { path: 'animals', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Server },
];
```

- [ ] **Step 6: Verify the build no longer prerenders animal data**

Run from `zms-fe/`: `pnpm build`
Expected: succeeds, and `dist/zms-fe/browser/animals/index.html` either is absent or contains no animal names. Check with:

```bash
grep -rl "Kibo" zms-fe/dist || echo "no prerendered roster"
```

Expected: `no prerendered roster`.

- [ ] **Step 7: Commit**

```bash
git add zms-fe/src/app/core/data/animal-store.ts zms-fe/src/app/core/data/animal-store.spec.ts \
        zms-fe/src/app/app.routes.server.ts
git commit -m "fix(zms-fe): keep the roster load off the server platform"
```

---

### Task 6: Backend seed and realm corrections

Makes live mode show the same roster as demo mode, under the same staff names, and closes the PKCE gap on the public client. Independent of every frontend task; can be reviewed on its own.

**Files:**
- Create: `zms-be/animal-service/src/main/resources/db/dev/R__seed_demo_animals.sql`
- Modify: `zms-be/animal-service/src/main/resources/application.properties`
- Modify: `zms-be/infrastructure/keycloak/realm-export.json`

**Interfaces:**
- Consumes: the enclosure ids from `enclosure-directory.ts` and the roster in `demo-data.ts`.
- Produces: 19 rows in `animals` on the `%dev` profile; realm users `admin.rossi`, `vet.bianchi`, `keeper.conti`.

- [ ] **Step 1: Write the seed migration**

Create `zms-be/animal-service/src/main/resources/db/dev/R__seed_demo_animals.sql`. A repeatable migration, not a versioned one: it lives outside `db/migration` and must never collide with a future `V4`.

```sql
-- Development seed. Mirrors zms-fe/src/app/core/data/demo-data.ts so that the demo
-- build and the live build show the same roster. Loaded only by the %dev profile,
-- via quarkus.flyway.locations. Every name here is fictional.
INSERT INTO animals (id, name, species, dangerous, habitat, enclosure_id, arrival_date, status, created_by, updated_by, version) VALUES
('7f3a9c21-5b64-4e1d-8a2f-0c9b7d6e5a41', 'Kibo', 'Reticulated giraffe', FALSE, 'TERRESTRIAL', '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', '2019-04-11', 'HEALTHY', 'admin.rossi', 'keeper.conti', 0),
('a21c8e4f-6d73-4b2a-9e1c-3f5a7b9d1c62', 'Amani', 'Plains zebra', FALSE, 'TERRESTRIAL', '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', '2021-09-02', 'HEALTHY', 'admin.rossi', 'admin.rossi', 0),
('c43e9a1b-7f82-4c3b-8d2e-4a6b8c0e2d73', 'Nia', 'Plains zebra', FALSE, 'TERRESTRIAL', '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', '2022-03-18', 'UNDER_OBSERVATION', 'admin.rossi', 'vet.bianchi', 0),
('e65b2c3d-8a91-4d4c-9f3a-5b7c9d1f3e84', 'Tamu', 'Common ostrich', FALSE, 'TERRESTRIAL', '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', '2020-06-27', 'HEALTHY', 'admin.rossi', 'keeper.conti', 0),
('19d4e5f6-0b2a-4e5d-8a4b-6c8d0e2a4f95', 'Bruno', 'Aldabra giant tortoise', FALSE, 'TERRESTRIAL', '0b6e2f1a-3c4d-4e8f-9a1b-2c3d4e5f6a70', '1998-05-14', 'DECEASED', 'admin.rossi', 'vet.bianchi', 0),
('3b5f6a7c-1c3b-4f6e-9b5c-7d9e1f3b5a06', 'Zuri', 'African lion', TRUE, 'TERRESTRIAL', '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81', '2018-11-03', 'UNDER_OBSERVATION', 'admin.rossi', 'vet.bianchi', 0),
('5d7a8b9e-2d4c-4a7f-8c6d-8e0f2a4c6b17', 'Asha', 'Sumatran tiger', TRUE, 'TERRESTRIAL', '1c7f3a2b-4d5e-4f90-8b2c-3d4e5f6a7b81', '2020-01-22', 'HEALTHY', 'admin.rossi', 'keeper.conti', 0),
('6e8b9cad-3e5d-4b80-9d7e-9f1a3b5d7c28', 'Mika', 'Bornean orangutan', FALSE, 'TERRESTRIAL', '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92', '2017-08-09', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0),
('81a2b3c4-4f6e-4c91-8e8f-0a2b4c6e8d39', 'Lulu', 'Ring-tailed lemur', FALSE, 'TERRESTRIAL', '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92', '2023-02-14', 'HEALTHY', 'admin.rossi', 'admin.rossi', 0),
('92b3c4d5-5a7f-4da2-9f9a-1b3c5d7f9e4a', 'Pepe', 'Ring-tailed lemur', FALSE, 'TERRESTRIAL', '2d8a4b3c-5e6f-4a01-9c3d-4e5f6a7b8c92', '2023-02-14', 'IN_TREATMENT', 'admin.rossi', 'vet.bianchi', 0),
('b4d5e6f7-6b8a-4eb3-8a0b-2c4d6e8a0f5b', 'Marea', 'California sea lion', FALSE, 'AQUATIC', '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3', '2016-07-30', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0),
('d6f7a8b9-7c9b-4fc4-9b1c-3d5e7f9b1a6c', 'Onda', 'Harbour seal', FALSE, 'AQUATIC', '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3', '2021-05-19', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0),
('f8a9b0c1-8dac-40d5-8c2d-4e6f8a0c2b7d', 'Blu', 'Humboldt penguin', FALSE, 'AQUATIC', '3e9b5c4d-6f7a-4b12-8d4e-5f6a7b8c9da3', '2022-10-08', 'UNDER_OBSERVATION', 'admin.rossi', 'vet.marino', 0),
('0a1b2c3d-9ebd-41e6-9d3e-5f7a9b1d3c8e', 'Gorgo', 'Nile crocodile', TRUE, 'AMPHIBIOUS', '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4', '2015-03-01', 'HEALTHY', 'admin.rossi', 'keeper.conti', 0),
('2c3d4e5f-0fce-42f7-8e4f-6a8b0c2e4d9f', 'Sibilla', 'Green anaconda', TRUE, 'AMPHIBIOUS', '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4', '2019-12-12', 'IN_TREATMENT', 'admin.rossi', 'vet.marino', 0),
('4e5f6a7b-1adf-4308-9f5a-7b9c1d3f5ea0', 'Rana', 'Smoky jungle frog', FALSE, 'AMPHIBIOUS', '4fac6d5e-7a8b-4c23-9e5f-6a7b8c9d0eb4', '2024-04-05', 'HEALTHY', 'admin.rossi', 'admin.rossi', 0),
('6a7b8c9d-2bea-4419-8a6b-8c0d2e4a6fb1', 'Pina', 'Eurasian otter', FALSE, 'AMPHIBIOUS', '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5', '2020-09-15', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0),
('8c9dae0f-3cfb-452a-9b7c-9d1e3f5b7ac2', 'Ciro', 'Capybara', FALSE, 'AMPHIBIOUS', '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5', '2021-11-23', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0),
('9daebf10-4d0c-463b-8c8d-0e2f4a6c8bd3', 'Fiamma', 'Chilean flamingo', FALSE, 'AMPHIBIOUS', '5abd7e6f-8b9c-4d34-8f6a-7b8c9d0e1fc5', '2018-04-29', 'HEALTHY', 'admin.rossi', 'keeper.gallo', 0)
ON CONFLICT (id) DO NOTHING;
```

`ON CONFLICT (id) DO NOTHING` matters twice: a repeatable migration re-runs whenever its checksum changes, and edits made through the UI must survive a restart.

`keeper.gallo` and `vet.marino` appear as historical authors only. They are not realm users and never sign in — they exist so the audit trail does not read as if three people ran the whole zoo.

- [ ] **Step 2: Wire the dev-only Flyway location**

In `zms-be/animal-service/src/main/resources/application.properties`, add one line to the `%dev` block, directly under `%dev.quarkus.flyway.migrate-at-start=true`:

```properties
# Demo roster for local development only. %prod and %test keep the default location.
%dev.quarkus.flyway.locations=db/migration,db/dev
```

Do not add the equivalent to `%prod` or `%test`.

- [ ] **Step 3: Verify the backend still builds and tests green**

Run from `zms-be/`: `./mvnw verify`
Expected: BUILD SUCCESS. The integration tests run under `%test`, which never sees `db/dev`, so no test count changes.

- [ ] **Step 4: Enforce PKCE on the public frontend client**

In `zms-be/infrastructure/keycloak/realm-export.json`, find the client whose `clientId` is `zms-fe` and add an `attributes` object to it:

```jsonc
"attributes": {
  "pkce.code.challenge.method": "S256"
}
```

The client is public (`"publicClient": true`), so without this the realm will exchange an authorization code with no verifier.

- [ ] **Step 5: Rename the realm users to the demo staff**

In the same file, in the `users` array, rename the three usernames so a live write is attributed to the same person the demo mode shows:

| Current `username` | New `username` | Realm role |
|---|---|---|
| `admin` | `admin.rossi` | `zoo-admin` |
| `vet` | `vet.bianchi` | `zoo-vet` |
| `keeper` | `keeper.conti` | `zoo-keeper` |

Change only the `username` field on each user. Leave `realmRoles`, credentials and every other field as they are.

- [ ] **Step 6: Verify the realm imports and seeds**

```bash
cd zms-be/infrastructure && docker compose down -v && docker compose up -d
```

Wait for Keycloak to report started, then confirm the three users exist with the new names at `http://localhost:8081` (admin console, realm `zoo`).

Then, from `zms-be/animal-service/`:

```bash
./mvnw quarkus:dev
```

Expected: Flyway logs applying `R__seed_demo_animals`. Confirm the rows:

```bash
docker compose -f zms-be/infrastructure/docker-compose.yml exec postgres-animal \
  psql -U zoo -d animal_db -c "SELECT count(*) FROM animals;"
```

Expected: `19`.

- [ ] **Step 7: Commit**

```bash
git add zms-be/animal-service/src/main/resources/db/dev \
        zms-be/animal-service/src/main/resources/application.properties \
        zms-be/infrastructure/keycloak/realm-export.json
git commit -m "feat(animal-service): seed the demo roster for local development" \
           -m "Adds a dev-only repeatable migration mirroring the frontend demo data, so
the live build and the demo build show the same roster.

Also enforces PKCE on the public zms-fe client, which set no
pkce.code.challenge.method attribute, and renames the three realm users to the
demo staff names so authorship reads the same in both modes."
```

---

### Task 7: The Keycloak session

The live half of the `Session` port, plus the providers that only the browser bundle gets.

**Files:**
- Create: `zms-fe/src/app/core/session/keycloak-session.ts`
- Create: `zms-fe/src/app/core/session/keycloak-session.spec.ts`
- Create: `zms-fe/src/app/core/auth/keycloak-providers.ts`
- Create: `zms-fe/public/silent-check-sso.html`
- Modify: `zms-fe/src/main.ts`
- Modify: `zms-fe/src/app/app.config.ts`
- Modify: `zms-fe/src/app/app.routes.ts`
- Modify: `zms-fe/package.json`

**Interfaces:**
- Consumes: `Session` (Task 3), `HttpAnimalApi` (Task 4), `environment` (Task 1).
- Produces: `KeycloakSession extends Session`; `rolesFrom(roles: readonly string[] | undefined): ZooRole` exported for testing; `keycloakProviders(): (Provider | EnvironmentProviders)[]` and `animalRouteGuard: CanActivateFn` from `core/auth/keycloak-providers`.

- [ ] **Step 1: Install the dependencies**

Run from `zms-fe/`:

```bash
pnpm add keycloak-angular@^22 keycloak-js@^26
```

`keycloak-angular@22` declares `@angular/core ^22` and `keycloak-js ^18 || ... || ^26`; the compose file runs Keycloak 26.0.

- [ ] **Step 2: Write the failing test**

Create `zms-fe/src/app/core/session/keycloak-session.spec.ts`:

```ts
import { rolesFrom } from './keycloak-session';

describe('rolesFrom', () => {
  it('reads the single zoo role from the realm roles', () => {
    expect(rolesFrom(['zoo-keeper'])).toBe('zoo-keeper');
    expect(rolesFrom(['zoo-vet'])).toBe('zoo-vet');
    expect(rolesFrom(['zoo-admin'])).toBe('zoo-admin');
  });

  it('ignores realm roles that are not zoo roles', () => {
    expect(rolesFrom(['offline_access', 'default-roles-zoo', 'zoo-vet'])).toBe('zoo-vet');
  });

  it('prefers the widest role when a user holds several', () => {
    expect(rolesFrom(['zoo-keeper', 'zoo-admin'])).toBe('zoo-admin');
    expect(rolesFrom(['zoo-keeper', 'zoo-vet'])).toBe('zoo-vet');
  });

  it('falls back to the narrowest role when none is present', () => {
    expect(rolesFrom([])).toBe('zoo-keeper');
    expect(rolesFrom(undefined)).toBe('zoo-keeper');
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run from `zms-fe/`: `pnpm test`
Expected: FAIL — cannot resolve `./keycloak-session`.

- [ ] **Step 4: Write the session adapter**

Create `zms-fe/src/app/core/session/keycloak-session.ts`:

```ts
import { computed, inject, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { KEYCLOAK_EVENT_SIGNAL } from 'keycloak-angular';
import { ZooRole } from '../models/animal';
import { Permission, can } from '../models/permissions';
import { Session } from './session';

/** Widest first: the UI shows one role, a Keycloak user may hold several. */
const ROLE_PRECEDENCE: readonly ZooRole[] = ['zoo-admin', 'zoo-vet', 'zoo-keeper'];

/**
 * Reduces `realm_access.roles` to the one role the UI works with. A user with no zoo
 * role at all is treated as a keeper, the least privileged: the backend rejects
 * anything they are not entitled to anyway, and the permission matrix only decides
 * which controls are offered.
 */
export function rolesFrom(roles: readonly string[] | undefined): ZooRole {
  return ROLE_PRECEDENCE.find((role) => roles?.includes(role)) ?? 'zoo-keeper';
}

interface ZooToken {
  readonly preferred_username?: string;
  readonly realm_access?: { readonly roles?: readonly string[] };
}

/** Live session: role and username come from the access token, never from the UI. */
export class KeycloakSession extends Session {
  private readonly keycloak = inject(Keycloak);
  /** Re-reads the token whenever keycloak-angular emits (ready, refresh, logout). */
  private readonly events = inject(KEYCLOAK_EVENT_SIGNAL);

  private readonly token = computed<ZooToken>(() => {
    this.events();
    return (this.keycloak.tokenParsed ?? {}) as ZooToken;
  });

  readonly role = computed(() => rolesFrom(this.token().realm_access?.roles));
  readonly username = computed(() => this.token().preferred_username ?? 'unknown');
  readonly canSwitchRole = false;

  can(permission: Permission): boolean {
    return can(this.role(), permission);
  }

  setRole(): void {
    throw new Error('The role comes from the access token and cannot be switched.');
  }

  /** Nothing to restore: the token is the source of truth. */
  restore(): void {}

  signOut(): void {
    void this.keycloak.logout({ redirectUri: window.location.origin });
  }
}
```

If `KEYCLOAK_EVENT_SIGNAL` cannot be injected outside an injection context in your build, replace the `events` field with a plain `signal(0)` bumped from an `effect` registered in `keycloakProviders()`; the rest of the class is unchanged. Confirm against the `keycloak-angular` docs before substituting.

- [ ] **Step 5: Run the test to verify it passes**

Run from `zms-fe/`: `pnpm test`
Expected: PASS — the four `rolesFrom` cases. The class itself is covered by the manual pass in Task 8.

- [ ] **Step 6: Write the providers**

Create `zms-fe/src/app/core/auth/keycloak-providers.ts`:

```ts
import { EnvironmentProviders, Provider, inject } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import Keycloak from 'keycloak-js';
import {
  AuthGuardData,
  AutoRefreshTokenService,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  UserActivityService,
  createAuthGuard,
  createInterceptorCondition,
  includeBearerTokenInterceptor,
  provideKeycloak,
  withAutoRefreshToken,
} from 'keycloak-angular';
import { environment } from '../../../environments/environment';
import { KeycloakSession } from '../session/keycloak-session';
import { Session } from '../session/session';

/**
 * Attach the bearer token to `animal-service` and to nothing else. Anchored at both
 * ends so a host merely starting with the API origin cannot match.
 */
const apiCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: new RegExp(`^${escapeForRegExp(environment.apiBaseUrl)}(/.*)?$`, 'i'),
  bearerPrefix: 'Bearer',
});

function escapeForRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Browser-only providers. They must not reach `app.config.ts`, because
 * `app.config.server.ts` merges it and `keycloak-js` touches `window`.
 */
export function keycloakProviders(): (Provider | EnvironmentProviders)[] {
  const config = environment.keycloak;
  if (!config) {
    throw new Error('keycloakProviders() called without a keycloak configuration.');
  }
  return [
    provideKeycloak({
      config,
      initOptions: {
        // check-sso, not login-required: an anonymous visitor reaches the shell and the
        // route guard is what asks them to sign in.
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        redirectUri: `${window.location.origin}/`,
      },
      features: [
        // Refreshes the token before it expires while the user is active, so a keeper
        // mid-shift does not meet a 401. The 401 copy remains the terminal fallback for
        // a session that ended server-side.
        withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 300000 }),
      ],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiCondition] },
    provideHttpClient(withFetch(), withInterceptors([includeBearerTokenInterceptor])),
    { provide: Session, useClass: KeycloakSession },
  ];
}

const isSignedIn = async (
  _route: unknown,
  _state: unknown,
  authData: AuthGuardData,
): Promise<boolean | UrlTree> => {
  if (authData.authenticated) {
    return true;
  }
  // Any zoo role may read the roster; the backend enforces the rest per endpoint.
  await inject(Keycloak).login({ redirectUri: window.location.href });
  return false;
};

export const animalRouteGuard = createAuthGuard<CanActivateFn>(isSignedIn);
```

- [ ] **Step 7: Add the silent SSO page**

Create `zms-fe/public/silent-check-sso.html`. It is served at the origin root and covered by the realm's existing `http://localhost:4200/*` redirect URI:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Silent SSO check</title>
  </head>
  <body>
    <script>
      parent.postMessage(location.href, location.origin);
    </script>
  </body>
</html>
```

- [ ] **Step 8: Bind the live adapters**

`app.config.ts` binds the demo pair and, in live mode, the HTTP adapter. Auth stays out of it:

```ts
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { AnimalApi } from './core/data/animal-api';
import { HttpAnimalApi } from './core/data/http-animal-api';
import { MockAnimalApi } from './core/data/mock-animal-api';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true })),
    provideClientHydration(),
    { provide: AnimalApi, useClass: environment.live ? HttpAnimalApi : MockAnimalApi },
    // Overridden by keycloakProviders() in the browser when live. The server keeps the
    // demo session so `App` can render the shell; the platform guard in AnimalStore
    // means it never issues a request with it.
    { provide: Session, useClass: DemoSession },
  ],
};
```

`src/main.ts` adds the browser-only half:

```ts
import { mergeApplicationConfig } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { environment } from './environments/environment';
import { appConfig } from './app/app.config';
import { App } from './app/app';

async function browserConfig() {
  if (!environment.live) {
    return appConfig;
  }
  // Imported lazily so the demo bundle never pulls in keycloak-js.
  const { keycloakProviders } = await import('./app/core/auth/keycloak-providers');
  return mergeApplicationConfig(appConfig, { providers: keycloakProviders() });
}

browserConfig()
  .then((config) => bootstrapApplication(App, config))
  .catch((err) => console.error(err));
```

- [ ] **Step 9: Guard the animal routes**

In `zms-fe/src/app/app.routes.ts`, attach the guard only in live mode, so the demo build neither imports nor runs it:

```ts
import { CanActivateFn, Routes } from '@angular/router';
import { environment } from '../environments/environment';

/** Live mode only: the demo build has nobody to sign in. */
const guards: CanActivateFn[] = environment.live
  ? [(route, state) => import('./core/auth/keycloak-providers').then((m) => m.animalRouteGuard(route, state))]
  : [];

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'animals' },
  {
    path: 'animals',
    title: 'Animals · Zoo Management System',
    canActivate: guards,
    loadComponent: () =>
      import('./features/animals/animal-list/animal-list').then((m) => m.AnimalList),
  },
  {
    path: 'animals/:id',
    title: 'Animal · Zoo Management System',
    canActivate: guards,
    loadComponent: () =>
      import('./features/animals/animal-detail/animal-detail').then((m) => m.AnimalDetail),
  },
  { path: '**', redirectTo: 'animals' },
];
```

The dynamic import inside the guard keeps `keycloak-angular` out of the demo bundle. If the returned promise's type does not satisfy `CanActivateFn`, wrap it with an explicit `Promise<boolean | UrlTree>` return annotation rather than importing the guard at module scope.

- [ ] **Step 10: Verify both bundles**

Run from `zms-fe/`: `pnpm test`
Expected: PASS.

Run from `zms-fe/`: `pnpm build`
Expected: succeeds, and the demo bundle contains no Keycloak code:

```bash
grep -rl "keycloak" zms-fe/dist/zms-fe/browser || echo "no keycloak in the demo bundle"
```

Expected: `no keycloak in the demo bundle`.

Run from `zms-fe/`: `pnpm exec ng build --configuration live`
Expected: succeeds.

- [ ] **Step 11: Commit**

```bash
git add zms-fe/src/app/core/auth zms-fe/src/app/core/session/keycloak-session.ts \
        zms-fe/src/app/core/session/keycloak-session.spec.ts zms-fe/public/silent-check-sso.html \
        zms-fe/src/main.ts zms-fe/src/app/app.config.ts zms-fe/src/app/app.routes.ts \
        zms-fe/package.json zms-fe/pnpm-lock.yaml
git commit -m "feat(zms-fe): sign in through Keycloak in live mode"
```

---

### Task 8: The header, and the live pass

The last visible difference between the two modes, then the verification that the whole thing works against a running stack.

**Files:**
- Modify: `zms-fe/src/app/app.ts`
- Modify: `zms-fe/src/app/app.html`
- Modify: `zms-fe/src/app/app.scss`
- Modify: `zms-fe/src/app/core/ui/role-select/role-select.ts`
- Modify: `zms-be/CLAUDE.md` or `zms-fe/CLAUDE.md` (whichever documents how to run the app)

**Interfaces:**
- Consumes: `Session.canSwitchRole`, `Session.username`, `Session.signOut()`.
- Produces: no new exports.

- [ ] **Step 1: Expose the mode and sign-out on `App`**

In `zms-fe/src/app/app.ts`, add to the class body:

```ts
  protected readonly canSwitchRole = this.session.canSwitchRole;

  protected signOut(): void {
    this.session.signOut();
  }
```

`session` is already injected at the top of the class.

- [ ] **Step 2: Swap the control in the header**

In `zms-fe/src/app/app.html`, replace `<app-role-select />` with:

```html
    @if (canSwitchRole) {
      <app-role-select />
    } @else {
      <span class="account">
        <span class="account-name">{{ session.username() }}</span>
        <button type="button" class="btn btn-quiet" (click)="signOut()">Sign out</button>
      </span>
    }
```

And replace the footer paragraph, which claims the role switch stands in for sign-in:

```html
<footer class="footer">
  @if (canSwitchRole) {
    <p>
      Demo data. Every animal, enclosure and staff name here is fictional. The role switch stands in
      for Keycloak sign-in; the same rules as <code>animal-service</code> apply.
    </p>
  } @else {
    <p>
      Signed in through Keycloak. Every animal, enclosure and staff name is fictional;
      <code>animal-service</code> enforces the rules.
    </p>
  }
</footer>
```

Add a minimal rule to `zms-fe/src/app/app.scss`, alongside the existing `.controls` rules:

```scss
.account {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.account-name {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
```

- [ ] **Step 3: Verify the demo header is unchanged**

Run from `zms-fe/`: `pnpm test && pnpm build`
Expected: PASS and a successful build.

Run from `zms-fe/`: `pnpm start`, then open `http://localhost:4200/animals`.
Expected: the roster loads from the mock, the role switcher works, no console errors, no network calls to port 8080.

- [ ] **Step 4: Bring up the live stack**

```bash
cd zms-be/infrastructure && docker compose up -d
```

In a second shell, from `zms-be/animal-service/`:

```bash
./mvnw quarkus:dev
```

In a third, from `zms-fe/`:

```bash
pnpm start:live
```

- [ ] **Step 5: Walk the live checks**

Open `http://localhost:4200/animals`. Confirm, one by one:

1. The app redirects to Keycloak, and signing in as `keeper.conti` returns to the roster.
2. The roster shows 19 animals, grouped under the enclosure names from the directory.
3. The header shows `keeper.conti` and a working Sign out.
4. As `keeper.conti`: the transfer action is offered, the status action is not. Transfer Kibo to Big Cat Ridge; the plate moves and `updatedBy` becomes `keeper.conti`.
5. Transferring Bruno (`DECEASED`) surfaces the 400 copy naming Bruno, not `Cannot transfer a deceased animal`.
6. Sign out, sign in as `vet.bianchi`: the status action is offered, transfer is not. Set Nia to `IN_TREATMENT`; setting it again surfaces the 422 copy.
7. In devtools, confirm the `Authorization: Bearer` header is on requests to `localhost:8080` and on nothing else.
8. Stop `quarkus:dev` and reload: the roster shows the generic failure copy, not a blank screen.

Fix anything that fails before continuing. A failure here is a bug in an earlier task, not a new task.

- [ ] **Step 6: Document how to run both modes**

Add to the frontend's `CLAUDE.md` (or `README.md` if the repo prefers that for run instructions) a short section:

```markdown
## Running

- `pnpm start` — demo mode. In-memory data, role switcher in the header, no backend needed.
- `pnpm start:live` — live mode against `animal-service` on :8080 and Keycloak on :8081.
  Requires `docker compose up -d` in `zms-be/infrastructure` and `./mvnw quarkus:dev` in
  `zms-be/animal-service`. Sign in as `keeper.conti`, `vet.bianchi` or `admin.rossi`.
```

- [ ] **Step 7: Commit**

```bash
git add zms-fe/src/app/app.ts zms-fe/src/app/app.html zms-fe/src/app/app.scss \
        zms-fe/src/app/core/ui/role-select/role-select.ts zms-fe/CLAUDE.md
git commit -m "feat(zms-fe): show the signed-in account in live mode"
```

---

## Verification checklist

Run from a clean tree on `feature/frontend-integration`:

- [ ] `cd zms-fe && pnpm test` — passes, including the five new suites (`api-errors`, `demo-session`, `http-animal-api`, `animal-store`, `keycloak-session`).
- [ ] `cd zms-fe && pnpm build` — succeeds; `grep -rl keycloak dist/zms-fe/browser` finds nothing.
- [ ] `cd zms-fe && pnpm exec ng build --configuration live` — succeeds.
- [ ] `grep -rl "Kibo" zms-fe/dist` — finds nothing; the roster is no longer prerendered.
- [ ] `cd zms-be && ./mvnw verify` — BUILD SUCCESS, test count unchanged from `c6ab8b0`.
- [ ] `pnpm start` with no backend running — roster loads, role switcher works.
- [ ] `pnpm start:live` against the stack — every check in Task 8 Step 5 passes.
- [ ] `git grep -n "DEMO_ENCLOSURES"` — no hits.
- [ ] `git status` — clean; no `.env`, no secret, no `test-output.log` added.
