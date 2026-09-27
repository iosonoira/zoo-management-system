package it.zoo.feeding.infrastructure.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordFeedingRequest(
        Instant fedAt,
        @NotNull @PositiveOrZero Integer quantityGrams,
        @Size(max = 500) String notes
) {}
