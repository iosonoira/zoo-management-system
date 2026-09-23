package it.zoo.animal.domain.event;

import it.zoo.animal.domain.enums.Habitat;
import java.time.Instant;
import java.util.UUID;

public record AnimalRegistered(
        UUID eventId,
        Instant occurredAt,
        UUID animalId,
        String performedBy,
        String name,
        String species,
        boolean dangerous,
        Habitat habitat,
        UUID enclosureId
) implements AnimalEvent {
}
