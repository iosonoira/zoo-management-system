package it.zoo.feeding.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CreateFeedingPlanRequest(
        @NotNull UUID animalId,
        @NotBlank @Size(max = 100) String food,
        @NotNull @Positive Integer quantityGrams,
        @NotEmpty @Size(max = 6) List<@NotNull LocalTime> feedingTimes,
        @Size(max = 500) String notes
) {}
