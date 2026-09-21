package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreatmentEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Treatment original = new Treatment(id, recordId, "Antibiotics", TreatmentStatus.ACTIVE);
        original.setStartedOn(LocalDate.of(2026, 9, 2));
        original.setEndedOn(LocalDate.of(2026, 9, 9));
        original.setCreatedBy("vet");
        original.setUpdatedBy("admin");
        original.setVersion(2L);

        Treatment result = TreatmentEntityMapper.toDomain(
                TreatmentEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(recordId, result.getMedicalRecordId());
        assertEquals("Antibiotics", result.getDescription());
        assertEquals(TreatmentStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.of(2026, 9, 2), result.getStartedOn());
        assertEquals(LocalDate.of(2026, 9, 9), result.getEndedOn());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("admin", result.getUpdatedBy());
        assertEquals(2L, result.getVersion());
    }
}
