package it.zoo.feeding.domain.exception;

import it.zoo.feeding.domain.enums.PlanStatus;

import java.util.UUID;

public class FeedingPlanNotActiveException extends RuntimeException {
    public FeedingPlanNotActiveException(UUID planId, PlanStatus status) {
        super("Feeding plan " + planId + " is " + status + ", feedings can only be recorded on an ACTIVE plan");
    }
}
