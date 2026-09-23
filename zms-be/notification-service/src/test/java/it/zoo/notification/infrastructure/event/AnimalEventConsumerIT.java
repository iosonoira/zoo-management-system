package it.zoo.notification.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.infrastructure.persistence.NotificationEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class AnimalEventConsumerIT {

    private static final String TOPIC = "zoo.animal.events";
    private static final String DLQ_TOPIC = "zoo.animal.events.dlq";

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() ->
                em.createQuery("DELETE FROM NotificationEntity").executeUpdate());
    }

    @Test
    void shouldPersistANotificationPerFixtureWithTheRuleDrivenSeverity() throws Exception {
        UUID registeredEventId = UUID.randomUUID();
        UUID registeredAnimalId = UUID.randomUUID();
        UUID statusChangedEventId = UUID.randomUUID();
        UUID statusChangedAnimalId = UUID.randomUUID();
        UUID transferredEventId = UUID.randomUUID();
        UUID transferredAnimalId = UUID.randomUUID();

        produce(rewriteFixture("animal-registered.json", registeredEventId, registeredAnimalId));
        produce(rewriteFixture("animal-status-changed.json", statusChangedEventId, statusChangedAnimalId));
        produce(rewriteFixture("animal-transferred.json", transferredEventId, transferredAnimalId));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(3, countNotifications()));

        // ANIMAL_REGISTERED is always INFO, regardless of payload.
        assertEquals(Severity.INFO, severityOf(registeredEventId));
        // previousStatus HEALTHY -> newStatus UNDER_OBSERVATION: NotificationRule maps
        // UNDER_OBSERVATION/IN_TREATMENT to WARNING.
        assertEquals(Severity.WARNING, severityOf(statusChangedEventId));
        // fixture's dangerous=true: NotificationRule maps a dangerous transfer to WARNING.
        assertEquals(Severity.WARNING, severityOf(transferredEventId));
    }

    @Test
    void shouldPersistExactlyOneRowWhenTheSameEventIsDeliveredTwice() {
        UUID eventId = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        String payload = rewriteFixture("animal-registered.json", eventId, animalId);

        produce(payload);
        produce(payload);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(1, countNotifications(eventId)));

        // Give a possible duplicate insert a chance to show up before asserting it never does.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1, countNotifications(eventId)));
    }

    @Test
    void shouldRouteMalformedRecordsToTheDlqAndKeepConsumingAfterwards() {
        ConsumerTask<String, String> dlq = companion.consumeStrings()
                .withGroupId("test-dlq-" + UUID.randomUUID())
                .fromTopics(DLQ_TOPIC, 1);

        companion.produceStrings().fromRecords(
                new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), "not json"));

        dlq.awaitCompletion(Duration.ofSeconds(20));
        assertEquals(1, dlq.count());

        UUID eventId = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        produce(rewriteFixture("animal-registered.json", eventId, animalId));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(1, countNotifications(eventId)));
    }

    private void produce(String payload) {
        ObjectNode envelope;
        try {
            envelope = (ObjectNode) objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String key = envelope.get("animalId").asText();
        companion.produceStrings().fromRecords(new ProducerRecord<>(TOPIC, key, payload));
    }

    private String rewriteFixture(String fixtureName, UUID eventId, UUID animalId) {
        try (InputStream in = getClass().getResourceAsStream("/contract/" + fixtureName)) {
            ObjectNode envelope = (ObjectNode) objectMapper.readTree(in);
            envelope.put("eventId", eventId.toString());
            envelope.put("animalId", animalId.toString());
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite fixture " + fixtureName, e);
        }
    }

    private long countNotifications() {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT COUNT(n) FROM NotificationEntity n", Long.class).getSingleResult());
    }

    private long countNotifications(UUID eventId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT COUNT(n) FROM NotificationEntity n WHERE n.eventId = :eventId", Long.class)
                        .setParameter("eventId", eventId)
                        .getSingleResult());
    }

    private Severity severityOf(UUID eventId) {
        List<NotificationEntity> rows = QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT n FROM NotificationEntity n WHERE n.eventId = :eventId", NotificationEntity.class)
                        .setParameter("eventId", eventId)
                        .getResultList());
        assertTrue(rows.size() == 1, "expected exactly one notification for event " + eventId);
        return rows.get(0).getSeverity();
    }
}
