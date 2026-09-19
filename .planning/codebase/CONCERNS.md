---
last_mapped_commit: 89d85180634495df6b49f9ccefe158aa52e17c34
last_mapped_at: 2026-09-19
---
# Codebase Concerns

**Analysis Date:** 2026-09-19

Scope: `zms-be/` only. Paths below are relative to `zms-be/`. Abbreviation: `AS` = `animal-service/src/main/java/it/zoo/animal`.

## Tech Debt

**Only one of four planned services exists:**

- Issue: `CLAUDE.md` lines 12-22 describe `health-service`, `feeding-service`, `notification-service` (all "[non iniziato]"). The parent POM declares only `<module>animal-service</module>` (`pom.xml:14-16`). The other three exist only as empty local folders (no files, not tracked by git).
- Files: `pom.xml`, `CLAUDE.md`
- Impact: Any cross-service contract (feeding/health referencing animals, notifications) is undefined. Kafka is listed in the stack (`CLAUDE.md:26`) but no Kafka dependency, broker in `infrastructure/docker-compose.yml`, or `infrastructure/event/` package exists.
- Fix approach: Add each service as a new Maven module mirroring the `animal-service` hexagonal layout; add Kafka broker to `infrastructure/docker-compose.yml` before the first event adapter.

**No domain events for animal lifecycle:**

- Issue: `CLAUDE.md:245` plans a Kafka producer in `infrastructure/event/`, but status changes and transfers only persist (`AS/application/UpdateAnimalStatusService.java:38-40`, `AS/application/TransferAnimalService.java:36-38`). No outbound port for events exists in `AS/domain/port/out/`.
- Impact: Downstream services (health, notification) have no way to react to `DECEASED`/`SICK` transitions.
- Fix approach: Add an `AnimalEventPublisher` port in `domain/port/out/`, call it from services, implement with transactional outbox to avoid dual-write.

**MapStruct version pinned in child POM against project rule:**

- Issue: `animal-service/pom.xml:20` defines `<mapstruct.version>1.5.5.Final</mapstruct.version>`; `CLAUDE.md:203` states versions must not be managed in the child POM.
- Impact: Version drift once more services add MapStruct.
- Fix approach: Move `mapstruct.version` and a `dependencyManagement` entry to `pom.xml` properties (`pom.xml:18-26`).

**Inconsistent mapping approach:**

- Issue: REST mapping uses MapStruct (`AS/infrastructure/rest/mapper/AnimalDtoMapper.java:9-13`); persistence mapping is a hand-written static utility (`AS/infrastructure/persistence/AnimalEntityMapper.java`). The static mapper must be updated by hand for every new field.
- Fix approach: Pick one; either convert `AnimalEntityMapper` to a `@Mapper(componentModel = "cdi")` or keep manual mapping and cover each field in `AnimalEntityMapperTest`.

**Anemic domain model with public setters:**

- Issue: `AS/domain/model/Animal.java:39-48` exposes setters for every field, including `id`, `status`, `createdBy`. Invariants (`canTransitionTo`, `canBeTransferred`, lines 50-60) are checked by services, not enforced by the model. Input validation lives in services (`AS/application/RegisterAnimalService.java:23-40`) and duplicates Bean Validation on `RegisterAnimalRequest`.
- Impact: Any caller can call `setStatus(...)` and bypass transition rules.
- Fix approach: Replace setters with intent methods (`changeStatus(target, actor)`, `transferTo(enclosure, actor)`) that throw domain exceptions.

**Repository named "Panache" but uses raw EntityManager:**

- Issue: `AS/infrastructure/persistence/AnimalPanacheRepository.java:15-22` injects `EntityManager` and uses `em.merge`/`em.find`; `quarkus-hibernate-orm-panache` (`animal-service/pom.xml:26`) is unused.
- Fix approach: Rename to `JpaAnimalRepository` or actually adopt `PanacheRepositoryBase<AnimalEntity, UUID>`; drop the unused dependency if not.

**Unused port method:**

- Issue: `existsById` (`AS/infrastructure/persistence/AnimalPanacheRepository.java:42-44`) has no caller in `AS/application/`.

## Known Bugs

**Status transition rule is effectively "anything except same/deceased":**

- Symptoms: `canTransitionTo` returns true for any target that differs from current status unless current is `DECEASED` (`AS/domain/model/Animal.java:50-55`). No real state machine exists (e.g. transitions back from quarantine states are unrestricted).
- Files: `AS/domain/model/Animal.java`, `AS/domain/enums/AnimalStatus.java`
- Trigger: `PUT /animals/{id}/status` with any different status.
- Workaround: None; define an explicit allowed-transition map per `AnimalStatus`.

