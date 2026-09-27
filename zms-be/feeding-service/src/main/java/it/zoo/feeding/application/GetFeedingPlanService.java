package it.zoo.feeding.application;

import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.GetFeedingPlanUseCase;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class GetFeedingPlanService implements GetFeedingPlanUseCase {

    private final FeedingPlanRepository repository;

    public GetFeedingPlanService(FeedingPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public FeedingPlan getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new FeedingPlanNotFoundException(id));
    }
}
