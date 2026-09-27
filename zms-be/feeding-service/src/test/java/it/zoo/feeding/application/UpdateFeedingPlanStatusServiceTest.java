package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.exception.InvalidPlanStatusTransitionException;
import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateFeedingPlanStatusServiceTest {

    @Mock
    FeedingPlanRepository repository;

    @InjectMocks
    UpdateFeedingPlanStatusService service;

    private FeedingPlan planWithStatus(UUID planId, PlanStatus status) {
        return new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, status, LocalDate.now());
    }

    @Test
    void shouldUpdateToSuspended() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = planWithStatus(planId, PlanStatus.ACTIVE);
        when(repository.findByIdForUpdate(planId)).thenReturn(Optional.of(plan));
        when(repository.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        FeedingPlan result = service.updateStatus(planId, PlanStatus.SUSPENDED, "keeper");

        assertEquals(PlanStatus.SUSPENDED, result.getStatus());
        assertNull(result.getEndedOn());
        assertEquals("keeper", result.getUpdatedBy());
    }

    @Test
    void shouldUpdateToEnded() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = planWithStatus(planId, PlanStatus.ACTIVE);
        when(repository.findByIdForUpdate(planId)).thenReturn(Optional.of(plan));
        when(repository.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        FeedingPlan result = service.updateStatus(planId, PlanStatus.ENDED, "keeper");

        assertEquals(PlanStatus.ENDED, result.getStatus());
        assertEquals(LocalDate.now(), result.getEndedOn());
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID planId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.updateStatus(planId, PlanStatus.SUSPENDED, ""));
    }

    @Test
    void shouldThrowWhenPlanNotFound() {
        UUID planId = UUID.randomUUID();
        when(repository.findByIdForUpdate(planId)).thenReturn(Optional.empty());

        assertThrows(FeedingPlanNotFoundException.class, () -> service.updateStatus(planId, PlanStatus.SUSPENDED, "keeper"));
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = planWithStatus(planId, PlanStatus.ENDED);
        when(repository.findByIdForUpdate(planId)).thenReturn(Optional.of(plan));

        assertThrows(InvalidPlanStatusTransitionException.class, () -> service.updateStatus(planId, PlanStatus.ACTIVE, "keeper"));
    }

    @Test
    void shouldNotSetEndedOnWhenNotEnded() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = planWithStatus(planId, PlanStatus.SUSPENDED);
        plan.setEndedOn(null);
        when(repository.findByIdForUpdate(planId)).thenReturn(Optional.of(plan));
        when(repository.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        FeedingPlan result = service.updateStatus(planId, PlanStatus.ACTIVE, "keeper");

        assertNull(result.getEndedOn());
    }
}
