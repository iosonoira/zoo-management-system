package it.zoo.health.domain.exception;

import java.util.UUID;

public class ConcurrentTreatmentUpdateException extends RuntimeException {
    public ConcurrentTreatmentUpdateException(UUID id) {
        super("Treatment was modified concurrently, retry the operation: " + id);
    }
}
