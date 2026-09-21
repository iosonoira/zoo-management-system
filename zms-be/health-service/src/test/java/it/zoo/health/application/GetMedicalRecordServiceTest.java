package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetMedicalRecordServiceTest {

    @Mock
    MedicalRecordRepository recordRepository;

    @Mock
    TreatmentRepository treatmentRepository;

    @InjectMocks
    GetMedicalRecordService service;

    @Test
    void shouldReturnRecordWithItsTreatments() {
        UUID id = UUID.randomUUID();
        MedicalRecord record = new MedicalRecord(id, UUID.randomUUID(), "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
        Treatment treatment = new Treatment(UUID.randomUUID(), id, "Rest", TreatmentStatus.PRESCRIBED);
        when(recordRepository.findById(id)).thenReturn(Optional.of(record));
        when(treatmentRepository.findByMedicalRecordId(id)).thenReturn(List.of(treatment));

        MedicalRecordDetail result = service.getById(id);

        assertEquals(id, result.record().getId());
        assertEquals(1, result.treatments().size());
        assertEquals("Rest", result.treatments().get(0).getDescription());
    }

    @Test
    void shouldReturnEmptyTreatmentListWhenNonePrescribed() {
        UUID id = UUID.randomUUID();
        MedicalRecord record = new MedicalRecord(id, UUID.randomUUID(), "Checkup",
                "Healthy", LocalDate.of(2026, 9, 1), "Dr Rossi");
        when(recordRepository.findById(id)).thenReturn(Optional.of(record));
        when(treatmentRepository.findByMedicalRecordId(id)).thenReturn(List.of());

        MedicalRecordDetail result = service.getById(id);

        assertTrue(result.treatments().isEmpty());
    }

    @Test
    void shouldThrowWhenRecordNotFound() {
        UUID id = UUID.randomUUID();
        when(recordRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(MedicalRecordNotFoundException.class, () -> service.getById(id));
    }
}
