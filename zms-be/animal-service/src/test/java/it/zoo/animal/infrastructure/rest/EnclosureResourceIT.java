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

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

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

        given()
        .when()
            .get("/enclosures")
        .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("name", org.hamcrest.Matchers.contains("Aviary Dome", "Zebra Field"))
            .body("[0].id", equalTo(FIRST_ID.toString()))
            .body("[0].habitat", equalTo("AMPHIBIOUS"))
            .body("[1].id", equalTo(SECOND_ID.toString()))
            .body("[1].habitat", equalTo("TERRESTRIAL"));
    }
}
