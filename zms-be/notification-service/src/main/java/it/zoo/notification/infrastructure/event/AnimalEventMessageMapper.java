package it.zoo.notification.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.exception.InvalidAnimalEventException;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;

import java.util.UUID;

public final class AnimalEventMessageMapper {

    private AnimalEventMessageMapper() {}

    public static HandleAnimalEventCommand toCommand(AnimalEventMessage message) {
        AnimalEventType eventType = parseEventType(message.eventType());
        JsonNode payload = message.payload();

        return switch (eventType) {
            case ANIMAL_REGISTERED -> new HandleAnimalEventCommand(
                    message.eventId(),
                    eventType,
                    message.occurredAt(),
                    message.animalId(),
                    message.performedBy(),
                    text(payload, "name"),
                    text(payload, "species"),
                    bool(payload, "dangerous"),
                    null,
                    null,
                    null,
                    null
            );
            case ANIMAL_STATUS_CHANGED -> new HandleAnimalEventCommand(
                    message.eventId(),
                    eventType,
                    message.occurredAt(),
                    message.animalId(),
                    message.performedBy(),
                    text(payload, "name"),
                    text(payload, "species"),
                    null,
                    text(payload, "previousStatus"),
                    text(payload, "newStatus"),
                    null,
                    null
            );
            case ANIMAL_TRANSFERRED -> new HandleAnimalEventCommand(
                    message.eventId(),
                    eventType,
                    message.occurredAt(),
                    message.animalId(),
                    message.performedBy(),
                    text(payload, "name"),
                    text(payload, "species"),
                    bool(payload, "dangerous"),
                    null,
                    null,
                    uuid(payload, "fromEnclosureId"),
                    uuid(payload, "toEnclosureId")
            );
        };
    }

    private static AnimalEventType parseEventType(String raw) {
        if (raw == null) {
            throw new InvalidAnimalEventException("Event type must not be null");
        }
        try {
            return AnimalEventType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidAnimalEventException("Unknown animal event type: " + raw, e);
        }
    }

    private static String text(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return payload.get(field).asText();
    }

    private static Boolean bool(JsonNode payload, String field) {
        if (payload == null || !payload.hasNonNull(field)) {
            return null;
        }
        return payload.get(field).asBoolean();
    }

    private static UUID uuid(JsonNode payload, String field) {
        String value = text(payload, field);
        return value == null ? null : UUID.fromString(value);
    }
}
