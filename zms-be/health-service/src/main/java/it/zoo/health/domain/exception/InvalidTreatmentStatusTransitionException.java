package it.zoo.health.domain.exception;

import it.zoo.health.domain.enums.TreatmentStatus;

public class InvalidTreatmentStatusTransitionException extends RuntimeException {
    public InvalidTreatmentStatusTransitionException(TreatmentStatus from, TreatmentStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
