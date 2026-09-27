package it.zoo.feeding.domain.exception;

public class InvalidFeedingDataException extends RuntimeException {
    public InvalidFeedingDataException(String message) {
        super(message);
    }
}
