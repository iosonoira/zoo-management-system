package it.zoo.notification.infrastructure.persistence;

import it.zoo.notification.domain.model.Notification;

public final class NotificationEntityMapper {

    private NotificationEntityMapper() {}

    public static Notification toDomain(NotificationEntity entity) {
        return new Notification(
                entity.getId(),
                entity.getEventId(),
                entity.getAnimalId(),
                entity.getEventType(),
                entity.getSeverity(),
                entity.getMessage(),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }

    public static NotificationEntity toEntity(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notification.getId());
        entity.setEventId(notification.getEventId());
        entity.setAnimalId(notification.getAnimalId());
        entity.setEventType(notification.getEventType());
        entity.setSeverity(notification.getSeverity());
        entity.setMessage(notification.getMessage());
        entity.setOccurredAt(notification.getOccurredAt());
        entity.setCreatedAt(notification.getCreatedAt());
        return entity;
    }
}
