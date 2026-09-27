package it.zoo.feeding.domain.port.in;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateFeedingPlanCommand(
    UUID animalId,
    String food,
    Integer quantityGrams,
    List<LocalTime> feedingTimes,
    String notes,
    String performedBy
) {}
