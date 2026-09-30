# zms-be

Backend of the [Zoo Management System](../README.md): Java 21 and Quarkus microservices in a multi-module Maven build. The parent [`pom.xml`](pom.xml) manages every version.

## Services

| Service | Dev port | Database | What it does |
|---|---|---|---|
| `animal-service` | 8080 | `animal_db` on :5432 | Registers animals, changes status, transfers them between enclosures, maintains the enclosure directory. Publishes animal events to Kafka through a transactional outbox |
| `health-service` | 8082 | `health_db` on :5433 | Medical records and treatments. Consumes animal events to cancel an animal's open treatments when it is declared deceased; refers to animals by id only, with no runtime call to animal-service |
| `notification-service` | 8083 | `notification_db` on :5434 | Consumes animal events idempotently and stores notifications. No REST API yet |
| `feeding-service` | 8084 | `feeding_db` on :5435 | Feeding plans and feedings. Consumes animal events to end an animal's plans when it is declared deceased; refers to animals by id only, with no runtime call to animal-service |

Shared infrastructure lives in [`infrastructure/docker-compose.yml`](infrastructure/docker-compose.yml): the four Postgres 16 databases, Keycloak 26 on :8081 and a single-node Kafka on :9092.

## Architecture

Every service follows the same hexagonal (ports and adapters) layout, and dependencies point one way only:

```
infrastructure  →  application  →  domain
(REST, JPA,        (one class per   (models, ports, exceptions;
 Kafka, security)   use case)        no framework imports)
```

In `health-service`, `DomainPurityTest` fails the build if anything under `domain/` imports Jakarta, Quarkus, Hibernate or MapStruct. The full rules (naming, testing, what never to do) are in [`CLAUDE.md`](CLAUDE.md).

### Events

`animal-service` writes `ANIMAL_REGISTERED`, `ANIMAL_STATUS_CHANGED` and `ANIMAL_TRANSFERRED` to an `outbox_event` table in the same transaction as the change. A scheduled relay sends them to the `zoo.animal.events` topic, keyed by animal id. `notification-service`, `feeding-service` and `health-service` each consume them independently at-least-once, with their own consumer group and DLQ. `notification-service` skips duplicates by event id and sends records it cannot read to `zoo.animal.events.dlq`; `feeding-service` acts only on an `ANIMAL_STATUS_CHANGED` event with `newStatus = DECEASED`, skips it if the animal is already recorded, and sends records it cannot process to `zoo.animal.events.feeding.dlq`; `health-service` also acts only on an `ANIMAL_STATUS_CHANGED` event with `newStatus = DECEASED`, skips it if the animal is already recorded, cancels the animal's `PRESCRIBED` and `ACTIVE` treatments, and sends records it cannot process to `zoo.animal.events.health.dlq`. Details: [docs/events.md](../docs/events.md).

## Local configuration

No secret is committed and there are no `.env` templates. Create a git-ignored `.env` in each of these folders, with the variables listed in the root README ([Live mode](../README.md#live-mode-frontend--backend--keycloak), step 1):

- `infrastructure/`, read by Docker Compose
- `animal-service/`, `health-service/`, `feeding-service/`, `notification-service/`, read by Quarkus in dev mode

Any value works locally, but these pairs must match, or the service cannot connect:

| In `infrastructure/.env` | Must equal |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `animal-service/.env` |
| `OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` in `animal-service/.env` |
| `POSTGRES_HEALTH_USER` / `POSTGRES_HEALTH_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `health-service/.env` |
| `HEALTH_OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` in `health-service/.env` |
| `POSTGRES_FEEDING_USER` / `POSTGRES_FEEDING_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `feeding-service/.env` |
| `FEEDING_OIDC_CLIENT_SECRET` | `OIDC_CLIENT_SECRET` in `feeding-service/.env` |
| `POSTGRES_NOTIFICATION_USER` / `POSTGRES_NOTIFICATION_PASSWORD` | `DB_USERNAME` / `DB_PASSWORD` in `notification-service/.env` |

Postgres applies a password only when its volume is first created. If you change it later, see [Troubleshooting](../README.md#troubleshooting).

`ZOO_TEST_USER_PASSWORD` is the password of the three Keycloak users; see [Users and roles](../README.md#users-and-roles).

## Run

```bash
cd infrastructure
docker compose up -d

cd ../animal-service
./mvnw quarkus:dev
```

Use the same `./mvnw quarkus:dev` in `health-service`, `feeding-service` or `notification-service`. On Windows use `mvnw.cmd`.

In dev mode, `animal-service` loads 19 sample animals and 7 sample enclosures, and exposes Swagger UI at http://localhost:8080/q/swagger-ui. Every endpoint needs a bearer token from the `zoo` realm; without one it answers 401.

## Security

Authentication is OIDC against Keycloak, and authorization uses realm roles on each endpoint:

| Endpoint | Admin | Vet | Keeper |
|---|:-:|:-:|:-:|
| `POST /animals` | ✓ | | |
| `GET /animals`, `GET /animals/{id}` | ✓ | ✓ | ✓ |
| `GET /enclosures` | ✓ | ✓ | ✓ |
| `PUT /animals/{id}/status` | ✓ | ✓ | |
| `PUT /animals/{id}/transfer` | ✓ | | ✓ |
| `POST /medical-records`, `POST /medical-records/{id}/treatments` | ✓ | ✓ | |
| `GET /medical-records`, `GET /medical-records/{id}` | ✓ | ✓ | ✓ |
| `PUT /treatments/{id}/status` | ✓ | ✓ | |
| `POST /feeding-plans`, `PUT /feeding-plans/{id}/status` | ✓ | ✓ | |
| `GET /feeding-plans`, `GET /feeding-plans/{id}`, `GET /feeding-plans/{id}/feedings` | ✓ | ✓ | ✓ |
| `POST /feeding-plans/{id}/feedings` | ✓ | | ✓ |

A missing token returns 401 and a wrong role returns 403, both with a JSON body. Writes record the acting user in `createdBy` / `updatedBy`. Attempting to register or transfer an animal to an unknown enclosure returns 400 with an error message.

## Tests

Run from a service folder:

```bash
./mvnw test     # unit tests (JUnit 5 + Mockito)
./mvnw verify   # unit + integration tests (*IT, real Postgres via Testcontainers)
```

Integration tests need Docker running. Surefire skips `*IT` classes, so only `verify` runs them; CI runs `verify` on every service ([`backend-ci.yml`](../.github/workflows/backend-ci.yml)). Test enclosures are loaded from a separate Flyway location (`db/test`) in the test profile.
