package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.TreatmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateTreatmentStatusServiceTest {

    @Mock
    TreatmentRepository repository;

    @InjectMocks
    UpdateTreatmentStatusService service;

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
    void shouldThrowWhenTreatmentNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(TreatmentNotFoundException.class,
                () -> service.updateStatus(id, TreatmentStatus.ACTIVE, "vet"));
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
