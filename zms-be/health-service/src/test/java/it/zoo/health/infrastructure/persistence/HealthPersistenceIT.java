package it.zoo.health.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.DeceasedAnimal;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class HealthPersistenceIT {

    @Inject
    MedicalRecordRepository medicalRecordRepository;

    @Inject
    TreatmentRepository treatmentRepository;

    @Inject
    DeceasedAnimalRepository deceasedAnimalRepository;

    @Inject
    AnimalLock animalLock;

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    private MedicalRecord saveRecord(UUID animalId) {
        MedicalRecord record = new MedicalRecord(UUID.randomUUID(), animalId, "Checkup", "Healthy",
                LocalDate.of(2026, 9, 1), "dr-rossi");
        record.setCreatedBy("dr-rossi");
        return QuarkusTransaction.requiringNew().call(() -> medicalRecordRepository.save(record));
    }

    private Treatment saveTreatment(UUID medicalRecordId, TreatmentStatus status) {
        Treatment treatment = new Treatment(UUID.randomUUID(), medicalRecordId, "Antibiotics", status);
        treatment.setCreatedBy("dr-rossi");
        return QuarkusTransaction.requiringNew().call(() -> treatmentRepository.save(treatment));
    }

    @Test
    void shouldFindAnimalIdByTreatmentId() {
        UUID animalId = UUID.randomUUID();
        MedicalRecord record = saveRecord(animalId);
        Treatment treatment = saveTreatment(record.getId(), TreatmentStatus.PRESCRIBED);

        assertEquals(animalId, treatmentRepository.findAnimalIdByTreatmentId(treatment.getId()).orElseThrow());
    }

    @Test
    void shouldReturnEmptyAnimalIdWhenTreatmentIsUnknown() {
        assertTrue(treatmentRepository.findAnimalIdByTreatmentId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void shouldFindTreatmentsByAnimalIdAndStatusIn() {
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        MedicalRecord recordA1 = saveRecord(animalA);
        MedicalRecord recordA2 = saveRecord(animalA);
        MedicalRecord recordB = saveRecord(animalB);

        Treatment prescribed = saveTreatment(recordA1.getId(), TreatmentStatus.PRESCRIBED);
        Treatment active = saveTreatment(recordA2.getId(), TreatmentStatus.ACTIVE);
        saveTreatment(recordA1.getId(), TreatmentStatus.COMPLETED);
        saveTreatment(recordB.getId(), TreatmentStatus.PRESCRIBED);

        List<Treatment> result = treatmentRepository.findByAnimalIdAndStatusIn(
                animalA, Set.of(TreatmentStatus.PRESCRIBED, TreatmentStatus.ACTIVE));

        assertEquals(2, result.size());
        assertEquals(Set.of(prescribed.getId(), active.getId()),
                Set.of(result.get(0).getId(), result.get(1).getId()));
    }

    @Test
    void shouldTrackDeceasedAnimalExistsAndSave() {
        UUID animalId = UUID.randomUUID();
        assertFalse(deceasedAnimalRepository.existsByAnimalId(animalId));

        DeceasedAnimal deceasedAnimal = new DeceasedAnimal(animalId, UUID.randomUUID(), Instant.now());
        QuarkusTransaction.requiringNew().run(() -> deceasedAnimalRepository.save(deceasedAnimal));

        assertTrue(deceasedAnimalRepository.existsByAnimalId(animalId));
        assertFalse(deceasedAnimalRepository.existsByAnimalId(UUID.randomUUID()));
    }

    @Test
    void shouldAcquireAnimalLockInsideTransaction() {
        assertDoesNotThrow(() ->
                QuarkusTransaction.requiringNew().run(() -> animalLock.acquire(UUID.randomUUID())));
    }
}
