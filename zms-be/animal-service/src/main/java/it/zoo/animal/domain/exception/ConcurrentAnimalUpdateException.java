package it.zoo.animal.domain.exception;

import java.util.UUID;

public class ConcurrentAnimalUpdateException extends RuntimeException {
    public ConcurrentAnimalUpdateException(UUID id) {
        super("Animal was modified concurrently, retry the operation: " + id);
    }
}
