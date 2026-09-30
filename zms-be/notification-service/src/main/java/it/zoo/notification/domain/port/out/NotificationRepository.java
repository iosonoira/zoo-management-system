package it.zoo.notification.domain.port.out;

import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    boolean existsByEventId(UUID eventId);
    List<Notification> findPage(NotificationQuery query, int page, int size);
    long count(NotificationQuery query);
    Optional<Notification> findById(UUID id);
}
