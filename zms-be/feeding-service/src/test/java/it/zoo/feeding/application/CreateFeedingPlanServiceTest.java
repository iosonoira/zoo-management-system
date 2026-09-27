package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.AnimalDeceasedException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.CreateFeedingPlanCommand;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateFeedingPlanServiceTest {

    @Mock
    FeedingPlanRepository repository;

    @Mock
    DeceasedAnimalRepository deceasedAnimals;

    @InjectMocks
    CreateFeedingPlanService service;

    private CreateFeedingPlanCommand validCommand() {
        return new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0), LocalTime.of(14, 0)), "Good hay", "keeper");
    }

    @Test
    void shouldCreateFeedingPlan() {
        when(repository.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(deceasedAnimals.existsByAnimalId(any())).thenReturn(false);
        CreateFeedingPlanCommand cmd = validCommand();

        FeedingPlan result = service.create(cmd);

        assertNotNull(result.getId());
        assertEquals(cmd.animalId(), result.getAnimalId());
        assertEquals("Hay", result.getFood());
        assertEquals(500, result.getQuantityGrams());
        assertEquals(2, result.getFeedingTimes().size());
        assertEquals(LocalTime.of(9, 0), result.getFeedingTimes().get(0));
        assertEquals(LocalTime.of(14, 0), result.getFeedingTimes().get(1));
        assertEquals(PlanStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.now(), result.getStartedOn());
        assertEquals("keeper", result.getCreatedBy());
        assertEquals("keeper", result.getUpdatedBy());
    }

    @Test
    void shouldSortFeedingTimes() {
        when(repository.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));
        when(deceasedAnimals.existsByAnimalId(any())).thenReturn(false);
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(14, 0), LocalTime.of(9, 0)), null, "keeper");

        FeedingPlan result = service.create(cmd);

        assertEquals(LocalTime.of(9, 0), result.getFeedingTimes().get(0));
        assertEquals(LocalTime.of(14, 0), result.getFeedingTimes().get(1));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0)), null, "");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                null, "Hay", 500, List.of(LocalTime.of(9, 0)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFoodIsBlank() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "  ", 500, List.of(LocalTime.of(9, 0)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenQuantityIsZero() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 0, List.of(LocalTime.of(9, 0)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenQuantityIsNegative() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", -1, List.of(LocalTime.of(9, 0)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFeedingTimesIsEmpty() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFeedingTimesContainsNull() {
        List<LocalTime> timesWithNull = new java.util.ArrayList<>();
        timesWithNull.add(LocalTime.of(9, 0));
        timesWithNull.add(null);
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, timesWithNull, null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenMoreThanSixFeedingTimes() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500,
                List.of(LocalTime.of(6, 0), LocalTime.of(9, 0), LocalTime.of(12, 0),
                        LocalTime.of(14, 0), LocalTime.of(17, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)),
                null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFeedingTimesHaveDuplicates() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0), LocalTime.of(9, 0)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenFeedingTimeHasSeconds() {
        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                UUID.randomUUID(), "Hay", 500, List.of(LocalTime.of(9, 0, 30)), null, "keeper");

        assertThrows(InvalidFeedingDataException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAnimalIsDeceased() {
        when(deceasedAnimals.existsByAnimalId(any())).thenReturn(true);
        CreateFeedingPlanCommand cmd = validCommand();

        assertThrows(AnimalDeceasedException.class, () -> service.create(cmd));
        verify(repository, never()).save(any());
    }
}
