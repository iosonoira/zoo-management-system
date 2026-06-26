package it.zoo.animal.domain.exception;

import it.zoo.animal.domain.model.AnimalStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(AnimalStatus from, AnimalStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
