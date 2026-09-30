package it.zoo.notification.application;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.exception.InvalidNotificationQueryException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationPage;
import it.zoo.notification.domain.model.NotificationQuery;
import it.zoo.notification.domain.port.in.ListNotificationsUseCase;
import it.zoo.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListNotificationsServiceTest {

    @Mock
    NotificationRepository repository;

    @InjectMocks
    ListNotificationsService service;

    private Notification notification(UUID animalId) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setEventId(UUID.randomUUID());
        n.setAnimalId(animalId);
        n.setEventType(AnimalEventType.ANIMAL_REGISTERED);
        n.setSeverity(Severity.INFO);
        n.setMessage("Leo (Lion) was registered");
        n.setOccurredAt(Instant.now());
        n.setCreatedAt(Instant.now());
        return n;
    }

    private NotificationQuery queryPassedToFindPage(int page, int size) {
        ArgumentCaptor<NotificationQuery> captor = ArgumentCaptor.forClass(NotificationQuery.class);
        verify(repository).findPage(captor.capture(), eq(page), eq(size));
        return captor.getValue();
    }

    @Test
    void shouldReturnPageWithPageSizeAndTotal() {
        Notification n = notification(UUID.randomUUID());
        when(repository.findPage(any(NotificationQuery.class), eq(2), eq(10))).thenReturn(List.of(n));
        when(repository.count(any(NotificationQuery.class))).thenReturn(21L);

        NotificationPage result = service.list(null, null, false, 2, 10);

        assertEquals(List.of(n), result.items());
        assertEquals(2, result.page());
        assertEquals(10, result.size());
        assertEquals(21L, result.total());
    }

    @Test
    void shouldPassAnimalIdAndOpenOnlyToTheQuery() {
        UUID animalId = UUID.randomUUID();
        when(repository.findPage(any(NotificationQuery.class), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.count(any(NotificationQuery.class))).thenReturn(0L);

        service.list(animalId, null, true, 0, 20);

        NotificationQuery query = queryPassedToFindPage(0, 20);
        assertEquals(animalId, query.animalId());
        assertTrue(query.openOnly());
        verify(repository).count(query);
    }

    @Test
    void shouldLeaveTheQueryUnfilteredWhenNothingIsGiven() {
        when(repository.findPage(any(NotificationQuery.class), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.count(any(NotificationQuery.class))).thenReturn(0L);

        service.list(null, null, false, 0, 20);

        NotificationQuery query = queryPassedToFindPage(0, 20);
        assertNull(query.animalId());
        assertFalse(query.openOnly());
    }

    @Test
    void shouldTurnANullSeverityListIntoAnEmptySet() {
        when(repository.findPage(any(NotificationQuery.class), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.count(any(NotificationQuery.class))).thenReturn(0L);

        service.list(null, null, false, 0, 20);

        assertEquals(Set.of(), queryPassedToFindPage(0, 20).severities());
    }

    @Test
    void shouldTurnAnEmptySeverityListIntoAnEmptySet() {
        when(repository.findPage(any(NotificationQuery.class), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.count(any(NotificationQuery.class))).thenReturn(0L);

        service.list(null, List.of(), false, 0, 20);

        assertEquals(Set.of(), queryPassedToFindPage(0, 20).severities());
    }

    @Test
    void shouldPassValidSeveritiesToTheRepositoryAsASet() {
        when(repository.findPage(any(NotificationQuery.class), anyInt(), anyInt())).thenReturn(List.of());
        when(repository.count(any(NotificationQuery.class))).thenReturn(0L);

        service.list(null, List.of("WARNING", " CRITICAL ", "WARNING"), false, 0, 20);

        assertEquals(Set.of(Severity.WARNING, Severity.CRITICAL), queryPassedToFindPage(0, 20).severities());
    }

    @Test
    void shouldThrowWhenSeverityIsUnknown() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, List.of("INFO", "URGENT"), false, 0, 20));

        assertEquals("Unknown severity: URGENT", e.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSeverityIsLowerCase() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, List.of("warning"), false, 0, 20));

        assertEquals("Unknown severity: warning", e.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSeverityIsBlank() {
        assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, List.of(" "), false, 0, 20));
        assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, List.of(""), false, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSeverityIsNull() {
        assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, Arrays.asList("INFO", null), false, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, null, false, -1, 20));

        assertEquals("Page must not be negative", e.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSizeIsBelowOne() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, null, false, 0, 0));

        assertEquals("Size must be at least 1", e.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSizeExceedsMaximum() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, null, false, 0, ListNotificationsUseCase.MAX_PAGE_SIZE + 1));

        assertEquals("Size must not exceed 100", e.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldCheckPagingBeforeSeverities() {
        InvalidNotificationQueryException e = assertThrows(InvalidNotificationQueryException.class,
                () -> service.list(null, List.of("URGENT"), false, -1, 20));

        assertEquals("Page must not be negative", e.getMessage());
    }
}
