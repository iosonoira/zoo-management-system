package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.AnimalDeceasedException;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class UpdateTreatmentStatusServiceTest {

    @Mock
    TreatmentRepository repository;

    @Mock
    DeceasedAnimalRepository deceasedAnimals;

    @Mock
    AnimalLock animalLock;

    @InjectMocks
    UpdateTreatmentStatusService service;

    private final UUID animalId = UUID.randomUUID();

    @BeforeEach
    void animalIsAlive() {
        when(repository.findAnimalIdByTreatmentId(any(UUID.class))).thenReturn(Optional.of(animalId));
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
    }

    private Treatment treatmentWithStatus(UUID id, TreatmentStatus status) {
        return new Treatment(id, UUID.randomUUID(), "Antibiotics", status);
    }

    @Test
    void shouldSetStartedOnWhenActivated() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.PRESCRIBED)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.ACTIVE, "vet");

        assertEquals(TreatmentStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.now(), result.getStartedOn());
        assertNull(result.getEndedOn());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldAcquireAnimalLockBeforeReadingTheTreatment() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.ACTIVE)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        service.updateStatus(id, TreatmentStatus.COMPLETED, "vet");

        InOrder inOrder = inOrder(repository, animalLock);
        inOrder.verify(repository).findAnimalIdByTreatmentId(id);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(repository).findById(id);
        inOrder.verify(repository).save(any(Treatment.class));
    }

    @Test
    void shouldSetEndedOnWhenCompleted() {
        UUID id = UUID.randomUUID();
        Treatment active = treatmentWithStatus(id, TreatmentStatus.ACTIVE);
        active.setStartedOn(LocalDate.of(2026, 9, 1));
        when(repository.findById(id)).thenReturn(Optional.of(active));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.COMPLETED, "vet");

        assertEquals(TreatmentStatus.COMPLETED, result.getStatus());
        assertEquals(LocalDate.of(2026, 9, 1), result.getStartedOn());
        assertEquals(LocalDate.now(), result.getEndedOn());
    }

    @Test
    void shouldSetEndedOnWhenCancelled() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.PRESCRIBED)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.CANCELLED, "vet");

        assertEquals(TreatmentStatus.CANCELLED, result.getStatus());
        assertEquals(LocalDate.now(), result.getEndedOn());
    }

    @Test
    void shouldNotOverwriteStartedOnWhenAlreadySet() {
        UUID id = UUID.randomUUID();
        Treatment prescribed = treatmentWithStatus(id, TreatmentStatus.PRESCRIBED);
        prescribed.setStartedOn(LocalDate.of(2026, 1, 1));
        when(repository.findById(id)).thenReturn(Optional.of(prescribed));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.ACTIVE, "vet");

        assertEquals(LocalDate.of(2026, 1, 1), result.getStartedOn());
    }

    @Test
    void shouldThrowWhenActivatingForDeceasedAnimal() {
        UUID id = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.PRESCRIBED)));

        assertThrows(AnimalDeceasedException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCompleteForDeceasedAnimal() {
        UUID id = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.ACTIVE)));
        when(repository.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        Treatment result = service.updateStatus(id, TreatmentStatus.COMPLETED, "vet");

        assertEquals(TreatmentStatus.COMPLETED, result.getStatus());
    }

    @Test
    void shouldThrowWhenTreatmentNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findAnimalIdByTreatmentId(id)).thenReturn(Optional.empty());

        assertThrows(TreatmentNotFoundException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));

        verifyNoInteractions(animalLock);
        verify(repository, never()).findById(any());
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(treatmentWithStatus(id, TreatmentStatus.COMPLETED)));

        assertThrows(InvalidTreatmentStatusTransitionException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        assertThrows(InvalidMedicalDataException.class,
                () -> service.updateStatus(UUID.randomUUID(), TreatmentStatus.ACTIVE, ""));
    }
}
