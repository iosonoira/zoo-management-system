package it.zoo.notification.application;

import it.zoo.notification.domain.exception.NotificationNotFoundException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcknowledgeNotificationServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    AcknowledgeNotificationService service;

    private Notification acknowledged(UUID id, String by, Instant at) {
        Notification n = new Notification();
        n.setId(id);
        n.setAcknowledgedBy(by);
        n.setAcknowledgedAt(at);
        return n;
    }

    @Test
    void shouldAcknowledgeAndReturnTheReloadedNotification() {
        UUID id = UUID.randomUUID();
        Instant before = Instant.now();
        when(repository.acknowledge(eq(id), eq("vet.bianchi"), any(Instant.class))).thenReturn(true);
        when(repository.findById(id)).thenReturn(Optional.of(acknowledged(id, "vet.bianchi", before)));

        Notification result = service.acknowledge(id, "vet.bianchi");

        assertEquals("vet.bianchi", result.getAcknowledgedBy());
        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(repository).acknowledge(eq(id), eq("vet.bianchi"), at.capture());
        assertFalse(at.getValue().isBefore(before));
        assertFalse(at.getValue().isAfter(Instant.now()));
    }

    @Test
    void shouldReturnTheExistingAcknowledgementWhenAlreadyAcknowledged() {
        UUID id = UUID.randomUUID();
        Instant first = Instant.parse("2026-09-30T08:00:00Z");
        when(repository.acknowledge(eq(id), eq("keeper.conti"), any(Instant.class))).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.of(acknowledged(id, "vet.bianchi", first)));

        Notification result = service.acknowledge(id, "keeper.conti");

        assertEquals("vet.bianchi", result.getAcknowledgedBy());
        assertEquals(first, result.getAcknowledgedAt());
    }

    @Test
    void shouldThrowWhenNotificationDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.acknowledge(eq(id), eq("vet.bianchi"), any(Instant.class))).thenReturn(false);
        when(repository.findById(id)).thenReturn(Optional.empty());

        NotificationNotFoundException ex = assertThrows(NotificationNotFoundException.class,
                () -> service.acknowledge(id, "vet.bianchi"));
        assertEquals("Notification not found with id: " + id, ex.getMessage());
    }
}
