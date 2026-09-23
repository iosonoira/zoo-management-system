# Zoo Management System

An internal tool for zoo staff: one audited record of who each animal is, where it lives and how it is doing, shared by keepers, vets and administrators.

It is a portfolio project. The backend is a set of Quarkus microservices built on a strict hexagonal architecture; the frontend is an Angular app that runs either on in-memory demo data or against the real backend and Keycloak.

| Folder | What it is | Details |
|---|---|---|
| [`zms-be/`](zms-be/) | Java 21 / Quarkus microservices, Postgres, Kafka, Keycloak | [Backend README](zms-be/README.md) |
| [`zms-fe/`](zms-fe/) | Angular 22 frontend (SSR) | [Frontend README](zms-fe/README.md) |

## Architecture at a glance

```
zms-fe (Angular) ──REST + JWT──▶ animal-service ──outbox──▶ Kafka ──▶ notification-service
        │                        health-service
        └──── login (PKCE) ────▶ Keycloak (realm "zoo")
```

- **animal-service**: registers animals, changes their status, transfers them between enclosures. Publishes domain events through a transactional outbox.
- **health-service**: medical records and treatments. Refers to animals by id only, with no runtime call to animal-service.
- **notification-service**: consumes animal events idempotently. No REST API yet.
- **feeding-service**: planned, not started.

## Run it

### Demo mode: frontend only, no backend

Needs Node and pnpm.

```bash
cd zms-fe
pnpm install
pnpm start
```

Open http://localhost:4200. Data lives in memory and a role switcher in the header replaces the login. This is the mode the published portfolio build uses.

### Live mode: frontend + backend + Keycloak

Needs Docker, Java 21, Node and pnpm.

1. **Create the local env files.** No secret is committed. Copy each `env.example` to `.env` in the same folder: `zms-be/infrastructure`, `zms-be/animal-service` (and `health-service` / `notification-service` if you run them). Any value works locally, but the pairs listed in the [backend README](zms-be/README.md#local-configuration) must match.
2. **Start the infrastructure** (Postgres, Keycloak, Kafka):
   ```bash
   cd zms-be/infrastructure
   docker compose up -d
   ```
3. **Start animal-service** on :8080:
   ```bash
   cd zms-be/animal-service
   ./mvnw quarkus:dev
   ```
4. **Start the frontend in live mode** on :4200:
   ```bash
   cd zms-fe
   pnpm start:live
   ```
5. Open http://localhost:4200. The app redirects to Keycloak; sign in with one of the users below.

## Users and roles

The Keycloak realm `zoo` is imported from [`realm-export.json`](zms-be/infrastructure/keycloak/realm-export.json) with three users. They all share one password: the value of `ZOO_TEST_USER_PASSWORD` in `zms-be/infrastructure/.env`.

| User | Role | Can do |
|---|---|---|
| `admin.rossi` | `zoo-admin` | Everything: register animals (API only for now), change status, transfer, write medical records |
| `vet.bianchi` | `zoo-vet` | Read animals, change their status, write medical records and treatments |
| `keeper.conti` | `zoo-keeper` | Read animals and medical records, transfer animals between enclosures |

Every write records who made it (`createdBy` / `updatedBy`, taken from the token).

The Keycloak admin console is at http://localhost:8081 (`KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` from the same `.env`).

## Troubleshooting

**`docker compose` fails with "required variable ... is missing a value".**
`zms-be/infrastructure/.env` is missing one of the variables in `env.example`. Compose checks all services at once, so a single missing variable blocks everything.

**Login says "Invalid username or password" for users that should exist.**
Keycloak imports the realm only when it does not exist yet, and this setup keeps no Keycloak volume, so recreating the container re-imports it. Run this in `zms-be/infrastructure`:
```bash
docker compose up -d --force-recreate keycloak
```

**A service fails at startup with `password authentication failed for user "zoo"`.**
Postgres applies `POSTGRES_PASSWORD` only when its volume is first created, so changing `.env` later has no effect. Align the existing user with the current `.env` (example for the animal database):
```bash
docker exec infrastructure-postgres-animal-1 sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "ALTER USER \"$POSTGRES_USER\" PASSWORD '"'"'$POSTGRES_PASSWORD'"'"'"'
```
Or drop the volume with `docker compose down -v`, which deletes local data.
