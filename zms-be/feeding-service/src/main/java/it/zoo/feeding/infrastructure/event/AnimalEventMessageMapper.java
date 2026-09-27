package it.zoo.feeding.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import it.zoo.feeding.domain.port.in.HandleAnimalEventCommand;

public final class AnimalEventMessageMapper {

    private static final String STATUS_CHANGED_EVENT_TYPE = "ANIMAL_STATUS_CHANGED";

    private AnimalEventMessageMapper() {}

    public static HandleAnimalEventCommand toCommand(AnimalEventMessage message) {
        String eventType = message.eventType();
        String newStatus = STATUS_CHANGED_EVENT_TYPE.equals(eventType)
                ? text(message.payload(), "newStatus")
                : null;

        return new HandleAnimalEventCommand(
                message.eventId(),
                eventType,
                message.animalId(),
                message.occurredAt(),
                message.performedBy(),
                newStatus
        );
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return payload.get(field).asText();
    }
}
