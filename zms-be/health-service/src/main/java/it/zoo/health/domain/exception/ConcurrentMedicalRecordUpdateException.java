package it.zoo.health.domain.exception;

import java.util.UUID;

public class ConcurrentMedicalRecordUpdateException extends RuntimeException {
    public ConcurrentMedicalRecordUpdateException(UUID id) {
        super("Medical record was modified concurrently, retry the operation: " + id);
    }
}
