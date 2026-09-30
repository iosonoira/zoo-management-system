package it.zoo.notification.domain.exception;

public class InvalidNotificationQueryException extends RuntimeException {
    public InvalidNotificationQueryException(String message) {
        super(message);
    }
}
