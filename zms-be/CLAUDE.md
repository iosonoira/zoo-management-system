# ZMS Backend

## Stato attuale e prossimi passi

### Completato — Security (fase 6)

- `infrastructure/security/ZooRoles.java` — costanti dei 3 realm role
- `@RolesAllowed` per endpoint su `AnimalResource` (matrice: POST=admin, GET=tutti,
  status=vet+admin, transfer=keeper+admin)
- `SecurityExceptionMapper` — 401/403 con lo stesso body degli altri errori
- Audit dell''attore: `performedBy` nelle firme dei use case di scrittura,
  colonne `created_by`/`updated_by` (migration `V2`)
- OIDC attivo solo in `%dev` contro Keycloak (`zms-be/infrastructure/keycloak/realm-export.json`),
  `quarkus.oidc.enabled=false` resta il default per `%test`
- Test: `@TestSecurity` su `AnimalResourceIT`, matrice di autorizzazione in `AnimalSecurityIT`

### Prossima fase

- `infrastructure/event/` — Kafka producer per eventi animale
- Servizi restanti: `health-service`, `feeding-service`, `notification-service`
