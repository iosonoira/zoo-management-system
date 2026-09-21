# Frontend Integration Design

**Date:** 2026-09-21
**Phase:** 8 (first phase connecting `zms-fe` to a running backend)
**Scope:** Give `zms-fe` a real HTTP adapter for `animal-service` and a real Keycloak
session, without losing the offline demo mode the portfolio depends on. `health-service`
stays disconnected.

---

## Context

`zms-be/animal-service` is complete: five REST endpoints under `/animals`, OIDC with
`@RolesAllowed`, actor auditing, optimistic locking, pagination. `health-service` shipped
on 2026-09-20 with its own database and Keycloak client.

`zms-fe` is an Angular 22 SSR application that has never made an HTTP call. It was built
against a port, `AnimalApi` (`src/app/core/data/animal-api.ts`), with a single in-memory
adapter, `MockAnimalApi`, that deliberately reproduces the backend's rules and status
codes. `Session` (`src/app/core/session/session.ts`) is a demo stand-in that holds a role
picked from a switcher in the header and persists it in `localStorage`. The comment on
`AnimalApi` states the intent explicitly: "an HttpClient + OIDC adapter can replace it
without touching the UI." This phase is that replacement.

Three facts from the existing code shape every decision below:

- `PRODUCT.md` names reviewers and recruiters as first-class users who "open the app cold
  to judge the quality of the work". A mandatory login screen breaks that scenario.
- `infrastructure/keycloak/realm-export.json` already declares a public client `zms-fe`
  with `redirectUris: ["http://localhost:4200/*"]` and `webOrigins:
  ["http://localhost:4200"]`. Both services already allow CORS from
  `http://localhost:4200` in their `%dev` profile. Nothing on the backend needs to be
  opened up.
- `animal-service` has no seed migration and no enclosure entity. `enclosure_id` is a bare
  `UUID` column. A freshly composed stack returns an empty roster, and the enclosure names
  the UI shows exist only in `demo-data.ts`.

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Scope | `animal-service` only | The animal screens already exist and are the whole current UI. Connecting `health-service` means designing medical-record and treatment screens that do not exist yet — a separate phase with its own design |
| Demo mode | Kept, alongside the live mode | `PRODUCT.md` makes the cold-open reviewer a primary user. Removing the mock trades that away for one less adapter behind a port that was built to have two |
| Mode selection | Build-time, via `fileReplacements` | A runtime flag would ship `keycloak-js` inside the demo bundle for nothing. Two build configurations keep the demo bundle free of auth code |
| Auth library | `keycloak-angular@22` + `keycloak-js@26` | Peer ranges match exactly (`@angular/core ^22`, `keycloak-js ^26` against the Keycloak 26.0 image in the compose file). Ships a bearer interceptor, an auth guard, and role extraction from `realm_access.roles` — the same claim path both services configure |
| `Session` | Becomes abstract, with `DemoSession` and `KeycloakSession` | The components already depend on `Session`, not on its internals. Making it a port keeps `App`, `RoleSelect` and every feature component untouched |
| Seed data | Dev-only Flyway migration, reusing the frontend's enclosure UUIDs | Both modes then show the same roster, enclosure names resolve, and the transfer sheet has a valid target list. A dev-only location keeps demo rows out of production migrations |
| Enclosure directory | Stays in the frontend, moved out of `demo-data.ts` | The backend has no enclosure entity to read from. The directory is needed by both modes, so it cannot live in a file named after demo data |
| `listAll()` | Stays flat; the adapter pages internally | `AnimalList` groups by enclosure and counts filters across the whole roster. Server-side paging in the UI would break both for no gain at zoo scale |
| Error copy | The adapter maps status codes to frontend copy; backend messages are discarded | 401/403/500 return generic strings (`"Insufficient role"`); the rest return technical domain messages (`"Cannot transfer a deceased animal"`). Neither is copy for a keeper holding a phone |
| SSR for `/animals` | `RenderMode.Client`, in both modes | Prerendering today bakes demo data into the HTML. In live mode there is no token at build time. One behaviour is easier to defend than a per-mode split |
| Realm usernames | Renamed to the demo usernames | `DEMO_USERS` says `admin.rossi`; the realm says `admin`. Aligning them keeps one authorship story across both modes, and renaming three demo users is cheaper than rewriting the frontend's fictional staff |
| PKCE | Enforced on the client via `pkce.code.challenge.method` | The client is public and currently sets no attribute, so the realm accepts a code with no verifier |

### Rejected alternatives

