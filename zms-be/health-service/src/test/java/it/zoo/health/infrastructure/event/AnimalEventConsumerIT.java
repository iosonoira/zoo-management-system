package it.zoo.health.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.ConsumerTask;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.infrastructure.persistence.DeceasedAnimalEntity;
import it.zoo.health.infrastructure.persistence.MedicalRecordEntity;
import it.zoo.health.infrastructure.persistence.TreatmentEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class AnimalEventConsumerIT {

    private static final String TOPIC = "zoo.animal.events";
    private static final String DLQ_TOPIC = "zoo.animal.events.health.dlq";

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    @Test
    void shouldCancelPrescribedAndActiveTreatmentsWhenAnimalDies() {
        UUID animalId = UUID.randomUUID();
        UUID otherAnimalId = UUID.randomUUID();
        UUID recordId = seedRecord(animalId);
        UUID otherRecordId = seedRecord(otherAnimalId);
        UUID prescribedId = seedTreatment(recordId, TreatmentStatus.PRESCRIBED, null, null);
        UUID activeId = seedTreatment(recordId, TreatmentStatus.ACTIVE, LocalDate.of(2026, 1, 1), null);
        UUID completedId = seedTreatment(recordId, TreatmentStatus.COMPLETED, LocalDate.of(2019, 1, 1),
                LocalDate.of(2020, 1, 1));
        UUID otherTreatmentId = seedTreatment(otherRecordId, TreatmentStatus.PRESCRIBED, null, null);

        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-23T10:15:30Z");
        produce(statusChangedJson(eventId, animalId, occurredAt, "zoo-vet", "DECEASED"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(deceasedExists(animalId)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(TreatmentStatus.CANCELLED, treatmentStatus(prescribedId)));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(TreatmentStatus.CANCELLED, treatmentStatus(activeId)));

        LocalDate expectedEndedOn = LocalDate.ofInstant(occurredAt, ZoneId.systemDefault());
        assertEquals(expectedEndedOn, treatmentEndedOn(prescribedId));
        assertEquals("zoo-vet", treatmentUpdatedBy(prescribedId));
        assertEquals(expectedEndedOn, treatmentEndedOn(activeId));
        assertEquals("zoo-vet", treatmentUpdatedBy(activeId));

        // Already-COMPLETED treatment is untouched: its original endedOn and updatedBy stay.
        assertEquals(TreatmentStatus.COMPLETED, treatmentStatus(completedId));
        assertEquals(LocalDate.of(2020, 1, 1), treatmentEndedOn(completedId));
        assertEquals("system", treatmentUpdatedBy(completedId));

        // A different animal's treatment is untouched.
        assertEquals(TreatmentStatus.PRESCRIBED, treatmentStatus(otherTreatmentId));
        assertFalse(deceasedExists(otherAnimalId));

        assertEquals(1, deceasedCount(animalId));
    }

    @Test
    void shouldPersistExactlyOneDeceasedRowWhenEventDeliveredTwiceAndNothingLandsInDlq() {
        UUID animalId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String payload = statusChangedJson(eventId, animalId, Instant.now(), "zoo-vet", "DECEASED");

        // This DLQ topic is shared across the whole test class (a real Kafka topic, not a table we
        // can clean per test), so an earlier test's malformed-JSON record may already be sitting in
        // it. Capture a baseline count once this fresh consumer has caught up, then assert it does
        // not grow, rather than assuming the topic starts empty.
        ConsumerTask<String, String> dlq = companion.consumeStrings()
                .withGroupId("test-dlq-" + UUID.randomUUID())
                .fromTopics(DLQ_TOPIC);
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).until(() -> true);
        long baseline = dlq.count();

        produce(payload);
        produce(payload);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(1, deceasedCount(animalId)));

        // Give a possible duplicate insert (or DLQ routing) a chance to show up before asserting it never does.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(1, deceasedCount(animalId));
            assertEquals(baseline, dlq.count());
        });
    }

    @Test
    void shouldNotChangeAnythingForNonDeceasedEvents() {
        UUID registeredAnimalId = UUID.randomUUID();
        UUID observedAnimalId = UUID.randomUUID();
        UUID registeredTreatmentId = seedTreatment(seedRecord(registeredAnimalId), TreatmentStatus.PRESCRIBED, null, null);
        UUID observedTreatmentId = seedTreatment(seedRecord(observedAnimalId), TreatmentStatus.PRESCRIBED, null, null);

        produce(rewriteFixture("animal-registered.json", UUID.randomUUID(), registeredAnimalId));
        produce(statusChangedJson(UUID.randomUUID(), observedAnimalId, Instant.now(), "zoo-vet", "UNDER_OBSERVATION"));

        // Marker event: once its deceased row lands, earlier same-topic records have been consumed
        // (single-partition auto-created topic, so consumption order matches send order).
        UUID markerAnimalId = UUID.randomUUID();
        produce(statusChangedJson(UUID.randomUUID(), markerAnimalId, Instant.now(), "zoo-vet", "DECEASED"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(deceasedExists(markerAnimalId)));

        assertFalse(deceasedExists(registeredAnimalId));
        assertFalse(deceasedExists(observedAnimalId));
        assertEquals(TreatmentStatus.PRESCRIBED, treatmentStatus(registeredTreatmentId));
        assertEquals(TreatmentStatus.PRESCRIBED, treatmentStatus(observedTreatmentId));
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

        UUID animalId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        produce(statusChangedJson(eventId, animalId, Instant.now(), "zoo-vet", "DECEASED"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(deceasedExists(animalId)));
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

    private String statusChangedJson(UUID eventId, UUID animalId, Instant occurredAt, String performedBy, String newStatus) {
        try (InputStream in = getClass().getResourceAsStream("/contract/animal-status-changed.json")) {
            ObjectNode envelope = (ObjectNode) objectMapper.readTree(in);
            envelope.put("eventId", eventId.toString());
            envelope.put("animalId", animalId.toString());
            envelope.put("occurredAt", occurredAt.toString());
            envelope.put("performedBy", performedBy);
            ((ObjectNode) envelope.get("payload")).put("newStatus", newStatus);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite fixture animal-status-changed.json", e);
        }
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

    private UUID seedRecord(UUID animalId) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            MedicalRecordEntity entity = new MedicalRecordEntity();
            entity.setId(id);
            entity.setAnimalId(animalId);
            entity.setReason("Checkup");
            entity.setDiagnosis("Healthy");
            entity.setExaminedOn(LocalDate.of(2026, 1, 1));
            entity.setVeterinarian("Dr Rossi");
            entity.setCreatedBy("system");
            entity.setUpdatedBy("system");
            em.persist(entity);
        });
        return id;
    }

    private UUID seedTreatment(UUID medicalRecordId, TreatmentStatus status, LocalDate startedOn, LocalDate endedOn) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TreatmentEntity entity = new TreatmentEntity();
            entity.setId(id);
            entity.setMedicalRecordId(medicalRecordId);
            entity.setDescription("Antibiotics");
            entity.setStatus(status);
            entity.setStartedOn(startedOn);
            entity.setEndedOn(endedOn);
            entity.setCreatedBy("system");
            entity.setUpdatedBy("system");
            em.persist(entity);
        });
        return id;
    }

    private boolean deceasedExists(UUID animalId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.find(DeceasedAnimalEntity.class, animalId) != null);
    }

    private long deceasedCount(UUID animalId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createQuery("SELECT COUNT(d) FROM DeceasedAnimalEntity d WHERE d.animalId = :animalId", Long.class)
                        .setParameter("animalId", animalId)
                        .getSingleResult());
    }

    private TreatmentStatus treatmentStatus(UUID treatmentId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.find(TreatmentEntity.class, treatmentId).getStatus());
    }

    private LocalDate treatmentEndedOn(UUID treatmentId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.find(TreatmentEntity.class, treatmentId).getEndedOn());
    }

    private String treatmentUpdatedBy(UUID treatmentId) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.find(TreatmentEntity.class, treatmentId).getUpdatedBy());
    }
}
