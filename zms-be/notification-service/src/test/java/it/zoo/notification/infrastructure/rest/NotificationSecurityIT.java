package it.zoo.notification.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.infrastructure.persistence.NotificationEntity;
import it.zoo.notification.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class NotificationSecurityIT {

    private static final UUID NOTIFICATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM NotificationEntity").executeUpdate();

            NotificationEntity notification = new NotificationEntity();
            notification.setId(NOTIFICATION_ID);
            notification.setEventId(UUID.randomUUID());
            notification.setAnimalId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
            notification.setEventType(AnimalEventType.ANIMAL_STATUS_CHANGED);
            notification.setSeverity(Severity.CRITICAL);
            notification.setMessage("Leo (Lion) status changed from HEALTHY to DECEASED");
            notification.setOccurredAt(Instant.parse("2026-09-30T08:00:00Z"));
            notification.setCreatedAt(Instant.parse("2026-09-30T08:00:01Z"));
            em.persist(notification);
        });
    }

    @Test
    void shouldRejectAnonymousList() {
        given().when().get("/notifications").then().statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    void shouldRejectAnonymousAcknowledgementAndLeaveTheNotificationOpen() {
        given().when().put("/notifications/" + NOTIFICATION_ID + "/acknowledge").then().statusCode(401)
            .body("message", equalTo("Authentication required"));
        assertStillOpen();
    }

    @Test
    @TestSecurity(user = "visitor", roles = {"visitor"})
    void shouldForbidAUserWithoutAZooRoleFromListing() {
        given().when().get("/notifications").then().statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "visitor", roles = {"visitor"})
    void shouldForbidAUserWithoutAZooRoleFromAcknowledging() {
        given().when().put("/notifications/" + NOTIFICATION_ID + "/acknowledge").then().statusCode(403)
            .body("message", equalTo("Insufficient role"));
        assertStillOpen();
    }

    @Test
    @TestSecurity(user = "keeper.conti", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToListAndAcknowledge() {
        given().when().get("/notifications").then().statusCode(200);
        given().when().put("/notifications/" + NOTIFICATION_ID + "/acknowledge").then().statusCode(200)
            .body("acknowledgedBy", equalTo("keeper.conti"));
    }

    @Test
    @TestSecurity(user = "vet.bianchi", roles = {ZooRoles.VET})
    void shouldAllowVetToListAndAcknowledge() {
        given().when().get("/notifications").then().statusCode(200);
        given().when().put("/notifications/" + NOTIFICATION_ID + "/acknowledge").then().statusCode(200)
            .body("acknowledgedBy", equalTo("vet.bianchi"));
    }

    @Test
    @TestSecurity(user = "admin.rossi", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToListAndAcknowledge() {
        given().when().get("/notifications").then().statusCode(200);
        given().when().put("/notifications/" + NOTIFICATION_ID + "/acknowledge").then().statusCode(200)
            .body("acknowledgedBy", equalTo("admin.rossi"));
    }

    private void assertStillOpen() {
        NotificationEntity row = QuarkusTransaction.requiringNew()
                .call(() -> em.find(NotificationEntity.class, NOTIFICATION_ID));
        assertThat(row.getAcknowledgedBy(), nullValue());
        assertThat(row.getAcknowledgedAt(), nullValue());
    }
}