- **`angular-auth-oidc-client@22`**: standards-only, no vendor coupling, PKCE and silent
  renew included. Its peer range declares `@angular/core >=20` rather than `^22`, and role
  extraction from `realm_access` would be hand-written. The vendor coupling it avoids is
  coupling to the Keycloak the backend already requires.
- **Hand-rolled PKCE on `oidc-client-ts`**: no Angular peer-dependency risk and total
  control, at the cost of writing and testing redirect handling, token storage, refresh
  and silent renew.
- **Dropping `MockAnimalApi` entirely**: one code path instead of two, but the app then
  shows a login screen to anyone without Postgres and Keycloak running.
- **A runtime mode switch** read from a config endpoint: lets one build serve both, but
  puts `keycloak-js` in every bundle and adds a request before first paint.
- **Seeding in every profile**: one migration instead of a dev-only location, but demo
  rows in a production migration is exactly the kind of thing a reviewer flags.
- **An enclosure resource in `animal-service`**: the correct long-term answer and the only
  way to stop duplicating names in the frontend. It is backend work with its own domain
  design, entity, migration and endpoints — a separate phase.
- **Generating TypeScript types from `/q/openapi`**: both services expose OpenAPI, and
  generation would kill contract drift permanently. It adds a build step and a generator
  dependency to keep two enums and ten fields in sync. Worth revisiting when the frontend
  covers `health-service` too.
- **Per-mode `app.routes.server.ts` via `fileReplacements`**: keeps the demo prerender.
  Two rendering behaviours to explain and test, to preserve a first paint that the client
  render reproduces within one frame of hydration.

---

## Architecture

Two ports, two adapters each, chosen by one build-time flag.

```
                    environment.live
                           │
          ┌────────────────┴────────────────┐
       false                              true
          │                                 │
   MockAnimalApi                      HttpAnimalApi ──► animal-service :8080
   DemoSession                        KeycloakSession ──► Keycloak :8081
   (role switcher)                    (realm_access.roles)
          │                                 │
          └────────────────┬────────────────┘
                           │
                  AnimalApi / Session
                           │
            AnimalStore ──► feature components
                          (unchanged)
```

Files added:

| File | Purpose |
|---|---|
| `src/environments/environment.ts` | Demo defaults: `{ live: false, apiBaseUrl: '', keycloak: null }` |
| `src/environments/environment.live.ts` | `{ live: true, apiBaseUrl, keycloak: { url, realm: 'zoo', clientId: 'zms-fe' } }` |
| `src/app/core/data/http-animal-api.ts` | `HttpAnimalApi extends AnimalApi` |
| `src/app/core/data/enclosure-directory.ts` | `ENCLOSURES`, moved out of `demo-data.ts` |
| `src/app/core/session/demo-session.ts` | Today's `Session` body, role switcher included |
| `src/app/core/session/keycloak-session.ts` | Role and username from the token |
| `src/app/core/auth/auth-providers.ts` | Browser-only Keycloak providers |
| `public/silent-check-sso.html` | Silent SSO iframe target |

Files changed: `app.config.ts`, `main.ts`, `app.routes.ts`, `app.routes.server.ts`,
`animal-api.ts`, `animal-store.ts`, `session.ts`, `demo-data.ts`, `app.html`,
`role-select.ts`, `angular.json`, `package.json`.

Backend: one dev-only migration in `animal-service`, the Flyway location configuration
that scopes it to `%dev`, and two edits to `infrastructure/keycloak/realm-export.json`
(PKCE attribute, renamed users).

---

## Mode selection

`angular.json` gains a `live` build configuration and a matching serve configuration:

```jsonc
"live": {
  "fileReplacements": [
    { "replace": "src/environments/environment.ts",
      "with": "src/environments/environment.live.ts" }
  ]
}
```

`pnpm start` keeps serving the demo. `pnpm start --configuration live` serves against the
running stack. The default build configuration stays `production`, which resolves to the
demo environment — the deployed portfolio build is the demo one.

`app.config.ts` binds `AnimalApi` from `environment.live`. Both modes bind it, because DI
must resolve on the server too; the platform guard below is what keeps the server from
calling it.

---

## Authentication

`provideKeycloak()` runs in the browser only, with:

```ts
initOptions: {
  onLoad: 'check-sso',
  pkceMethod: 'S256',
  silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
}
```

`check-sso` rather than `login-required`: an already-authenticated session is picked up
silently, and an anonymous visitor reaches the app shell instead of being bounced to
Keycloak before anything renders. `createAuthGuard` on the animal routes is what triggers
the login redirect, so the redirect happens for a reason the user can see.

