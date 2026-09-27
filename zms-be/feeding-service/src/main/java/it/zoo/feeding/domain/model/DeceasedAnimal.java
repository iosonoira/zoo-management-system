package it.zoo.feeding.domain.model;

import java.time.Instant;
import java.util.UUID;

public record DeceasedAnimal(UUID animalId, UUID eventId, Instant occurredAt) {}
