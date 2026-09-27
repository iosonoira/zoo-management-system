package it.zoo.feeding.domain.exception;

import java.util.UUID;

public class ConcurrentFeedingPlanUpdateException extends RuntimeException {
    public ConcurrentFeedingPlanUpdateException(UUID id) {
        super("Feeding plan was modified concurrently, retry the operation: " + id);
    }
}
