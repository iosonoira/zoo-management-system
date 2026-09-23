package it.zoo.notification.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Contract test: the same fixture JSON also lives in animal-service's
 * {@code src/test/resources/contract/}, serialized there by the producer side and deserialized
 * here by the consumer side. Both sides must agree on the wire shape without sharing a module.
 */
class AnimalEventMessageContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule());

    @Test
    void shouldMapAnimalRegisteredFixtureToCommand() throws IOException {
        AnimalEventMessage message = readFixture("animal-registered.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals(AnimalEventType.ANIMAL_REGISTERED, command.eventType());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals("zoo-admin", command.performedBy());
        assertEquals("Leo", command.name());
        assertEquals("Lion", command.species());
        assertEquals(Boolean.TRUE, command.dangerous());
        assertNull(command.previousStatus());
        assertNull(command.newStatus());
        assertNull(command.fromEnclosureId());
        assertNull(command.toEnclosureId());
    }

    @Test
    void shouldMapAnimalStatusChangedFixtureToCommand() throws IOException {
        AnimalEventMessage message = readFixture("animal-status-changed.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals(AnimalEventType.ANIMAL_STATUS_CHANGED, command.eventType());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals("zoo-vet", command.performedBy());
        assertEquals("Leo", command.name());
        assertEquals("Lion", command.species());
        assertNull(command.dangerous());
        assertEquals("HEALTHY", command.previousStatus());
        assertEquals("UNDER_OBSERVATION", command.newStatus());
        assertNull(command.fromEnclosureId());
        assertNull(command.toEnclosureId());
    }

    @Test
    void shouldMapAnimalTransferredFixtureToCommand() throws IOException {
        AnimalEventMessage message = readFixture("animal-transferred.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals(AnimalEventType.ANIMAL_TRANSFERRED, command.eventType());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals("zoo-keeper", command.performedBy());
        assertEquals("Leo", command.name());
        assertEquals("Lion", command.species());
        assertEquals(Boolean.TRUE, command.dangerous());
        assertNull(command.previousStatus());
        assertNull(command.newStatus());
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), command.fromEnclosureId());
        assertEquals(UUID.fromString("44444444-4444-4444-4444-444444444444"), command.toEnclosureId());
    }

    private AnimalEventMessage readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contract/" + name)) {
            return objectMapper.readValue(in, AnimalEventMessage.class);
        }
    }
}
