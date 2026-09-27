package it.zoo.feeding.domain.port.in;

import java.time.Instant;
import java.util.UUID;

public record RecordFeedingCommand(
    UUID planId,
    Instant fedAt,
    Integer quantityGrams,
    String notes,
    String performedBy
) {}
