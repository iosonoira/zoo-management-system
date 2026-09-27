package it.zoo.feeding.infrastructure.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record FeedingResponse(
        UUID id,
        UUID planId,
        Instant fedAt,
        int quantityGrams,
        String notes,
        String recordedBy
) {}
