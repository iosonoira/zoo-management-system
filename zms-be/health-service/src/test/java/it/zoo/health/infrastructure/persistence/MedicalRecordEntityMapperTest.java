package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.MedicalRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalRecordEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        MedicalRecord original = new MedicalRecord(id, animalId, "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
        original.setCreatedBy("vet");
        original.setUpdatedBy("admin");
        original.setVersion(3L);

        MedicalRecord result = MedicalRecordEntityMapper.toDomain(
                MedicalRecordEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(animalId, result.getAnimalId());
        assertEquals("Limping", result.getReason());
        assertEquals("Sprained paw", result.getDiagnosis());
        assertEquals(LocalDate.of(2026, 9, 1), result.getExaminedOn());
        assertEquals("Dr Rossi", result.getVeterinarian());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("admin", result.getUpdatedBy());
        assertEquals(3L, result.getVersion());
    }
}
