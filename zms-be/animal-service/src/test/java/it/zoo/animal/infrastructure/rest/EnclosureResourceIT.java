package it.zoo.animal.infrastructure.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.infrastructure.persistence.EnclosureEntity;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EnclosureResourceIT {

    private static final UUID FIRST_ID = UUID.fromString("aaaaaaaa-1111-4a1a-8a1a-aaaaaaaaaaaa");
    private static final UUID SECOND_ID = UUID.fromString("bbbbbbbb-2222-4b2b-8b2b-bbbbbbbbbbbb");

    @Inject
    EntityManager em;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM EnclosureEntity e WHERE e.id IN :ids")
                    .setParameter("ids", java.util.List.of(FIRST_ID, SECOND_ID))
                    .executeUpdate();
        });
    }

    private void seedTwoEnclosures() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnclosureEntity zebra = new EnclosureEntity();
            zebra.setId(SECOND_ID);
            zebra.setName("Zebra Field");
            zebra.setHabitat(Habitat.TERRESTRIAL);
            em.persist(zebra);

            EnclosureEntity aviary = new EnclosureEntity();
            aviary.setId(FIRST_ID);
            aviary.setName("Aviary Dome");
            aviary.setHabitat(Habitat.AMPHIBIOUS);
            em.persist(aviary);
        });
    }

    @Test
    void shouldReturn401WhenListingEnclosuresAnonymously() {
        given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(401)
            .body("message", equalTo("Authentication required"));
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldReturn200ForAdmin() {
        given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "vet", roles = {ZooRoles.VET})
    void shouldReturn200ForVet() {
        given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "keeper", roles = {ZooRoles.KEEPER})
    void shouldReturn200ForKeeper() {
        given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
    void shouldListEnclosuresSortedByName() {
        seedTwoEnclosures();

        // %test also carries the fixed reference enclosures seeded from db/test (used by
        // AnimalResourceIT/AnimalSecurityIT), so assert the full response is sorted by name
        // and that the two rows this test inserted appear in the right relative order,
        // rather than asserting on an exact row count/content.
        List<String> names = given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(200)
            .extract().jsonPath().getList("name", String.class);

        List<String> sorted = names.stream().sorted().toList();
        assertEquals(sorted, names, "enclosures must be returned sorted by name");

        int aviaryIndex = names.indexOf("Aviary Dome");
        int zebraIndex = names.indexOf("Zebra Field");
        assertTrue(aviaryIndex >= 0 && zebraIndex >= 0, "both inserted enclosures must be present");
        assertTrue(aviaryIndex < zebraIndex, "Aviary Dome must be listed before Zebra Field");
    }
}
