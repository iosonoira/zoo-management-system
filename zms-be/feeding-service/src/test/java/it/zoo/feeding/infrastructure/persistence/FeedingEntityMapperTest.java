package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.model.Feeding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedingEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant fedAt = Instant.parse("2026-09-01T07:30:00Z");
        Feeding original = new Feeding(id, planId, fedAt, 250, "Ate everything", "keeper");

        Feeding result = FeedingEntityMapper.toDomain(FeedingEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(planId, result.getPlanId());
        assertEquals(fedAt, result.getFedAt());
        assertEquals(250, result.getQuantityGrams());
        assertEquals("Ate everything", result.getNotes());
        assertEquals("keeper", result.getRecordedBy());
    }
}
