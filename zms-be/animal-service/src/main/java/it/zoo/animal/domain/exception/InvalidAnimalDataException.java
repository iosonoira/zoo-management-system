package it.zoo.animal.domain.exception;

public class InvalidAnimalDataException extends RuntimeException {
    public InvalidAnimalDataException(String message) {
        super(message);
    }
}
