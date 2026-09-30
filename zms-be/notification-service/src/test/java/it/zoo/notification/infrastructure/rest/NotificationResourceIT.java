package it.zoo.notification.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.port.in.AcknowledgeNotificationUseCase;
import it.zoo.notification.infrastructure.persistence.NotificationEntity;
import it.zoo.notification.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class NotificationResourceIT {

    private static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    private static final UUID ANIMAL_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANIMAL_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static final UUID ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ID_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ID_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Inject
    EntityManager em;

    @Inject
    AcknowledgeNotificationUseCase acknowledgeNotification;

    /*
     * Seed (occurredAt desc, then id):
     *   ID_5  T0+4h  animal B  CRITICAL  open
     *   ID_3  T0+2h  animal A  WARNING   open
     *   ID_4  T0+2h  animal B  INFO      acknowledged
     *   ID_2  T0+1h  animal A  WARNING   acknowledged
     *   ID_1  T0     animal A  INFO      open
     */
    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM NotificationEntity").executeUpdate();
            em.persist(row(ID_1, ANIMAL_A, Severity.INFO, T0, false));
            em.persist(row(ID_2, ANIMAL_A, Severity.WARNING, T0.plusSeconds(3600), true));
            em.persist(row(ID_3, ANIMAL_A, Severity.WARNING, T0.plusSeconds(7200), false));
            em.persist(row(ID_4, ANIMAL_B, Severity.INFO, T0.plusSeconds(7200), true));
            em.persist(row(ID_5, ANIMAL_B, Severity.CRITICAL, T0.plusSeconds(14400), false));
        });
    }

    private NotificationEntity row(UUID id, UUID animalId, Severity severity, Instant occurredAt, boolean acknowledged) {
        NotificationEntity e = new NotificationEntity();
        e.setId(id);
        e.setEventId(UUID.randomUUID());
        e.setAnimalId(animalId);
        e.setEventType(AnimalEventType.ANIMAL_STATUS_CHANGED);
        e.setSeverity(severity);
        e.setMessage("message " + id);
        e.setOccurredAt(occurredAt);
        e.setCreatedAt(occurredAt);
        e.setPerformedBy("zoo-vet");
        e.setName("Leo");
        e.setSpecies("Lion");
        e.setPreviousStatus("HEALTHY");
        e.setNewStatus("UNDER_OBSERVATION");
        if (acknowledged) {
            e.setAcknowledgedBy("zoo-vet");
            e.setAcknowledgedAt(occurredAt.plusSeconds(60));
        }
        return e;
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldListNotificationsNewestFirstAsAdmin() {
        given().when().get("/notifications")
            .then()
                .statusCode(200)
                .body("items.id", contains(
                        ID_5.toString(), ID_3.toString(), ID_4.toString(), ID_2.toString(), ID_1.toString()))
                .body("total", equalTo(5));
    }

    @Test
    @TestSecurity(user = "dr-rossi", roles = ZooRoles.VET)
    void shouldListNotificationsAsVet() {
        given().when().get("/notifications")
            .then()
                .statusCode(200)
                .body("items", hasSize(5));
    }

    @Test
    @TestSecurity(user = "keeper.conti", roles = ZooRoles.KEEPER)
    void shouldListNotificationsAsKeeper() {
        given().when().get("/notifications")
            .then()
                .statusCode(200)
                .body("items", hasSize(5));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldExposeTheDocumentedFieldsAndNeitherEventIdNorCreatedAt() {
        given().when().get("/notifications?animalId=" + ANIMAL_A + "&open=true&size=1")
            .then()
                .statusCode(200)
                .body("items[0].keySet()", containsInAnyOrder(
                        "id", "animalId", "eventType", "severity", "message", "occurredAt",
                        "performedBy", "name", "species", "dangerous", "previousStatus", "newStatus",
                        "fromEnclosureId", "toEnclosureId", "acknowledgedBy", "acknowledgedAt"))
                .body("items[0]", not(hasKey("eventId")))
                .body("items[0]", not(hasKey("createdAt")))
                .body("items[0].id", equalTo(ID_3.toString()))
                .body("items[0].animalId", equalTo(ANIMAL_A.toString()))
                .body("items[0].eventType", equalTo("ANIMAL_STATUS_CHANGED"))
                .body("items[0].severity", equalTo("WARNING"))
                .body("items[0].message", equalTo("message " + ID_3))
                .body("items[0].occurredAt", equalTo("2026-09-20T12:00:00Z"))
                .body("items[0].performedBy", equalTo("zoo-vet"))
                .body("items[0].name", equalTo("Leo"))
                .body("items[0].species", equalTo("Lion"))
                .body("items[0].previousStatus", equalTo("HEALTHY"))
                .body("items[0].newStatus", equalTo("UNDER_OBSERVATION"))
                .body("items[0].acknowledgedBy", nullValue())
                .body("items[0].acknowledgedAt", nullValue());
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldFilterByAnimalId() {
        given().when().get("/notifications?animalId=" + ANIMAL_A)
            .then()
                .statusCode(200)
                .body("items.id", contains(ID_3.toString(), ID_2.toString(), ID_1.toString()))
                .body("total", equalTo(3));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldFilterBySeveralSeverities() {
        given().when().get("/notifications?severity=WARNING&severity=CRITICAL")
            .then()
                .statusCode(200)
                .body("items.id", contains(ID_5.toString(), ID_3.toString(), ID_2.toString()))
                .body("total", equalTo(3));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturnOnlyOpenNotificationsWhenOpenIsTrue() {
        given().when().get("/notifications?open=true")
            .then()
                .statusCode(200)
                .body("items.id", contains(ID_5.toString(), ID_3.toString(), ID_1.toString()))
                .body("total", equalTo(3));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturnPageSizeAndTotalInTheBody() {
        given().when().get("/notifications?page=1&size=2")
            .then()
                .statusCode(200)
                .body("items.id", contains(ID_4.toString(), ID_2.toString()))
                .body("page", equalTo(1))
                .body("size", equalTo(2))
                .body("total", equalTo(5));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturn400WhenSeverityIsUnknown() {
        given().when().get("/notifications?severity=warning")
            .then()
                .statusCode(400)
                .contentType("application/json")
                .body("message", equalTo("Unknown severity: warning"))
                .body("size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturn400WhenSizeIsZero() {
        given().when().get("/notifications?size=0")
            .then()
                .statusCode(400)
                .contentType("application/json")
                .body("message", equalTo("Size must be at least 1"));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturn400WhenSizeExceedsTheMaximum() {
        given().when().get("/notifications?size=101")
            .then()
                .statusCode(400)
                .contentType("application/json")
                .body("message", equalTo("Size must not exceed 100"));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturn400WhenPageIsNegative() {
        given().when().get("/notifications?page=-1")
            .then()
                .statusCode(400)
                .contentType("application/json")
                .body("message", equalTo("Page must not be negative"));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldRejectAMalformedAnimalIdWithAFrameworkStatus() {
        given().when().get("/notifications?animalId=not-a-uuid")
            .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "keeper.conti", roles = ZooRoles.KEEPER)
    void shouldAcknowledgeAsTheCurrentUser() {
        given().when().put("/notifications/" + ID_1 + "/acknowledge")
            .then()
                .statusCode(200)
                .body("id", equalTo(ID_1.toString()))
                .body("acknowledgedBy", equalTo("keeper.conti"))
                .body("acknowledgedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "keeper.conti", roles = ZooRoles.KEEPER)
    void shouldPersistTheAcknowledgementAndHideItFromTheOpenList() {
        given().when().put("/notifications/" + ID_1 + "/acknowledge").then().statusCode(200);

        given().when().get("/notifications?open=true")
            .then()
                .statusCode(200)
                .body("items.id", contains(ID_5.toString(), ID_3.toString()))
                .body("total", equalTo(2));
    }

    @Test
    @TestSecurity(user = "keeper.conti", roles = ZooRoles.KEEPER)
    void shouldAcknowledgeWithNoBodyAndNoContentType() {
        given().when().put("/notifications/" + ID_3 + "/acknowledge")
            .then()
                .statusCode(200)
                .body("acknowledgedBy", equalTo("keeper.conti"));
    }

    @Test
    @TestSecurity(user = "dr-rossi", roles = ZooRoles.VET)
    void shouldKeepTheFirstAcknowledgementWhenAcknowledgedAgain() {
        // ID_2 is already acknowledged by "zoo-vet" in the seed: a second call must not overwrite it.
        given().when().put("/notifications/" + ID_2 + "/acknowledge")
            .then()
                .statusCode(200)
                .body("acknowledgedBy", equalTo("zoo-vet"))
                .body("acknowledgedAt", equalTo("2026-09-20T11:01:00Z"));
    }

    @Test
    @TestSecurity(user = "dr-rossi", roles = ZooRoles.VET)
    void shouldKeepTheKeepersAcknowledgementWhenAVetAcknowledgesAfterwards() {
        // @TestSecurity fixes one identity per test, so the keeper's first acknowledgement goes through the use case.
        Instant first = acknowledgeNotification.acknowledge(ID_1, "keeper.conti").getAcknowledgedAt();

        given().when().put("/notifications/" + ID_1 + "/acknowledge")
            .then()
                .statusCode(200)
                .body("acknowledgedBy", equalTo("keeper.conti"))
                .body("acknowledgedAt", equalTo(first.toString()));
    }

    @Test
    @TestSecurity(user = "admin", roles = ZooRoles.ADMIN)
    void shouldReturn404WhenAcknowledgingAnUnknownNotification() {
        UUID unknown = UUID.randomUUID();

        given().when().put("/notifications/" + unknown + "/acknowledge")
            .then()
                .statusCode(404)
                .contentType("application/json")
                .body("message", equalTo("Notification not found with id: " + unknown));
    }
}
