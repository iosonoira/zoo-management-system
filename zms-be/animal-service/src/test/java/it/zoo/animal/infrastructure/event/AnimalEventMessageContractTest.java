package it.zoo.animal.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.event.AnimalRegistered;
import it.zoo.animal.domain.event.AnimalStatusChanged;
import it.zoo.animal.domain.event.AnimalTransferred;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimalEventMessageContractTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ANIMAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENCLOSURE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TARGET_ENCLOSURE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-23T10:15:30Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldMatchAnimalRegisteredFixture() throws IOException {
        AnimalRegistered event = new AnimalRegistered(
                EVENT_ID,
                OCCURRED_AT,
                ANIMAL_ID,
                "zoo-admin",
                "Leo",
                "Lion",
                true,
                Habitat.TERRESTRIAL,
                ENCLOSURE_ID
        );

        assertMatchesFixture(event, "animal-registered.json");
    }

    @Test
    void shouldMatchAnimalStatusChangedFixture() throws IOException {
        AnimalStatusChanged event = new AnimalStatusChanged(
                EVENT_ID,
                OCCURRED_AT,
                ANIMAL_ID,
                "zoo-vet",
                "Leo",
                "Lion",
                AnimalStatus.HEALTHY,
                AnimalStatus.UNDER_OBSERVATION
        );

        assertMatchesFixture(event, "animal-status-changed.json");
    }

    @Test
    void shouldMatchAnimalTransferredFixture() throws IOException {
        AnimalTransferred event = new AnimalTransferred(
                EVENT_ID,
                OCCURRED_AT,
                ANIMAL_ID,
                "zoo-keeper",
                "Leo",
                "Lion",
                true,
                ENCLOSURE_ID,
                TARGET_ENCLOSURE_ID
        );

        assertMatchesFixture(event, "animal-transferred.json");
    }

    private void assertMatchesFixture(it.zoo.animal.domain.event.AnimalEvent event, String fixtureName) throws IOException {
        AnimalEventMessage message = AnimalEventMessageMapper.toMessage(event);
        JsonNode actual = objectMapper.valueToTree(message);
        JsonNode expected = objectMapper.readTree(
                getClass().getClassLoader().getResourceAsStream("contract/" + fixtureName));

        assertEquals(expected, actual);
    }
}
