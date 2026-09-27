package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.model.FeedingPlanPage;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListFeedingPlansServiceTest {

    @Mock
    FeedingPlanRepository repository;

    @InjectMocks
    ListFeedingPlansService service;

    @Test
    void shouldListFeedingPlans() {
        UUID animalId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.ACTIVE, LocalDate.now());
        when(repository.findPage(animalId, 0, 10)).thenReturn(List.of(plan));
        when(repository.count(animalId)).thenReturn(1L);

        FeedingPlanPage result = service.list(animalId, 0, 10);

        assertEquals(1, result.items().size());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(1L, result.total());
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(animalId, -1, 10));
    }

    @Test
    void shouldThrowWhenSizeIsZero() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(animalId, 0, 0));
    }

    @Test
    void shouldThrowWhenSizeExceedsMaxPageSize() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(animalId, 0, 101));
    }
}
