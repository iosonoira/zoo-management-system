package it.zoo.notification.application;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.exception.InvalidAnimalEventException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;
import it.zoo.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandleAnimalEventServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    HandleAnimalEventService service;

    private HandleAnimalEventCommand validCommand() {
        return new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );
    }

    @Test
    void shouldSaveNotificationForNewEvent() {
        when(repository.existsByEventId(any(UUID.class))).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        UUID eventId = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            eventId, AnimalEventType.ANIMAL_REGISTERED, occurredAt,
            animalId, "keeper", "Leo", "Lion", false, null, null, null, null
        );

        service.handle(cmd);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(captor.capture());

        Notification captured = captor.getValue();
        assertNotNull(captured.getId());
        assertEquals(eventId, captured.getEventId());
        assertEquals(animalId, captured.getAnimalId());
        assertEquals(AnimalEventType.ANIMAL_REGISTERED, captured.getEventType());
        assertEquals(Severity.INFO, captured.getSeverity());
        assertEquals("Leo (Lion) was registered", captured.getMessage());
        assertEquals(occurredAt, captured.getOccurredAt());
        assertNotNull(captured.getCreatedAt());
    }

    @Test
    void shouldStoreStructuredFieldsForRegisteredEvent() {
        when(repository.existsByEventId(any(UUID.class))).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "zoo-admin", "Leo", "Lion", true, null, null, null, null
        ));

        Notification captured = captureSaved();
        assertEquals("zoo-admin", captured.getPerformedBy());
        assertEquals("Leo", captured.getName());
        assertEquals("Lion", captured.getSpecies());
        assertEquals(Boolean.TRUE, captured.getDangerous());
        assertNull(captured.getPreviousStatus());
        assertNull(captured.getNewStatus());
        assertNull(captured.getFromEnclosureId());
        assertNull(captured.getToEnclosureId());
        assertNull(captured.getAcknowledgedBy());
        assertNull(captured.getAcknowledgedAt());
    }

    @Test
    void shouldStoreStructuredFieldsForStatusChangedEvent() {
        when(repository.existsByEventId(any(UUID.class))).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.handle(new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_STATUS_CHANGED, Instant.now(),
            UUID.randomUUID(), "zoo-vet", "Leo", "Lion", null, "HEALTHY", "UNDER_OBSERVATION", null, null
        ));

        Notification captured = captureSaved();
        assertEquals("zoo-vet", captured.getPerformedBy());
        assertEquals("Leo", captured.getName());
        assertEquals("Lion", captured.getSpecies());
        assertNull(captured.getDangerous());
        assertEquals("HEALTHY", captured.getPreviousStatus());
        assertEquals("UNDER_OBSERVATION", captured.getNewStatus());
        assertNull(captured.getFromEnclosureId());
        assertNull(captured.getToEnclosureId());
        assertNull(captured.getAcknowledgedBy());
        assertNull(captured.getAcknowledgedAt());
    }

    @Test
    void shouldStoreStructuredFieldsForTransferredEvent() {
        when(repository.existsByEventId(any(UUID.class))).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        service.handle(new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_TRANSFERRED, Instant.now(),
            UUID.randomUUID(), "zoo-keeper", "Leo", "Lion", true, null, null, from, to
        ));

        Notification captured = captureSaved();
        assertEquals("zoo-keeper", captured.getPerformedBy());
        assertEquals("Leo", captured.getName());
        assertEquals("Lion", captured.getSpecies());
        assertEquals(Boolean.TRUE, captured.getDangerous());
        assertNull(captured.getPreviousStatus());
        assertNull(captured.getNewStatus());
        assertEquals(from, captured.getFromEnclosureId());
        assertEquals(to, captured.getToEnclosureId());
        assertNull(captured.getAcknowledgedBy());
        assertNull(captured.getAcknowledgedAt());
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldNotSaveNotificationForDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            eventId, AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        service.handle(cmd);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEventIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            null, AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenEventTypeIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), null, Instant.now(),
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, Instant.now(),
            null, "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenOccurredAtIsNull() {
        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
            UUID.randomUUID(), AnimalEventType.ANIMAL_REGISTERED, null,
            UUID.randomUUID(), "keeper", "Leo", "Lion", false, null, null, null, null
        );

        assertThrows(InvalidAnimalEventException.class, () -> service.handle(cmd));
        verifyNoInteractions(repository);
    }
}
