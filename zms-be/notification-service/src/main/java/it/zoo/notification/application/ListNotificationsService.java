package it.zoo.notification.application;

import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.exception.InvalidNotificationQueryException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationPage;
import it.zoo.notification.domain.model.NotificationQuery;
import it.zoo.notification.domain.port.in.ListNotificationsUseCase;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ListNotificationsService implements ListNotificationsUseCase {

    private final NotificationRepository repository;

    public ListNotificationsService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationPage list(UUID animalId, List<String> severities, boolean openOnly, int page, int size) {
        if (page < 0) {
            throw new InvalidNotificationQueryException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidNotificationQueryException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidNotificationQueryException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        NotificationQuery query = new NotificationQuery(animalId, parseSeverities(severities), openOnly);
        List<Notification> items = repository.findPage(query, page, size);
        return new NotificationPage(items, page, size, repository.count(query));
    }

    private Set<Severity> parseSeverities(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<Severity> parsed = EnumSet.noneOf(Severity.class);
        for (String value : raw) {
            parsed.add(parseSeverity(value));
        }
        return parsed;
    }

    private Severity parseSeverity(String value) {
        if (value == null) {
            throw new InvalidNotificationQueryException("Unknown severity: null");
        }
        try {
            return Severity.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidNotificationQueryException("Unknown severity: " + value);
        }
    }
}
