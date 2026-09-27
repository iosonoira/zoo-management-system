package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.model.FeedingPlan;

import java.util.UUID;

public interface GetFeedingPlanUseCase {
    FeedingPlan getById(UUID id);
}
