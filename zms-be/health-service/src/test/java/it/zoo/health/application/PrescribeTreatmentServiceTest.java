package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescribeTreatmentServiceTest {

    @Mock
    MedicalRecordRepository recordRepository;

    @Mock
    TreatmentRepository treatmentRepository;

    @InjectMocks
    PrescribeTreatmentService service;

    @Test
    void shouldPrescribeTreatmentInPrescribedStatus() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.existsById(recordId)).thenReturn(true);
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet"));

        assertNotNull(result.getId());
        assertEquals(recordId, result.getMedicalRecordId());
        assertEquals("Antibiotics", result.getDescription());
        assertEquals(TreatmentStatus.PRESCRIBED, result.getStatus());
        assertNull(result.getStartedOn());
        assertNull(result.getEndedOn());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenMedicalRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.existsById(recordId)).thenReturn(false);

        assertThrows(MedicalRecordNotFoundException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet")));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(UUID.randomUUID(), "Antibiotics", " ")));
    }

    @Test
    void shouldThrowWhenMedicalRecordIdIsNull() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(null, "Antibiotics", "vet")));
    }

    @Test
    void shouldThrowWhenDescriptionIsBlank() {
        assertThrows(InvalidMedicalDataException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(UUID.randomUUID(), "", "vet")));
    }
}
