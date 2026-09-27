package it.zoo.feeding.domain.exception;

import java.util.UUID;

public class FeedingPlanNotFoundException extends RuntimeException {
    public FeedingPlanNotFoundException(UUID id) {
        super("Feeding plan not found with id: " + id);
    }
}
