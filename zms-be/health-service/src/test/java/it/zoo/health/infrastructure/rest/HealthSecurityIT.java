package it.zoo.health.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.infrastructure.persistence.MedicalRecordEntity;
import it.zoo.health.infrastructure.persistence.TreatmentEntity;
import it.zoo.health.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthSecurityIT {

    private static final UUID RECORD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TREATMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ANIMAL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private static final String RECORD_JSON = "{"
            + "  \"animalId\": \"550e8400-e29b-41d4-a716-446655440000\","
            + "  \"reason\": \"Limping\","
            + "  \"diagnosis\": \"Sprained paw\","
            + "  \"examinedOn\": \"2026-09-01\","
            + "  \"veterinarian\": \"Dr Rossi\""
            + "}";

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();

            MedicalRecordEntity record = new MedicalRecordEntity();
            record.setId(RECORD_ID);
            record.setAnimalId(ANIMAL_ID);
            record.setReason("Limping");
            record.setDiagnosis("Sprained paw");
            record.setExaminedOn(LocalDate.of(2026, 9, 1));
            record.setVeterinarian("Dr Rossi");
            record.setCreatedBy("system");
            em.persist(record);

            TreatmentEntity treatment = new TreatmentEntity();
            treatment.setId(TREATMENT_ID);
            treatment.setMedicalRecordId(RECORD_ID);
            treatment.setDescription("Antibiotics");
            treatment.setStatus(TreatmentStatus.PRESCRIBED);
            treatment.setCreatedBy("system");
            em.persist(treatment);
        });
    }

    @Test
    void shouldRejectAnonymousRead() {
        given().when().get("/medical-records").then().statusCode(401);
    }

    @Test
    void shouldRejectAnonymousWrite() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToList() {
        given().when().get("/medical-records").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldAllowKeeperToReadOneRecord() {
        given().when().get("/medical-records/" + RECORD_ID).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromCreatingRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromPrescribing() {
        given().contentType(ContentType.JSON).body("{\"description\": \"Antibiotics\"}")
            .when().post("/medical-records/" + RECORD_ID + "/treatments")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldForbidKeeperFromChangingTreatmentStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"ACTIVE\"}")
            .when().put("/treatments/" + TREATMENT_ID + "/status")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToCreateRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldAllowVetToChangeTreatmentStatus() {
        given().contentType(ContentType.JSON).body("{\"status\": \"ACTIVE\"}")
            .when().put("/treatments/" + TREATMENT_ID + "/status")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldAllowAdminToCreateRecord() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records").then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldRecordTheActingPrincipalAsCreator() {
        given().contentType(ContentType.JSON).body(RECORD_JSON)
            .when().post("/medical-records")
            .then().statusCode(201).body("createdBy", equalTo("admin"));
    }
}
