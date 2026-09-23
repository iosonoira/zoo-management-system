package it.zoo.animal.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface AnimalEvent permits AnimalRegistered, AnimalStatusChanged, AnimalTransferred {
    UUID eventId();
    Instant occurredAt();
    UUID animalId();
    String performedBy();
}
