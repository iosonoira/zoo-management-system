package it.zoo.animal.domain.exception;

import java.util.UUID;

public class UnknownEnclosureException extends RuntimeException {
    public UnknownEnclosureException(UUID id) {
        super("Enclosure not found with id: " + id);
    }
}
