package it.zoo.feeding.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import it.zoo.feeding.domain.enums.PlanStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record FeedingPlanResponse(
        UUID id,
        UUID animalId,
        String food,
        int quantityGrams,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") List<LocalTime> feedingTimes,
        String notes,
        PlanStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        String createdBy,
        String updatedBy
) {}
