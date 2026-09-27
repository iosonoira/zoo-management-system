package it.zoo.feeding.domain.exception;

public class InvalidAnimalEventException extends RuntimeException {
    public InvalidAnimalEventException(String message) {
        super(message);
    }

    public InvalidAnimalEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
