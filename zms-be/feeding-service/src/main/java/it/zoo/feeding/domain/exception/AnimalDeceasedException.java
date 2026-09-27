package it.zoo.feeding.domain.exception;

import java.util.UUID;

public class AnimalDeceasedException extends RuntimeException {
    public AnimalDeceasedException(UUID animalId) {
        super("Animal " + animalId + " is deceased");
    }
}
