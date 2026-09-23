package it.zoo.notification.infrastructure.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire envelope for {@code zoo.animal.events}, owned by this service (no shared module with
 * animal-service). {@code payload} is kept as a raw {@link JsonNode} because its shape differs
 * per {@code eventType}; {@link AnimalEventMessageMapper} picks the fields it needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnimalEventMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID animalId,
        String performedBy,
        JsonNode payload
) {
}