`includeBearerTokenInterceptor` is configured with a URL condition matching
`environment.apiBaseUrl` only. The token is never attached to any other origin.

The `withAutoRefreshToken` feature keeps the token fresh while the user is active and
logs out after inactivity, so a keeper mid-shift does not meet a 401. A 401 that still
arrives means the session ended server-side, and the copy says so.

`KeycloakSession` reads `realm_access.roles` and reduces it to a single `ZooRole` by
precedence `zoo-admin` > `zoo-vet` > `zoo-keeper`, because the UI is built around one
active role while a Keycloak user can hold several. `username` comes from the token's
`preferred_username` claim. The backend writes `identity.getPrincipal().getName()`
(`AnimalResource.java:107`) into `createdBy` / `updatedBy`; Quarkus OIDC resolves that
principal from `upn`, falling back to `preferred_username`. No realm user sets `upn`, so
the two agree — the frontend must not claim authorship it did not observe, and the value
it displays after a write is the one the response returns, not the one it sent.
`setRole()` throws in live mode: the role belongs to the token.

The header swaps the role switcher for the username and a logout control when
`environment.live` is true.

### Realm changes required

The `zms-fe` client exists as a public client with the right redirect URIs and web
origins, and the wildcard covers the silent-SSO iframe. Two gaps found while reviewing
`realm-export.json`:

- **The client has no `attributes` block**, so `pkce.code.challenge.method` is unset and
  the realm does not enforce PKCE. A public client without enforced PKCE accepts an
  authorization code without a verifier, which is the attack PKCE exists to stop. Add
  `"attributes": { "pkce.code.challenge.method": "S256" }` to the `zms-fe` client.
- **The realm users are `admin`, `vet` and `keeper`**, while `DEMO_USERS` in
  `session.ts` uses `admin.rossi`, `vet.bianchi` and `keeper.conti`. Left alone, the two
  modes attribute the same edits to different people. Rename the three realm users to the
  demo usernames so one story holds across both modes.

Realm roles are declared and assigned (`admin` → `zoo-admin`, `vet` → `zoo-vet`,
`keeper` → `zoo-keeper`), and both services already read them through
`roles.role-claim-path=realm_access/roles`.

---

## HTTP contract

`AnimalResponse` and the frontend's `Animal` interface already share every field name.
`arrivalDate` is a `LocalDate` and serialises as `"2019-04-11"`, which is what the
interface declares. No mapper is needed — the adapter types the response and returns it.

| Port method | Request |
|---|---|
| `listAll()` | `GET /animals?page=N&size=100`, repeated while accumulated `< total` |
| `getById(id)` | `GET /animals/{id}` |
| `updateStatus(id, status)` | `PUT /animals/{id}/status`, body `{ status }` |
| `transfer(id, enclosureId)` | `PUT /animals/{id}/transfer`, body `{ targetEnclosureId }` |
| `listEnclosures()` | No request; returns `ENCLOSURES` |

`size=100` is `ListAnimalsUseCase.MAX_PAGE_SIZE`; a larger value is rejected with 400.

`provideHttpClient(withFetch())`, because the server build runs under Node and `withFetch`
avoids the XHR shim.

---

## Error mapping

Every mapper in `animal-service` returns `{"message": "..."}`. The status codes actually
reachable are wider than `ApiError` currently declares:

| Status | Source | Frontend handling |
|---|---|---|
| 400 | `InvalidAnimalDataExceptionMapper` | Transfer of a deceased animal; invalid page/size |
| 401 | `UnauthorizedExceptionMapper` | **New.** Terminal: the session ended server-side, so the copy asks the user to sign in again |
| 403 | `ForbiddenExceptionMapper` | Role lacks the action — the UI should not have offered it |
| 404 | `AnimalNotFoundExceptionMapper` | Unknown id |
| 409 | `ConcurrentAnimalUpdateExceptionMapper` | **New.** Optimistic lock lost; ask the user to reload |
| 422 | `InvalidStatusTransitionExceptionMapper` | Same status, or a deceased animal |
| 500 | `UnexpectedExceptionMapper` | Generic failure |

`ApiError`'s status union widens to include 401 and 409. `HttpAnimalApi` catches
`HttpErrorResponse`, discards the backend's `message`, and throws an `ApiError` carrying
the frontend's own copy — the adapter knows which animal the call was about, so the copy
can name it, matching what `MockAnimalApi` already writes. `errorMessage()` in
`animal-store.ts` stays the single place the UI reads a message from.

