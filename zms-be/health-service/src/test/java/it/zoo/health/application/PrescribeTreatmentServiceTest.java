package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.AnimalDeceasedException;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescribeTreatmentServiceTest {

    @Mock
    MedicalRecordRepository recordRepository;

    @Mock
    TreatmentRepository treatmentRepository;

    @Mock
    DeceasedAnimalRepository deceasedAnimals;

    @Mock
    AnimalLock animalLock;

    @InjectMocks
    PrescribeTreatmentService service;

    private MedicalRecord recordFor(UUID recordId, UUID animalId) {
        return new MedicalRecord(recordId, animalId, "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi");
    }

    @Test
    void shouldPrescribeTreatmentInPrescribedStatus() {
        UUID recordId = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        when(recordRepository.findById(recordId)).thenReturn(Optional.of(recordFor(recordId, animalId)));
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
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

        InOrder inOrder = inOrder(animalLock, deceasedAnimals, treatmentRepository);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(deceasedAnimals).existsByAnimalId(animalId);
        inOrder.verify(treatmentRepository).save(any(Treatment.class));
    }

    @Test
    void shouldThrowWhenAnimalIsDeceased() {
        UUID recordId = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        when(recordRepository.findById(recordId)).thenReturn(Optional.of(recordFor(recordId, animalId)));
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(true);

        assertThrows(AnimalDeceasedException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet")));

        verify(animalLock).acquire(animalId);
        verify(treatmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenMedicalRecordDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(recordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThrows(MedicalRecordNotFoundException.class, () -> service.prescribe(
                new PrescribeTreatmentCommand(recordId, "Antibiotics", "vet")));

        verifyNoInteractions(animalLock, deceasedAnimals, treatmentRepository);
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