**Wrong error semantics for transferring a deceased animal:**

- Symptoms: Returns 400 via `InvalidAnimalDataException` (`AS/application/TransferAnimalService.java:32-34`), while an invalid status transition returns 422 (`AS/infrastructure/rest/ZooExceptionMapper.java:21-23`). Both are business-rule violations on valid input.
- Files: `AS/application/TransferAnimalService.java`, `AS/infrastructure/rest/ZooExceptionMapper.java`
- Workaround: Test `AnimalResourceIT.java:160-177` locks in the 400; change test and code together.

**Transfer to same enclosure is a silent no-op write:**

- Symptoms: `TransferAnimalService.transfer` does not reject `targetEnclosureId.equals(animal.getEnclosureId())` and still updates `updatedBy` (`AS/application/TransferAnimalService.java:36-38`).
- Trigger: `PUT /animals/{id}/transfer` with current enclosure.

## Security Considerations

**Catch-all RuntimeException mapper hides errors and may shadow framework mappers:**

- Risk: `ZooExceptionMapper implements ExceptionMapper<RuntimeException>` (`AS/infrastructure/rest/ZooExceptionMapper.java:12`) returns a generic 500 (line 25) without logging the exception. Unexpected failures leave no trace. Framework `WebApplicationException`s (malformed UUID path param, unparseable JSON/enum in body) are not covered by any test and may surface as 500 instead of 400/404.
- Files: `AS/infrastructure/rest/ZooExceptionMapper.java`
- Current mitigation: Bean Validation 400 path is verified (`AnimalResourceIT.java:142-156`).
- Recommendations: Map only domain exceptions (introduce a common `ZooDomainException` base); log at ERROR before returning 500; add IT cases for bad UUID and invalid enum payload.

**OIDC disabled outside dev profile:**

- Risk: `application.properties:1` sets `quarkus.oidc.enabled=false`; only `%dev` enables it (line 2). There is no `%prod` config, so a prod build has no token verification configured and no datasource config.
- Files: `animal-service/src/main/resources/application.properties`
- Recommendations: Add `%prod` block driven by env vars (`QUARKUS_OIDC_AUTH_SERVER_URL`, `QUARKUS_DATASOURCE_*`) with OIDC enabled.

**Hard-coded dev secrets committed:**

- Risk: Client secret in `application.properties:5` and `infrastructure/keycloak/realm-export.json:21`; DB password in `application.properties:11` and `infrastructure/docker-compose.yml:7`; Keycloak admin `admin/admin` (`docker-compose.yml:16-17`); seeded users with password equal to username (`realm-export.json:35-52`). Frontend client has `directAccessGrantsEnabled: true` (`realm-export.json:28`), enabling password grant.
- Current mitigation: Values are dev-only and profile-scoped.
- Recommendations: Move to `.env` referenced by compose (`${POSTGRES_PASSWORD}`); disable direct access grants on the public client once the FE uses auth-code + PKCE.

**CORS whitelists no DELETE/PATCH, dev only:**

- Risk: `application.properties:21-24` defines CORS only under `%dev`. Low risk now; will break when DELETE endpoints are added.

**`currentActor()` assumes non-null principal:**

- Risk: `AS/infrastructure/rest/AnimalResource.java:97-99` calls `identity.getPrincipal().getName()`; safe only because every endpoint is `@RolesAllowed`. Adding a `@PermitAll` endpoint that calls it would store an empty actor, which services reject with a 400 (`RegisterAnimalService.java:23-25`).

## Performance Bottlenecks

**Unbounded list endpoint:**

- Problem: `GET /animals` loads all rows (`AS/infrastructure/persistence/AnimalPanacheRepository.java:35-38`, `AS/application/ListAnimalsService.java:19-21`). No pagination, filtering, or sorting.
- Cause: `findAll()` with no `setMaxResults`.
- Improvement path: Add `page`/`size` query params and filters (status, habitat, enclosure) to `ListAnimalsUseCase` and the repository port.

**Missing indexes:**

- Problem: `V1__create_animals_table.sql` defines only the PK. Future filters on `enclosure_id` and `status` will scan.
- Improvement path: New Flyway migration `V3__...` adding indexes when filters are introduced.

