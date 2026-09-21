package it.zoo.health.domain.exception;

public class InvalidMedicalDataException extends RuntimeException {
    public InvalidMedicalDataException(String message) {
        super(message);
    }
}
