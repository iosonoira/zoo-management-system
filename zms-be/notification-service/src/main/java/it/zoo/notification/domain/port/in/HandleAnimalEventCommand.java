package it.zoo.notification.domain.port.in;

import it.zoo.notification.domain.enums.AnimalEventType;

import java.time.Instant;
import java.util.UUID;

public record HandleAnimalEventCommand(
    UUID eventId,
    AnimalEventType eventType,
    Instant occurredAt,
    UUID animalId,
    String performedBy,
    String name,
    String species,
    Boolean dangerous,
    String previousStatus,
    String newStatus,
    UUID fromEnclosureId,
    UUID toEnclosureId
) {}
