package it.zoo.notification.domain.port.in;

import it.zoo.notification.domain.model.Notification;

import java.util.UUID;

public interface AcknowledgeNotificationUseCase {

    /**
     * Marks the notification as taken in charge by {@code actor}. The first acknowledgement wins:
     * acknowledging a notification that is already acknowledged changes nothing and returns it
     * with its existing {@code acknowledgedBy} and {@code acknowledgedAt}.
     */
    Notification acknowledge(UUID id, String actor);
}
