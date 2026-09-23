package it.zoo.animal.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import it.zoo.animal.infrastructure.security.ZooRoles;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
@TestSecurity(user = "admin", roles = {ZooRoles.ADMIN})
class AnimalEventOutboxIT {

    @Inject
    EntityManager em;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    OutboxRelay outboxRelay;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM OutboxEventEntity").executeUpdate();
            em.createQuery("DELETE FROM AnimalEntity").executeUpdate();
        });
    }

    @Test
    void shouldWriteOneOutboxRowWhenAnimalIsRegistered() {
        String animalId = postAnimal("Leo", "Lion");

        List<OutboxEventEntity> rows = QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT o FROM OutboxEventEntity o", OutboxEventEntity.class).getResultList());

        assertEquals(1, rows.size());
        OutboxEventEntity row = rows.get(0);
        assertEquals("ANIMAL_REGISTERED", row.getEventType());
        assertNull(row.getPublishedAt());
        assertEquals(UUID.fromString(animalId), row.getAggregateId());
    }

    @Test
    void shouldNotWriteOutboxRowWhenStatusTransitionIsRejected() {
        String id = postAnimal("Leo", "Lion");

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"DECEASED\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(200);

        long countBefore = countOutboxRows();

        given()
            .contentType(ContentType.JSON)
            .body("{\"status\": \"HEALTHY\"}")
        .when()
            .put("/animals/" + id + "/status")
        .then()
            .statusCode(422);

        long countAfter = countOutboxRows();

        assertEquals(countBefore, countAfter);
    }

    @Test
    void shouldRelayPendingEventsToKafka() throws Exception {
        String animalId = postAnimal("Leo", "Lion");

        int published = outboxRelay.publishPending();
        assertEquals(1, published);

        ConsumerTask<String, String> records = companion.consumeStrings()
                .withGroupId("test-relay-" + UUID.randomUUID())
                .fromTopics("zoo.animal.events", 1);
        records.awaitCompletion(Duration.ofSeconds(20));

        ConsumerRecord<String, String> record = records.getFirstRecord();
        assertEquals(animalId, record.key());

        JsonNode envelope = new ObjectMapper().readTree(record.value());
        assertEquals("ANIMAL_REGISTERED", envelope.get("eventType").asText());
        assertEquals(animalId, envelope.get("animalId").asText());

        OutboxEventEntity row = QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT o FROM OutboxEventEntity o WHERE o.aggregateId = :id", OutboxEventEntity.class)
                        .setParameter("id", UUID.fromString(animalId))
                        .getSingleResult());
        assertNotNull(row.getPublishedAt());
    }

    @Test
    void shouldReturnZeroOnSecondRelayCall() {
        postAnimal("Leo", "Lion");

        int firstRun = outboxRelay.publishPending();
        assertEquals(1, firstRun);

        int secondRun = outboxRelay.publishPending();
        assertEquals(0, secondRun);
    }

    private long countOutboxRows() {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT COUNT(o) FROM OutboxEventEntity o", Long.class).getSingleResult());
    }

    private String postAnimal(String name, String species) {
        return given()
            .contentType(ContentType.JSON)
            .body("{" +
                "  \"name\": \"" + name + "\"," +
                "  \"species\": \"" + species + "\"," +
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
            .extract().path("id");
    }
}
