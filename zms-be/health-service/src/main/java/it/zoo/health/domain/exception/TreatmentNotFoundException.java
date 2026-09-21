package it.zoo.health.domain.exception;

import java.util.UUID;

public class TreatmentNotFoundException extends RuntimeException {
    public TreatmentNotFoundException(UUID id) {
        super("Treatment not found with id: " + id);
    }
}
