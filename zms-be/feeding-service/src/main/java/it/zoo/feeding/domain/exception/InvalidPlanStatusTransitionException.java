package it.zoo.feeding.domain.exception;

import it.zoo.feeding.domain.enums.PlanStatus;

public class InvalidPlanStatusTransitionException extends RuntimeException {
    public InvalidPlanStatusTransitionException(PlanStatus from, PlanStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
