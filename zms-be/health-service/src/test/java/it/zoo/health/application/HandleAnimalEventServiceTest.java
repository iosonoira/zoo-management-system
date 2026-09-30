package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidAnimalEventException;
import it.zoo.health.domain.model.DeceasedAnimal;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.HandleAnimalEventCommand;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
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
import java.util.EnumSet;
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
    TreatmentRepository treatments;

    @Mock
    AnimalLock animalLock;

    @InjectMocks
    HandleAnimalEventService service;

    private HandleAnimalEventCommand statusChangedCommand(UUID animalId, String newStatus, String performedBy) {
        return new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, Instant.now(), performedBy, newStatus);
    }

    private Treatment treatment(TreatmentStatus status, LocalDate startedOn) {
        Treatment treatment = new Treatment(UUID.randomUUID(), UUID.randomUUID(), "Antibiotics", status);
        treatment.setStartedOn(startedOn);
        return treatment;
    }

    @Test
    void shouldThrowWhenCommandIsNull() {
        assertThrows(InvalidAnimalEventException.class, () -> service.handle(null));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldThrowWhenEventIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                null, "ANIMAL_STATUS_CHANGED", UUID.randomUUID(), Instant.now(), "vet", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldThrowWhenEventTypeIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), null, UUID.randomUUID(), Instant.now(), "vet", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", null, Instant.now(), "vet", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldThrowWhenOccurredAtIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", UUID.randomUUID(), null, "vet", "DECEASED");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldIgnoreNonStatusChangedEventTypes() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_TRANSFERRED", UUID.randomUUID(), Instant.now(), "admin", null);

        service.handle(cmd);

        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldThrowWhenStatusChangedEventHasNullNewStatus() {
        HandleAnimalEventCommand cmd = statusChangedCommand(UUID.randomUUID(), null, "vet");

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldIgnoreStatusChangeToNonDeceasedStatus() {
        HandleAnimalEventCommand cmd = statusChangedCommand(UUID.randomUUID(), "IN_TREATMENT", "vet");

        service.handle(cmd);

        verifyNoInteractions(deceasedAnimals, treatments, animalLock);
    }

    @Test
    void shouldBeIdempotentWhenAnimalAlreadyMarkedDeceased() {
        UUID animalId = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(true);

        service.handle(statusChangedCommand(animalId, "DECEASED", "vet"));

        verify(deceasedAnimals, never()).save(any());
        verifyNoInteractions(treatments);

        InOrder inOrder = inOrder(animalLock, deceasedAnimals);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(deceasedAnimals).existsByAnimalId(animalId);
    }

    @Test
    void shouldMarkAnimalDeceasedAndCancelPrescribedAndActiveTreatments() {
        UUID animalId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-23T10:15:30Z");
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                eventId, "ANIMAL_STATUS_CHANGED", animalId, occurredAt, "zoo-vet", "DECEASED");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        Treatment prescribed = treatment(TreatmentStatus.PRESCRIBED, null);
        Treatment active = treatment(TreatmentStatus.ACTIVE, LocalDate.of(2026, 9, 1));
        when(treatments.findByAnimalIdAndStatusIn(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(prescribed, active));
        when(treatments.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(cmd);

        ArgumentCaptor<DeceasedAnimal> deceasedCaptor = ArgumentCaptor.forClass(DeceasedAnimal.class);
        verify(deceasedAnimals, times(1)).save(deceasedCaptor.capture());
        assertEquals(new DeceasedAnimal(animalId, eventId, occurredAt), deceasedCaptor.getValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TreatmentStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(treatments).findByAnimalIdAndStatusIn(eq(animalId), statusesCaptor.capture());
        assertEquals(EnumSet.of(TreatmentStatus.PRESCRIBED, TreatmentStatus.ACTIVE),
                EnumSet.copyOf(statusesCaptor.getValue()));

        ArgumentCaptor<Treatment> treatmentCaptor = ArgumentCaptor.forClass(Treatment.class);
        verify(treatments, times(2)).save(treatmentCaptor.capture());
        LocalDate diedOn = LocalDate.ofInstant(occurredAt, ZoneId.systemDefault());
        for (Treatment saved : treatmentCaptor.getAllValues()) {
            assertEquals(TreatmentStatus.CANCELLED, saved.getStatus());
            assertEquals(diedOn, saved.getEndedOn());
            assertEquals("zoo-vet", saved.getUpdatedBy());
        }

        InOrder inOrder = inOrder(animalLock, deceasedAnimals, treatments);
        inOrder.verify(animalLock).acquire(animalId);
        inOrder.verify(deceasedAnimals).existsByAnimalId(animalId);
        inOrder.verify(treatments).findByAnimalIdAndStatusIn(eq(animalId), any(Collection.class));
    }

    @Test
    void shouldNotEndTreatmentBeforeItStarted() {
        UUID animalId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-23T10:15:30Z");
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, occurredAt, "zoo-vet", "DECEASED");

        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        LocalDate startedAfterDeath = LocalDate.of(2026, 9, 25);
        Treatment active = treatment(TreatmentStatus.ACTIVE, startedAfterDeath);
        when(treatments.findByAnimalIdAndStatusIn(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(active));
        when(treatments.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(cmd);

        assertEquals(startedAfterDeath, active.getEndedOn());
    }

    @Test
    void shouldDefaultUpdatedByToAnimalServiceWhenPerformedByIsBlank() {
        UUID animalId = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        when(treatments.findByAnimalIdAndStatusIn(eq(animalId), any(Collection.class)))
                .thenReturn(List.of(treatment(TreatmentStatus.PRESCRIBED, null)));
        when(treatments.save(any(Treatment.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(statusChangedCommand(animalId, "DECEASED", "  "));

        ArgumentCaptor<Treatment> treatmentCaptor = ArgumentCaptor.forClass(Treatment.class);
        verify(treatments, times(1)).save(treatmentCaptor.capture());
        assertEquals("animal-service", treatmentCaptor.getValue().getUpdatedBy());
    }

    @Test
    void shouldNotSaveAnyTreatmentWhenNoneAreOpen() {
        UUID animalId = UUID.randomUUID();
        when(deceasedAnimals.existsByAnimalId(animalId)).thenReturn(false);
        when(treatments.findByAnimalIdAndStatusIn(eq(animalId), any(Collection.class)))
                .thenReturn(List.of());

        service.handle(statusChangedCommand(animalId, "DECEASED", "vet"));

        verify(deceasedAnimals, times(1)).save(any(DeceasedAnimal.class));
        verify(treatments, never()).save(any());
    }
}
