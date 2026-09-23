package it.zoo.animal.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AnimalTransferred(
        UUID eventId,
        Instant occurredAt,
        UUID animalId,
        String performedBy,
        String name,
        String species,
        boolean dangerous,
        UUID fromEnclosureId,
        UUID toEnclosureId
) implements AnimalEvent {
}
