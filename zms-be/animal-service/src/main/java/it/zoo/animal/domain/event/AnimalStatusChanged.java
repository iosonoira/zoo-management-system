package it.zoo.animal.domain.event;

import it.zoo.animal.domain.enums.AnimalStatus;
import java.time.Instant;
import java.util.UUID;

public record AnimalStatusChanged(
        UUID eventId,
        Instant occurredAt,
        UUID animalId,
        String performedBy,
        String name,
        String species,
        AnimalStatus previousStatus,
        AnimalStatus newStatus
) implements AnimalEvent {
}