**`save` always uses `merge` (extra SELECT):**

- Problem: `AnimalPanacheRepository.java:22-26` calls `em.merge` on a freshly mapped detached entity for both insert and update, causing a SELECT per write, and the update path re-reads the row already loaded in `findById`.
- Improvement path: Keep managed entity across the transaction or use `persist` for new aggregates.

## Fragile Areas

**No optimistic locking / audit timestamps:**

- Files: `AS/infrastructure/persistence/AnimalEntity.java`, `animal-service/src/main/resources/db/migration/V2__add_audit_columns.sql`
- Why fragile: No `@Version` column; concurrent status updates (vet) and transfers (keeper) overwrite each other (last-write-wins through `merge`). Audit tracks only `created_by`/`updated_by`, no `created_at`/`updated_at` and no history.
- Safe modification: Add `version BIGINT` + `@Version` and timestamp columns via a new migration; map 409 on `OptimisticLockException`.

**Adding a field requires touching five places manually:**

- Files: `AS/domain/model/Animal.java`, `AnimalEntity.java`, `AnimalEntityMapper.java`, `rest/dto/AnimalResponse.java`, a new Flyway migration.
- Why fragile: Static mapper silently drops fields not copied.
- Test coverage: `AnimalEntityMapperTest.java` exists; extend it for every new field.

**Enclosure is an unvalidated UUID:**

- Files: `AS/domain/model/Animal.java` (`enclosureId`), `rest/dto/TransferAnimalRequest.java`
- Why fragile: No enclosure aggregate or service exists; any random UUID is accepted.

## Scaling Limits

**Single shared Postgres port and one DB container:**

- Current capacity: `infrastructure/docker-compose.yml:2-11` runs one `postgres-animal` on host port 5432.
- Limit: Additional services need their own DB/ports; host 5432 conflicts with a locally installed Postgres.
- Scaling path: One DB (or schema) per service in compose, non-default host ports.

## Dependencies at Risk

**MapStruct 1.5.5.Final:**

- Risk: Older than current 1.6.x line; pinned in child POM (`animal-service/pom.xml:20`).
- Migration plan: Bump in parent POM.

**Keycloak `start-dev` with `26.0` tag:**

- Risk: `infrastructure/docker-compose.yml:13-14` uses dev mode (H2, no TLS) and a floating minor tag.
- Migration plan: Pin patch version; separate prod config.

## Missing Critical Features

**No delete/archive, no update of core attributes:**

- Problem: `AnimalResource.java` exposes only POST, GET list, GET by id, PUT status, PUT transfer. Name/species/habitat corrections are impossible.

**No health checks, metrics, or container build config for deployment:**

- Problem: No `quarkus-smallrye-health` or `quarkus-micrometer` in `animal-service/pom.xml`; the service is not in `infrastructure/docker-compose.yml`. Dockerfiles in `animal-service/src/main/docker/` are unmodified scaffolding.
- Blocks: Orchestrated deployment and readiness probes.

**No logging in application code:**

- Problem: No logger usage in `AS/` (services, resource, mappers). Business actions are recorded only via `updated_by`.

## Test Coverage Gaps

**Integration tests depend on Dev Services (Docker) implicitly:**

- What's not tested: `%test` profile sets only `db-kind` (`application.properties:17-19`); ITs require a running Docker daemon for Testcontainers-backed Dev Services. No `src/test/resources` override exists.
- Files: `animal-service/src/test/java/it/zoo/animal/infrastructure/rest/AnimalResourceIT.java`, `AnimalSecurityIT.java`
- Risk: Tests fail on machines/CI without Docker.
- Priority: Medium

**Real OIDC token validation never exercised:**

- What's not tested: OIDC is disabled in `%test` (inherits `application.properties:1`); security tests use `@TestSecurity` (`AnimalSecurityIT.java:78-143`). Role-claim path `realm_access/roles` (`application.properties:7`) and Keycloak role names are unverified.
- Risk: Role mapping mismatch with Keycloak only caught manually.
- Priority: Medium

**Error-mapping edge cases:**

- What's not tested: Malformed UUID path, invalid enum in body, unknown JSON field, generic 500 path in `ZooExceptionMapper.java:25`, `SecurityExceptionMapper` fallback branch (`SecurityExceptionMapper.java:23`).
- Priority: Medium

**Concurrency:**

- What's not tested: Concurrent updates on the same animal (see optimistic locking above).
- Priority: Low

---

*Concerns audit: 2026-09-19*
