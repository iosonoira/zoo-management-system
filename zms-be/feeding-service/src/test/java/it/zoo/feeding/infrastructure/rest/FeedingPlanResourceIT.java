package it.zoo.feeding.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.infrastructure.persistence.DeceasedAnimalEntity;
import it.zoo.feeding.infrastructure.persistence.FeedingPlanEntity;
import it.zoo.feeding.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "dr-rossi", roles = {ZooRoles.VET})
class FeedingPlanResourceIT {

    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM FeedingEntity").executeUpdate();
            em.createQuery("DELETE FROM FeedingPlanEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    private String planJson(UUID animalId, String food, Object quantityGrams, String timesArray, String notes) {
        return "{"
                + "  \"animalId\": \"" + animalId + "\","
                + "  \"food\": \"" + food + "\","
                + "  \"quantityGrams\": " + quantityGrams + ","
                + "  \"feedingTimes\": " + timesArray
                + (notes != null ? ", \"notes\": \"" + notes + "\"" : "")
                + "}";
    }

    private String postPlan(UUID animalId, String timesArray) {
        return given()
                .contentType(ContentType.JSON)
                .body(planJson(animalId, "Hay", 500, timesArray, null))
            .when()
                .post("/feeding-plans")
            .then()
                .statusCode(201)
                .extract().path("id");
    }

    private UUID seedPlan(PlanStatus status) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            FeedingPlanEntity entity = new FeedingPlanEntity();
            entity.setId(id);
            entity.setAnimalId(ANIMAL_ID);
            entity.setFood("Hay");
            entity.setQuantityGrams(500);
            entity.setFeedingTimes(List.of(LocalTime.of(8, 0)));
            entity.setStatus(status);
            entity.setStartedOn(LocalDate.now());
            entity.setCreatedBy("system");
            entity.setUpdatedBy("system");
            em.persist(entity);
        });
        return id;
    }

    @Test
    void shouldCreateFeedingPlanWithSortedFeedingTimes() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 500, "[\"18:00\",\"07:30\"]", "Loves hay"))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("animalId", equalTo(ANIMAL_ID.toString()))
            .body("food", equalTo("Hay"))
            .body("quantityGrams", equalTo(500))
            .body("feedingTimes", contains("07:30", "18:00"))
            .body("status", equalTo("ACTIVE"))
            .body("startedOn", notNullValue());
    }

    @Test
    void shouldRejectBlankFood() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "", 500, "[\"07:30\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectZeroQuantity() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 0, "[\"07:30\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectEmptyFeedingTimes() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 500, "[]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectMoreThanSixFeedingTimes() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 500,
                    "[\"01:00\",\"02:00\",\"03:00\",\"04:00\",\"05:00\",\"06:00\",\"07:00\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldRejectDuplicateFeedingTimes() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 500, "[\"07:30\",\"07:30\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400)
            .body("message", containsString("repeat"));
    }

    @Test
    void shouldRejectFeedingTimeWithSeconds() {
        given()
            .contentType(ContentType.JSON)
            .body(planJson(ANIMAL_ID, "Hay", 500, "[\"07:30:45\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(400)
            .body("message", containsString("whole minutes"));
    }

    @Test
    void shouldGetPlanById() {
        String id = postPlan(ANIMAL_ID, "[\"07:30\"]");

        given()
        .when()
            .get("/feeding-plans/" + id)
        .then()
            .statusCode(200)
            .body("id", equalTo(id));
    }

    @Test
    void shouldReturn404WhenPlanDoesNotExist() {
        given()
        .when()
            .get("/feeding-plans/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("message", containsString("not found"));
    }

    @Test
    void shouldReturn404WhenPlanIdIsNotAUuid() {
        given()
        .when()
            .get("/feeding-plans/not-a-uuid")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldListPlansFilteredByAnimalIdWithEnvelope() {
        postPlan(ANIMAL_ID, "[\"07:30\"]");
        postPlan(UUID.randomUUID(), "[\"08:00\"]");

        given()
            .queryParam("animalId", ANIMAL_ID.toString())
        .when()
            .get("/feeding-plans")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("page", equalTo(0))
            .body("size", equalTo(20))
            .body("total", equalTo(1));
    }

    @Test
    void shouldChangeStatusAndReturn200() {
        String id = postPlan(ANIMAL_ID, "[\"07:30\"]");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"SUSPENDED\"}")
        .when()
            .put("/feeding-plans/" + id + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("SUSPENDED"));
    }

    @Test
    void shouldSetEndedOnWhenStatusBecomesEnded() {
        String id = postPlan(ANIMAL_ID, "[\"07:30\"]");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ENDED\"}")
        .when()
            .put("/feeding-plans/" + id + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("ENDED"))
            .body("endedOn", notNullValue());
    }

    @Test
    void shouldReturn422OnInvalidTransition() {
        String id = postPlan(ANIMAL_ID, "[\"07:30\"]");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ENDED\"}")
        .when()
            .put("/feeding-plans/" + id + "/status")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ACTIVE\"}")
        .when()
            .put("/feeding-plans/" + id + "/status")
        .then()
            .statusCode(422)
            .body("message", containsString("Cannot transition"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldRecordFeedingWithDefaultFedAt() {
        UUID planId = seedPlan(PlanStatus.ACTIVE);

        given()
            .contentType(ContentType.JSON)
            .body("{\"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(201)
            .body("planId", equalTo(planId.toString()))
            .body("quantityGrams", equalTo(100))
            .body("fedAt", notNullValue())
            .body("recordedBy", equalTo("keeper"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn422WhenRecordingOnSuspendedPlan() {
        UUID planId = seedPlan(PlanStatus.SUSPENDED);

        given()
            .contentType(ContentType.JSON)
            .body("{\"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn400WhenFedAtIsInTheFuture() {
        UUID planId = seedPlan(PlanStatus.ACTIVE);
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        given()
            .contentType(ContentType.JSON)
            .body("{\"fedAt\": \"" + future + "\", \"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn404WhenRecordingOnUnknownPlan() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + UUID.randomUUID() + "/feedings")
        .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldListFeedingsOrderedByFedAtDesc() {
        UUID planId = seedPlan(PlanStatus.ACTIVE);

        given()
            .contentType(ContentType.JSON)
            .body("{\"fedAt\": \"2026-09-01T07:00:00Z\", \"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body("{\"fedAt\": \"2026-09-01T18:00:00Z\", \"quantityGrams\": 100}")
        .when()
            .post("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(201);

        given()
        .when()
            .get("/feeding-plans/" + planId + "/feedings")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(2))
            .body("items[0].fedAt", equalTo("2026-09-01T18:00:00Z"))
            .body("items[1].fedAt", equalTo("2026-09-01T07:00:00Z"))
            .body("total", equalTo(2));
    }

    @Test
    void shouldReturn422WhenCreatingPlanForDeceasedAnimal() {
        UUID deceasedAnimalId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            DeceasedAnimalEntity entity = new DeceasedAnimalEntity();
            entity.setAnimalId(deceasedAnimalId);
            entity.setEventId(UUID.randomUUID());
            entity.setOccurredAt(Instant.now());
            em.persist(entity);
        });

        given()
            .contentType(ContentType.JSON)
            .body(planJson(deceasedAnimalId, "Hay", 500, "[\"07:30\"]", null))
        .when()
            .post("/feeding-plans")
        .then()
            .statusCode(422)
            .body("message", containsString("deceased"));
    }
}
