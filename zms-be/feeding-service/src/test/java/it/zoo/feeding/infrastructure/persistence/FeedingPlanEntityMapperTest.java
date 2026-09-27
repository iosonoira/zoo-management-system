package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.model.FeedingPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedingPlanEntityMapperTest {

    @Test
    void shouldRoundTripEveryField() {
        UUID id = UUID.randomUUID();
        UUID animalId = UUID.randomUUID();
        List<LocalTime> times = List.of(LocalTime.of(7, 30), LocalTime.of(18, 0));
        FeedingPlan original = new FeedingPlan(id, animalId, "Hay", 500, times, "Likes it fresh",
                PlanStatus.ACTIVE, LocalDate.of(2026, 9, 1));
        original.setEndedOn(LocalDate.of(2026, 9, 20));
        original.setCreatedBy("keeper");
        original.setUpdatedBy("admin");
        original.setVersion(4L);

        FeedingPlan result = FeedingPlanEntityMapper.toDomain(
                FeedingPlanEntityMapper.toEntity(original));

        assertEquals(id, result.getId());
        assertEquals(animalId, result.getAnimalId());
        assertEquals("Hay", result.getFood());
        assertEquals(500, result.getQuantityGrams());
        assertEquals(times, result.getFeedingTimes());
        assertEquals("Likes it fresh", result.getNotes());
        assertEquals(PlanStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.of(2026, 9, 1), result.getStartedOn());
        assertEquals(LocalDate.of(2026, 9, 20), result.getEndedOn());
        assertEquals("keeper", result.getCreatedBy());
        assertEquals("admin", result.getUpdatedBy());
        assertEquals(4L, result.getVersion());
    }
}
