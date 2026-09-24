package it.zoo.animal.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
class AnimalResourceIT {

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM OutboxEventEntity").executeUpdate();
            em.createQuery("DELETE FROM AnimalEntity").executeUpdate();
        });
    }

    @Test
    void shouldRegisterAnimal() {
        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"Leo\"," +
                "  \"species\": \"Lion\"," +
                "  \"dangerous\": true," +
                "  \"habitat\": \"TERRESTRIAL\"," +
                "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\"," +
                "  \"arrivalDate\": \"2024-01-15\"" +
                "}")
        .when()
            .post("/animals")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("name", equalTo("Leo"))
            .body("species", equalTo("Lion"))
            .body("dangerous", equalTo(true))
            .body("status", equalTo("HEALTHY"));
    }

    @Test
    void shouldListAllAnimals() {
        postAnimal("Leo", "Lion");
        postAnimal("Nemo", "Fish");

        given()
        .when()
            .get("/animals")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(2))
            .body("page", equalTo(0))
            .body("size", equalTo(20))
            .body("total", equalTo(2));
    }

    @Test
    void shouldReturnRequestedPage() {
        postAnimal("Ape", "Primate");
        postAnimal("Bear", "Ursid");
        postAnimal("Cobra", "Snake");

        given()
            .queryParam("page", 1)
            .queryParam("size", 2)
        .when()
            .get("/animals")
        .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].name", equalTo("Cobra"))
            .body("page", equalTo(1))
            .body("total", equalTo(3));
    }

    @Test
    void shouldReturn400WhenSizeExceedsMaximum() {
        given()
            .queryParam("size", 101)
        .when()
            .get("/animals")
        .then()
            .statusCode(400)
            .body("message", notNullValue());
    }

    @Test
    void shouldReturn400WhenPageIsNegative() {
        given()
            .queryParam("page", -1)
        .when()
            .get("/animals")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldGetAnimalById() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
        .when()
            .get("/animals/" + id)
        .then()
            .statusCode(200)
            .body("id", equalTo(id))
            .body("name", equalTo("Leo"));
    }

    @Test
    void shouldReturn404WhenAnimalNotFound() {
        given()
        .when()
            .get("/animals/00000000-0000-0000-0000-000000000000")
        .then()
            .statusCode(404)
            .body("message", notNullValue());
    }

    @Test
    void shouldUpdateAnimalStatus() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"UNDER_OBSERVATION\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("UNDER_OBSERVATION"));
    }

    @Test
    void shouldReturn422OnInvalidStatusTransition() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"DECEASED\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"HEALTHY\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(422)
            .body("message", notNullValue());
    }

    @Test
    void shouldTransferAnimal() {
        String id = postAnimal("Leo", "Lion").extract().path("id");
        String newEnclosureId = "660e8400-e29b-41d4-a716-446655440001";

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"" + newEnclosureId + "\"}")
        .when()
            .put("/animals/" + id + "/transfer")
        .then()
            .statusCode(200)
            .body("enclosureId", equalTo(newEnclosureId));
    }

    @Test
    void shouldReturn400OnBlankName() {
        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"\"," +
                "  \"species\": \"Lion\"," +
                "  \"dangerous\": false," +
                "  \"habitat\": \"TERRESTRIAL\"," +
                "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\"," +
                "  \"arrivalDate\": \"2024-01-15\"" +
                "}")
        .when()
            .post("/animals")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn404WhenIdIsNotAUuid() {
        given()
        .when()
            .get("/animals/not-a-uuid")
        .then()
            .statusCode(404);
    }

    @Test
    void shouldReturn400WhenStatusIsNotAKnownEnumValue() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"ON_HOLIDAY\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenBodyIsNotValidJson() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": ")
        .when()
            .post("/animals")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenNameExceedsColumnLength() {
        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"" + "L".repeat(101) + "\"," +
                "  \"species\": \"Lion\"," +
                "  \"dangerous\": false," +
                "  \"habitat\": \"TERRESTRIAL\"," +
                "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\"," +
                "  \"arrivalDate\": \"2024-01-15\"" +
                "}")
        .when()
            .post("/animals")
        .then()
            .statusCode(400);
    }

    @Test
    void shouldReturn400WhenTransferringDeceasedAnimal() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"DECEASED\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"660e8400-e29b-41d4-a716-446655440001\"}")
        .when()
            .put("/animals/" + id + "/transfer")
        .then()
            .statusCode(400)
            .body("message", notNullValue());
    }

    @Test
    void shouldRecordActingUserWhenRegisteringAnimal() {
        String id = postAnimal("Leo", "Lion")
            .body("createdBy", equalTo("admin"))
            .body("updatedBy", equalTo("admin"))
            .extract().path("id");

        given()
        .when()
            .get("/animals/" + id)
        .then()
            .statusCode(200)
            .body("createdBy", equalTo("admin"));
    }

    @Test
    void shouldReturn400WhenRegisteringWithUnknownEnclosure() {
        given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"Leo\"," +
                "  \"species\": \"Lion\"," +
                "  \"dangerous\": false," +
                "  \"habitat\": \"TERRESTRIAL\"," +
                "  \"enclosureId\": \"00000000-0000-0000-0000-000000000099\"," +
                "  \"arrivalDate\": \"2024-01-15\"" +
                "}")
        .when()
            .post("/animals")
        .then()
            .statusCode(400)
            .body("message", notNullValue());
    }

    @Test
    void shouldReturn400WhenTransferringToUnknownEnclosure() {
        String id = postAnimal("Leo", "Lion").extract().path("id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"targetEnclosureId\": \"00000000-0000-0000-0000-000000000099\"}")
        .when()
            .put("/animals/" + id + "/transfer")
        .then()
            .statusCode(400)
            .body("message", notNullValue());
    }

    private ValidatableResponse postAnimal(String name, String species) {
        return given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"" + name + "\"," +
                "  \"species\": \"" + species + "\"," +
                "  \"dangerous\": false," +
                "  \"habitat\": \"TERRESTRIAL\"," +
                "  \"enclosureId\": \"550e8400-e29b-41d4-a716-446655440000\"," +
                "  \"arrivalDate\": \"2024-01-15\"" +
                "}")
        .when()
            .post("/animals")
        .then()
            .statusCode(201);
    }
}
