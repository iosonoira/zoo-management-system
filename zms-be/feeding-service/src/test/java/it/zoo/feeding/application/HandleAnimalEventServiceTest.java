package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.InvalidAnimalEventException;
import it.zoo.feeding.domain.model.DeceasedAnimal;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.HandleAnimalEventCommand;
import it.zoo.feeding.domain.port.out.AnimalLock;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleAnimalEventServiceTest {

    @Mock
    DeceasedAnimalRepository deceasedAnimals;

    @Mock
    FeedingPlanRepository feedingPlans;

    @Mock
    AnimalLock animalLock;

    @InjectMocks
    HandleAnimalEventService service;

    private HandleAnimalEventCommand statusChangedCommand(UUID animalId, String newStatus, String performedBy) {
        return new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, Instant.now(), performedBy, newStatus);
    }

    @Test
    void shouldThrowWhenCommandIsNull() {
        assertThrows(InvalidAnimalEventException.class, () -> service.handle(null));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldThrowWhenEventIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                null, "ANIMAL_STATUS_CHANGED", UUID.randomUUID(), Instant.now(), "keeper", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldThrowWhenEventTypeIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), null, UUID.randomUUID(), Instant.now(), "keeper", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", null, Instant.now(), "keeper", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldThrowWhenOccurredAtIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", UUID.randomUUID(), null, "keeper", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldIgnoreNonStatusChangedEventTypes() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_REGISTERED", UUID.randomUUID(), Instant.now(), "keeper", null);

        service.handle(cmd);

        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldThrowWhenStatusChangedEventHasNullNewStatus() {
        HandleAnimalEventCommand cmd = statusChangedCommand(UUID.randomUUID(), null, "keeper");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldIgnoreStatusChangeToNonDeceasedStatus() {
        HandleAnimalEventCommand cmd = statusChangedCommand(UUID.randomUUID(), "UNDER_OBSERVATION", "keeper");

        service.handle(cmd);

        verifyNoInteractions(deceasedAnimals, feedingPlans, animalLock);
    }

    @Test
    void shouldBeIdempotentWhenAnimalAlreadyMarkedDeceased() {
        UUID animalId = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(true);
        HandleAnimalEventCommand cmd = statusChangedCommand(animalId, "DECEASED", "keeper");

        service.handle(cmd);

        verify(deceasedAnimals, never()).save(any());
        verifyNoInteractions(feedingPlans);

        InOrder inOrder = inOrder(animalLock, deceasedAnimals);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(deceasedAnimals).existsByAnimalId(animalId);
    }

    @Test
    void shouldMarkAnimalDeceasedAndEndActiveAndSuspendedPlans() {
        UUID animalId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-23T10:15:30Z");
        UUID eventId = UUID.randomUUID();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                eventId, "ANIMAL_STATUS_CHANGED", animalId, occurredAt, "zoo-vet", "DECEASED");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);

        FeedingPlan activePlan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500,
                List.of(), null, PlanStatus.ACTIVE, LocalDate.of(2026, 9, 1));
        FeedingPlan suspendedPlan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500,
                List.of(), null, PlanStatus.SUSPENDED, LocalDate.of(2026, 9, 1));
        when(feedingPlans.findByAnimalIdAndStatusInForUpdate(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(activePlan, suspendedPlan));
        when(feedingPlans.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(cmd);

        ArgumentCaptor<DeceasedAnimal> deceasedCaptor = ArgumentCaptor.forClass(DeceasedAnimal.class);
        verify(deceasedAnimals, times(1)).save(deceasedCaptor.capture());
        assertEquals(animalId, deceasedCaptor.getValue().animalId());
        assertEquals(eventId, deceasedCaptor.getValue().eventId());
        assertEquals(occurredAt, deceasedCaptor.getValue().occurredAt());

        ArgumentCaptor<FeedingPlan> planCaptor = ArgumentCaptor.forClass(FeedingPlan.class);
        verify(feedingPlans, times(2)).save(planCaptor.capture());
        for (FeedingPlan saved : planCaptor.getAllValues()) {
            assertEquals(PlanStatus.ENDED, saved.getStatus());
            assertEquals(LocalDate.ofInstant(occurredAt, ZoneId.systemDefault()), saved.getEndedOn());
            assertEquals("zoo-vet", saved.getUpdatedBy());
        }

        InOrder inOrder = inOrder(animalLock, deceasedAnimals, feedingPlans);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(deceasedAnimals).existsByAnimalId(animalId);
        inOrder.verify(feedingPlans).findByAnimalIdAndStatusInForUpdate(eq(animalId), any(Collection.class));
    }

    @Test
    void shouldNotEndPlanBeforeItStarted() {
        UUID animalId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-23T10:15:30Z");
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, occurredAt, "zoo-vet", "DECEASED");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        LocalDate startedAfterDeath = LocalDate.of(2026, 9, 25);
        FeedingPlan plan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500,
                List.of(), null, PlanStatus.ACTIVE, startedAfterDeath);
        when(feedingPlans.findByAnimalIdAndStatusInForUpdate(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(plan));
        when(feedingPlans.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(cmd);

        assertEquals(startedAfterDeath, plan.getEndedOn());
    }

    @Test
    void shouldDefaultUpdatedByToAnimalServiceWhenPerformedByIsBlank() {
        UUID animalId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, occurredAt, "  ", "DECEASED");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        FeedingPlan activePlan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500,
                List.of(), null, PlanStatus.ACTIVE, LocalDate.of(2026, 9, 1));
        when(feedingPlans.findByAnimalIdAndStatusInForUpdate(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(activePlan));
        when(feedingPlans.save(any(FeedingPlan.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(cmd);

        ArgumentCaptor<FeedingPlan> planCaptor = ArgumentCaptor.forClass(FeedingPlan.class);
        verify(feedingPlans, times(1)).save(planCaptor.capture());
        assertEquals("animal-service", planCaptor.getValue().getUpdatedBy());
    }

    @Test
    void shouldNotSaveAnyPlanWhenNoneAreActiveOrSuspended() {
        UUID animalId = UUID.randomUUID();
        HandleAnimalEventCommand cmd = statusChangedCommand(animalId, "DECEASED", "keeper");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        when(feedingPlans.findByAnimalIdAndStatusInForUpdate(eq(animalId), any(Collection.class)))
                .thenReturn(List.of());

        service.handle(cmd);

        verify(deceasedAnimals, times(1)).save(any(DeceasedAnimal.class));
        verify(feedingPlans, never()).save(any());
    }
}
