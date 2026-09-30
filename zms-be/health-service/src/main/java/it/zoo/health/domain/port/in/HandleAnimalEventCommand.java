package it.zoo.health.domain.port.in;

import java.time.Instant;
import java.util.UUID;

public record HandleAnimalEventCommand(
    UUID eventId,
    String eventType,
    UUID animalId,
    Instant occurredAt,
    String performedBy,
    String newStatus
) {}
