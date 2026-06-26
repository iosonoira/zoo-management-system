package it.zoo.animal.domain.exception;

import java.util.UUID;

public class AnimalNotFoundException extends RuntimeException {
    public AnimalNotFoundException(UUID id) {
        super("Animal not found with id: " + id);
    }
}
