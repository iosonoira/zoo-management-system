package it.zoo.health.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "dr-rossi", roles = {ZooRoles.VET})
class MedicalRecordResourceIT {

    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();
        });
    }

    private String recordJson(String reason) {
        return "{"
                + "  \"animalId\": \"" + ANIMAL_ID + "\","
                + "  \"reason\": \"" + reason + "\","
                + "  \"diagnosis\": \"Sprained paw\","
                + "  \"examinedOn\": \"2026-09-01\","
                + "  \"veterinarian\": \"Dr Rossi\""
                + "}";
    }

    private String postRecord(String reason) {
        return given()
                .contentType(ContentType.JSON)
                .body(recordJson(reason))
            .when()
                .post("/medical-records")
            .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    void shouldCreateMedicalRecord() {
        given()
            .contentType(ContentType.JSON)
            .body(recordJson("Limping"))
        .when()
            .post("/medical-records")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("animalId", equalTo(ANIMAL_ID.toString()))
            .body("reason", equalTo("Limping"))
            .body("diagnosis", equalTo("Sprained paw"))
            .body("veterinarian", equalTo("Dr Rossi"));
    }

    @Test
    void shouldRejectMedicalRecordWithBlankReason() {
        given()
            .contentType(ContentType.JSON)
            .body(recordJson(""))
        .when()
            .post("/medical-records")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturnRecordWithItsTreatments() {
        String recordId = postRecord("Limping");
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\": \"Antibiotics\"}")
        .when()
            .post("/medical-records/" + recordId + "/treatments")
        .then()
            .statusCode(201)
            .body("status", equalTo("PRESCRIBED"));

        given()
        .when()
            .get("/medical-records/" + recordId)
        .then()
            .statusCode(200)
            .body("id", equalTo(recordId))
            .body("treatments.size()", equalTo(1))
            .body("treatments[0].description", equalTo("Antibiotics"));
    }

    @Test
    void shouldReturn404WhenRecordDoesNotExist() {
        given()
        .when()
            .get("/medical-records/" + UUID.randomUUID())
        .then()
            .statusCode(404)
            .body("message", containsString("Medical record not found"));
    }

    @Test
    void shouldReturn404WhenIdIsNotAUuid() {
        // 404, not 400: an unparseable @PathParam UUID never reaches the resource,
        // so JAX-RS treats it as an unmatched path. AnimalResourceIT asserts the same.
        given()
        .when()
            .get("/medical-records/not-a-uuid")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldListRecordsWithPageEnvelope() {
        postRecord("Limping");
        postRecord("Checkup");

        given()
        .when()
            .get("/medical-records")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(2))
            .body("page", equalTo(0))
            .body("size", equalTo(20))
            .body("total", equalTo(2));
    }

    @Test
    void shouldFilterListByAnimalId() {
        postRecord("Limping");

        given()
            .queryParam("animalId", UUID.randomUUID().toString())
        .when()
            .get("/medical-records")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(0))
            .body("total", equalTo(0));
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        given()
            .queryParam("size", 101)
        .when()
            .get("/medical-records")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn404WhenPrescribingOnMissingRecord() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"description\": \"Antibiotics\"}")
        .when()
            .post("/medical-records/" + UUID.randomUUID() + "/treatments")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldActivateThenCompleteTreatment() {
        String recordId = postRecord("Limping");
        String treatmentId = given()
                .contentType(ContentType.JSON)
                .body("{\"description\": \"Antibiotics\"}")
            .when()
                .post("/medical-records/" + recordId + "/treatments")
            .then()
                .statusCode(201)
                .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ACTIVE\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("ACTIVE"))
            .body("startedOn", notNullValue());

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"COMPLETED\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("COMPLETED"))
            .body("endedOn", notNullValue());
    }

    @Test
    void shouldReturn422OnIllegalTransition() {
        String recordId = postRecord("Limping");
        String treatmentId = given()
                .contentType(ContentType.JSON)
                .body("{\"description\": \"Antibiotics\"}")
            .when()
                .post("/medical-records/" + recordId + "/treatments")
            .then()
                .statusCode(201)
                .extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"COMPLETED\"}")
        .when()
            .put("/treatments/" + treatmentId + "/status")
        .then()
            .statusCode(422)
            .body("message", containsString("Cannot transition"));
    }

    @Test
    void shouldReturn404WhenTreatmentDoesNotExist() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ACTIVE\"}")
        .when()
            .put("/treatments/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(404);
    }
}
