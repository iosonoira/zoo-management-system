package it.zoo.feeding.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.infrastructure.persistence.FeedingPlanEntity;
import it.zoo.feeding.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class FeedingSecurityIT {

    private static final UUID PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private static final String PLAN_JSON = "{"
            + "  \"animalId\": \"550e8400-e29b-41d4-a716-446655440000\","
            + "  \"food\": \"Hay\","
            + "  \"quantityGrams\": 500,"
            + "  \"feedingTimes\": [\"07:30\"]"
            + "}";

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM FeedingEntity").executeUpdate();
            em.createQuery("DELETE FROM FeedingPlanEntity").executeUpdate();

            FeedingPlanEntity entity = new FeedingPlanEntity();
            entity.setId(PLAN_ID);
            entity.setAnimalId(ANIMAL_ID);
            entity.setFood("Hay");
            entity.setQuantityGrams(500);
            entity.setFeedingTimes(List.of(LocalTime.of(8, 0)));
            entity.setStatus(PlanStatus.ACTIVE);
            entity.setStartedOn(LocalDate.now());
            entity.setCreatedBy("system");
            entity.setUpdatedBy("system");
            em.persist(entity);
        });
    }

    @Test
    void shouldRejectAnonymousRead() {
        given().when().get("/feeding-plans").then().statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    void shouldRejectAnonymousWrite() {
        given().contentType(ContentType.JSON).body(PLAN_JSON)
            .when().post("/feeding-plans")
            .then().statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToList() {
        given().when().get("/feeding-plans").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToReadOnePlan() {
        given().when().get("/feeding-plans/" + PLAN_ID).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromCreatingPlan() {
        given().contentType(ContentType.JSON).body(PLAN_JSON)
            .when().post("/feeding-plans")
            .then().statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromChangingStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"SUSPENDED\"}")
            .when().put("/feeding-plans/" + PLAN_ID + "/status")
            .then().statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToRecordFeeding() {
        given().contentType(ContentType.JSON).body("{\"quantityGrams\": 100}")
            .when().post("/feeding-plans/" + PLAN_ID + "/feedings")
            .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToCreatePlan() {
        given().contentType(ContentType.JSON).body(PLAN_JSON)
            .when().post("/feeding-plans").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToChangeStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"SUSPENDED\"}")
            .when().put("/feeding-plans/" + PLAN_ID + "/status")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldForbidVetFromRecordingFeeding() {
        given().contentType(ContentType.JSON).body("{\"quantityGrams\": 100}")
            .when().post("/feeding-plans/" + PLAN_ID + "/feedings")
            .then().statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToCreatePlan() {
        given().contentType(ContentType.JSON).body(PLAN_JSON)
            .when().post("/feeding-plans").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToChangeStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"SUSPENDED\"}")
            .when().put("/feeding-plans/" + PLAN_ID + "/status")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToRecordFeeding() {
        given().contentType(ContentType.JSON).body("{\"quantityGrams\": 100}")
            .when().post("/feeding-plans/" + PLAN_ID + "/feedings")
            .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldRecordTheActingPrincipalAsCreator() {
        given().contentType(ContentType.JSON).body(PLAN_JSON)
            .when().post("/feeding-plans")
            .then().statusCode(201).body("createdBy", equalTo("admin"));
    }
}
