package it.zoo.feeding.domain.port.out;

import java.util.UUID;

public interface AnimalLock {
    /** Serializes, until the end of the current transaction, writes that depend on whether an animal is deceased. */
    void acquire(UUID animalId);
}
