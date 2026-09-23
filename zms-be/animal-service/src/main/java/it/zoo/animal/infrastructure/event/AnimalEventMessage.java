package it.zoo.animal.infrastructure.event;

import java.time.Instant;
import java.util.UUID;

public record AnimalEventMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID animalId,
        String performedBy,
        Object payload
) {

    public record AnimalRegisteredPayload(
            String name,
            String species,
            boolean dangerous,
            String habitat,
            UUID enclosureId
    ) {
    }

    public record AnimalStatusChangedPayload(
            String previousStatus,
            String newStatus,
            String name,
            String species
    ) {
    }

    public record AnimalTransferredPayload(
            UUID fromEnclosureId,
            UUID toEnclosureId,
            String name,
            String species,
            boolean dangerous
    ) {
    }
}
