package it.zoo.animal.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.infrastructure.persistence.AnimalEntity;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AnimalSecurityIT {

    private static final UUID SEEDED_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENCLOSURE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String ANIMAL_JSON = "{"
            + "  \"name\": \"Leo\","
            + "  \"species\": \"Lion\","
            + "  \"dangerous\": false,"
            + "  \"habitat\": \"TERRESTRIAL\","
            + "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\","
            + "  \"arrivalDate\": \"2024-01-15\""
            + "}";

    @Inject
    EntityManager em;

    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM AnimalEntity").executeUpdate();
            AnimalEntity entity = new AnimalEntity();
            entity.setId(SEEDED_ID);
            entity.setName("Leo");
            entity.setSpecies("Lion");
            entity.setDangerous(false);
            entity.setHabitat(Habitat.TERRESTRIAL);
            entity.setEnclosureId(ENCLOSURE_ID);
            entity.setArrivalDate(LocalDate.of(2024, 1, 15));
            entity.setStatus(AnimalStatus.HEALTHY);
            em.persist(entity);
        });
    }

    @Test
    void shouldReturn401WhenListingAnimalsAnonymously() {
        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    void shouldReturn401WhenRegisteringAnimalAnonymously() {
        given()
            .contentType(ContentType.JSON)
            .body(ANIMAL_JSON)
        .when()
            .post("/animals")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldReturn403WhenVetRegistersAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body(ANIMAL_JSON)
        .when()
            .post("/animals")
        .then()
            .statusCode(403)
            .body("message", equalTo("Insufficient role"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn403WhenKeeperUpdatesStatus() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"UNDER_OBSERVATION\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/status")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldReturn403WhenVetTransfersAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"660e8400-e29b-41d4-a716-446655440001\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/transfer")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldUpdateStatusWhenCallerIsVet() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"UNDER_OBSERVATION\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("UNDER_OBSERVATION"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldTransferAnimalWhenCallerIsKeeper() {
        String target = "660e8400-e29b-41d4-a716-446655440001";

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"" + target + "\"}")
        .when()
            .put("/animals/" + SEEDED_ID + "/transfer")
        .then()
            .statusCode(200)
            .body("enclosureId", equalTo(target));
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldListAnimalsWhenCallerIsKeeper() {
        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1));
    }
}
