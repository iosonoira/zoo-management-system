package it.zoo.notification.domain.port.out;

import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationQuery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    boolean existsByEventId(UUID eventId);
    List<Notification> findPage(NotificationQuery query, int page, int size);
    long count(NotificationQuery query);
    Optional<Notification> findById(UUID id);

    /** Sets the acknowledgement only if there is none yet; returns whether this call set it. */
    boolean acknowledge(UUID id, String actor, Instant at);
}