409 has no existing copy because `MockAnimalApi` never raises it. The mock gains no 409
simulation: the condition needs two concurrent writers, which an in-memory single-user
adapter cannot produce honestly.

One accepted divergence: `MockAnimalApi` rejects a transfer to an unknown enclosure with
400, while `TransferAnimalService` accepts any non-null UUID because no enclosure entity
exists to check against. Since the target list comes from the frontend directory in both
modes, the case is unreachable through the UI. The mock keeps the stricter check.

---

## SSR

- `app.routes.server.ts`: `/animals` moves from `RenderMode.Prerender` to
  `RenderMode.Client`. Prerendering executes `AnimalStore.load()` at build time — against
  demo data today, against an unauthenticated backend in live mode.
- `AnimalStore.load()` gains an `isPlatformBrowser` guard and returns early on the server.
  One guard in the store rather than two in the components, which keeps
  `animal-list.ts:88` and `animal-detail.ts:76` untouched.
- `app.config.server.ts` merges `appConfig`, so anything registered there also runs on the
  server — and `keycloak-js` touches `window`. The Keycloak providers therefore go into
  `main.ts`, the browser entry point, via `mergeApplicationConfig(appConfig,
  browserConfig)`. `app.config.ts` keeps only what both platforms can run.

---

## Backend seed

`R__seed_demo_animals.sql` inserts the animals and enclosure UUIDs from `demo-data.ts`,
so both modes show the same roster and the enclosure directory resolves against live data.

It lives in a separate Flyway location, `db/dev`, wired only in the `%dev` profile:

```properties
%dev.quarkus.flyway.locations=db/migration,db/dev
```

Production and test keep the default `db/migration` and never see the seed. Because the
seed sits outside the versioned location, it must not reuse a `V` prefix that the main
location could later claim; `R__seed_demo_animals.sql` as a repeatable migration avoids
the collision entirely.

The rows carry `version = 0` and copy `created_by` / `updated_by` from `demo-data.ts`
verbatim — which is why the realm users are renamed to match. A row authored by
`admin.rossi` next to a logged-in user called `admin` would read as two different people.

---

## Testing

Vitest is already configured. New coverage:

- `HttpAnimalApi` with `provideHttpClientTesting`: the paging loop across two pages, the
  request shape of each write, and one case per mapped status code including 401 and 409.
- `KeycloakSession`: role extraction from `realm_access.roles`, the multi-role precedence
  rule, and `username` from `preferred_username`.
- `AnimalStore`: `load()` is a no-op on the server platform.
- `MockAnimalApi` keeps its current behaviour; existing component tests keep using it.

No end-to-end test against a live stack in this phase. Verification of the live mode is
manual and listed under Definition of done.

---

## Out of scope

- `health-service` in any form: no medical-record or treatment screens, no second HTTP
  adapter, no second Keycloak client in the frontend
- `POST /animals` (register): the endpoint exists and is admin-only, but no registration
  screen exists in `zms-fe` and designing one is feature work, not integration
- An enclosure entity, resource or migration in `animal-service`
- OpenAPI type generation
- Token refresh strategy beyond `keycloak-angular`'s `withAutoRefreshToken` feature,
  which refreshes while the user is active and logs out after inactivity
- Deployment, reverse proxy, or any production origin configuration
- Search, filtering or pagination in the API

---

## Definition of done

1. `pnpm start` serves the demo mode with no backend running, role switcher included, and
   no `keycloak-js` in the bundle.
2. `pnpm start --configuration live` against `docker compose up` plus
   `mvnw quarkus:dev` redirects to Keycloak on first visit to `/animals`, and returns to
   the roster authenticated.
3. The seeded roster in live mode matches the demo roster, enclosure names resolve on both
   the list and the detail screen, and a live write is attributed to the same username the
   demo mode would show.
4. A `zoo-keeper` sees the transfer action and not the status action; a `zoo-vet` the
   reverse. A forbidden call surfaces the 403 copy rather than a raw backend message.
5. Transferring a deceased animal surfaces the 400 copy; a same-status change surfaces the
   422 copy.
6. `pnpm test` passes, including the new adapter and session suites.
7. `mvnw verify` still passes from `zms-be/`, with the seed absent from the test profile.
8. No secret is added to a tracked file, and the `zms-fe` client stays public with
   `pkce.code.challenge.method = S256` enforced by the realm.
