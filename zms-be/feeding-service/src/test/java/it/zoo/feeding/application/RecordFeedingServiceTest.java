package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.FeedingPlanNotActiveException;
import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.RecordFeedingCommand;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordFeedingServiceTest {

    @Mock
    FeedingPlanRepository planRepository;

    @Mock
    FeedingRepository feedingRepository;

    @InjectMocks
    RecordFeedingService service;

    @Test
    void shouldRecordFeeding() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.ACTIVE, LocalDate.now());
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(feedingRepository.save(any(Feeding.class))).thenAnswer(i -> i.getArgument(0));

        Instant fedAt = Instant.now();
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, fedAt, 500, "Fed well", "keeper");

        Feeding result = service.record(cmd);

        assertNotNull(result.getId());
        assertEquals(planId, result.getPlanId());
        assertEquals(fedAt, result.getFedAt());
        assertEquals(500, result.getQuantityGrams());
        assertEquals("keeper", result.getRecordedBy());
    }

    @Test
    void shouldDefaultFedAtToNow() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.ACTIVE, LocalDate.now());
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(feedingRepository.save(any(Feeding.class))).thenAnswer(i -> i.getArgument(0));

        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, null, 500, null, "keeper");

        Feeding result = service.record(cmd);

        assertNotNull(result.getFedAt());
        assertTrue(result.getFedAt().isBefore(Instant.now().plusSeconds(5)));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID planId = UUID.randomUUID();
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, Instant.now(), 500, null, "");

        assertThrows(InvalidFeedingDataException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPlanIdIsNull() {
        RecordFeedingCommand cmd = new RecordFeedingCommand(null, Instant.now(), 500, null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenQuantityIsNegative() {
        UUID planId = UUID.randomUUID();
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, Instant.now(), -1, null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFedAtIsInTheFuture() {
        UUID planId = UUID.randomUUID();
        Instant futureTime = Instant.now().plusSeconds(120);
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, futureTime, 500, null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPlanNotFound() {
        UUID planId = UUID.randomUUID();
        when(planRepository.findById(planId)).thenReturn(Optional.empty());
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, Instant.now(), 500, null, "keeper");

        assertThrows(FeedingPlanNotFoundException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPlanIsSuspended() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.SUSPENDED, LocalDate.now());
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, Instant.now(), 500, null, "keeper");

        assertThrows(FeedingPlanNotActiveException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPlanIsEnded() {
        UUID planId = UUID.randomUUID();
        FeedingPlan plan = new FeedingPlan(planId, UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, PlanStatus.ENDED, LocalDate.now());
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        RecordFeedingCommand cmd = new RecordFeedingCommand(planId, Instant.now(), 500, null, "keeper");

        assertThrows(FeedingPlanNotActiveException.class, () -> service.record(cmd));
        verify(feedingRepository, never()).save(any());
    }
}
