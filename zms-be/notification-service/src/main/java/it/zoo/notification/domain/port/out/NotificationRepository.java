package it.zoo.notification.domain.port.out;

import it.zoo.notification.domain.model.Notification;

import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    boolean existsByEventId(UUID eventId);
}
