package it.zoo.feeding.domain;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.model.FeedingPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlanStatusTransitionTest {

    private FeedingPlan planWithStatus(PlanStatus status) {
        return new FeedingPlan(UUID.randomUUID(), UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, status, LocalDate.now());
    }

    @Test
    void shouldAllowTransitionFromActiveToSuspended() {
        assertTrue(planWithStatus(PlanStatus.ACTIVE).canTransitionTo(PlanStatus.SUSPENDED));
    }

    @Test
    void shouldAllowTransitionFromActiveToEnded() {
        assertTrue(planWithStatus(PlanStatus.ACTIVE).canTransitionTo(PlanStatus.ENDED));
    }

    @Test
    void shouldRejectTransitionFromActiveToActive() {
        assertFalse(planWithStatus(PlanStatus.ACTIVE).canTransitionTo(PlanStatus.ACTIVE));
    }

    @Test
    void shouldAllowTransitionFromSuspendedToActive() {
        assertTrue(planWithStatus(PlanStatus.SUSPENDED).canTransitionTo(PlanStatus.ACTIVE));
    }

    @Test
    void shouldAllowTransitionFromSuspendedToEnded() {
        assertTrue(planWithStatus(PlanStatus.SUSPENDED).canTransitionTo(PlanStatus.ENDED));
    }

    @Test
    void shouldRejectTransitionFromSuspendedToSuspended() {
        assertFalse(planWithStatus(PlanStatus.SUSPENDED).canTransitionTo(PlanStatus.SUSPENDED));
    }

    @Test
    void shouldRejectAnyTransitionWhenEnded() {
        FeedingPlan ended = planWithStatus(PlanStatus.ENDED);
        assertFalse(ended.canTransitionTo(PlanStatus.ACTIVE));
        assertFalse(ended.canTransitionTo(PlanStatus.SUSPENDED));
        assertFalse(ended.canTransitionTo(PlanStatus.ENDED));
    }

    @Test
    void shouldRejectTransitionToNull() {
        assertFalse(planWithStatus(PlanStatus.ACTIVE).canTransitionTo(null));
    }
}
