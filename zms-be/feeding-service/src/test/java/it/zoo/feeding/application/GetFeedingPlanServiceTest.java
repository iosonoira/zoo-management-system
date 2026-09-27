package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFeedingPlanServiceTest {

    @Mock
    FeedingPlanRepository repository;

    @InjectMocks
    GetFeedingPlanService service;

    @Test
    void shouldGetFeedingPlan() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.ACTIVE, LocalDate.now());
        when(repository.findById(planId)).thenReturn(Optional.of(plan));

        FeedingPlan result = service.getById(planId);

        assertEquals(planId, result.getId());
        assertEquals("Hay", result.getFood());
    }

    @Test
    void shouldThrowWhenPlanNotFound() {
        UUID planId = UUID.randomUUID();
        when(repository.findById(planId)).thenReturn(Optional.empty());

        assertThrows(FeedingPlanNotFoundException.class, () -> service.getById(planId));
    }
}
