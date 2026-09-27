package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.model.FeedingPlan;

public interface CreateFeedingPlanUseCase {
    FeedingPlan create(CreateFeedingPlanCommand cmd);
}
