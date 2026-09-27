package it.zoo.feeding.application;

import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPage;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListFeedingsServiceTest {

    @Mock
    FeedingPlanRepository planRepository;

    @Mock
    FeedingRepository feedingRepository;

    @InjectMocks
    ListFeedingsService service;

    @Test
    void shouldListFeedings() {
        UUID planId = UUID.randomUUID();
        Feeding feeding = new Feeding(UUID.randomUUID(), planId, Instant.now(), 500, null, "keeper");
        when(planRepository.existsById(planId)).thenReturn(true);
        when(feedingRepository.findPageByPlanId(planId, 0, 10)).thenReturn(List.of(feeding));
        when(feedingRepository.countByPlanId(planId)).thenReturn(1L);

        FeedingPage result = service.list(planId, 0, 10);

        assertEquals(1, result.items().size());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(1L, result.total());
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        UUID planId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(planId, -1, 10));
    }

    @Test
    void shouldThrowWhenSizeIsZero() {
        UUID planId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(planId, 0, 0));
    }

    @Test
    void shouldThrowWhenSizeExceedsMaxPageSize() {
        UUID planId = UUID.randomUUID();

        assertThrows(InvalidFeedingDataException.class, () -> service.list(planId, 0, 101));
    }

    @Test
    void shouldThrowWhenPlanNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.existsById(planId)).thenReturn(false);

        assertThrows(FeedingPlanNotFoundException.class, () -> service.list(planId, 0, 10));
    }
}
