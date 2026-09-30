package it.zoo.notification.infrastructure.rest.dto;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID animalId,
        AnimalEventType eventType,
        Severity severity,
        String message,
        Instant occurredAt,
        String performedBy,
        String name,
        String species,
        Boolean dangerous,
        String previousStatus,
        String newStatus,
        UUID fromEnclosureId,
        UUID toEnclosureId,
        String acknowledgedBy,
        Instant acknowledgedAt
) {}
