package it.zoo.health.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import it.zoo.health.domain.port.in.HandleAnimalEventCommand;
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
    void shouldMapAnimalRegisteredFixtureToCommandWithNullNewStatus() throws IOException {
        AnimalEventMessage message = readFixture("animal-registered.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals("ANIMAL_REGISTERED", command.eventType());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals("zoo-admin", command.performedBy());
        assertNull(command.newStatus());
    }

    @Test
    void shouldMapAnimalStatusChangedFixtureToCommandWithNewStatus() throws IOException {
        AnimalEventMessage message = readFixture("animal-status-changed.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals("ANIMAL_STATUS_CHANGED", command.eventType());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals("zoo-vet", command.performedBy());
        assertEquals("UNDER_OBSERVATION", command.newStatus());
    }

    @Test
    void shouldMapAnimalTransferredFixtureToCommandWithNullNewStatus() throws IOException {
        AnimalEventMessage message = readFixture("animal-transferred.json");
        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), command.eventId());
        assertEquals("ANIMAL_TRANSFERRED", command.eventType());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), command.animalId());
        assertEquals(Instant.parse("2026-09-23T10:15:30Z"), command.occurredAt());
        assertEquals("zoo-keeper", command.performedBy());
        assertNull(command.newStatus());
    }

    private AnimalEventMessage readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/contract/" + name)) {
            return objectMapper.readValue(in, AnimalEventMessage.class);
        }
    }
}
