package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.model.FeedingPlan;

import java.util.UUID;

public interface UpdateFeedingPlanStatusUseCase {
    FeedingPlan updateStatus(UUID id, PlanStatus newStatus, String performedBy);
}
