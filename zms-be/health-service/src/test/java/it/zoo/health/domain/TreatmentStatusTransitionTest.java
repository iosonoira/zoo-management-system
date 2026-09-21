package it.zoo.health.domain;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TreatmentStatusTransitionTest {

    private Treatment treatmentWithStatus(TreatmentStatus status) {
        return new Treatment(UUID.randomUUID(), UUID.randomUUID(), "Antibiotics", status);
    }

    @Test
    void shouldAllowTransitionFromPrescribedToActive() {
        assertTrue(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.ACTIVE));
    }

    @Test
    void shouldAllowTransitionFromPrescribedToCancelled() {
        assertTrue(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.CANCELLED));
    }

    @Test
    void shouldRejectTransitionFromPrescribedToCompleted() {
        assertFalse(treatmentWithStatus(TreatmentStatus.PRESCRIBED).canTransitionTo(TreatmentStatus.COMPLETED));
    }

    @Test
    void shouldAllowTransitionFromActiveToCompleted() {
        assertTrue(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.COMPLETED));
    }

    @Test
    void shouldAllowTransitionFromActiveToCancelled() {
        assertTrue(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.CANCELLED));
    }

    @Test
    void shouldRejectTransitionFromActiveBackToPrescribed() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectAnyTransitionWhenCompleted() {
        Treatment completed = treatmentWithStatus(TreatmentStatus.COMPLETED);
        assertFalse(completed.canTransitionTo(TreatmentStatus.ACTIVE));
        assertFalse(completed.canTransitionTo(TreatmentStatus.CANCELLED));
        assertFalse(completed.canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectAnyTransitionWhenCancelled() {
        Treatment cancelled = treatmentWithStatus(TreatmentStatus.CANCELLED);
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.ACTIVE));
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.COMPLETED));
        assertFalse(cancelled.canTransitionTo(TreatmentStatus.PRESCRIBED));
    }

    @Test
    void shouldRejectTransitionToSameStatus() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(TreatmentStatus.ACTIVE));
    }

    @Test
    void shouldRejectTransitionToNull() {
        assertFalse(treatmentWithStatus(TreatmentStatus.ACTIVE).canTransitionTo(null));
    }
}
