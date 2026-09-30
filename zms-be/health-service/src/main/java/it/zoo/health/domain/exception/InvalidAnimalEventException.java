package it.zoo.health.domain.exception;

public class InvalidAnimalEventException extends RuntimeException {
    public InvalidAnimalEventException(String message) {
        super(message);
    }

    public InvalidAnimalEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
