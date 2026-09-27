package it.zoo.feeding.application;

import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.model.FeedingPlanPage;
import it.zoo.feeding.domain.port.in.ListFeedingPlansUseCase;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListFeedingPlansService implements ListFeedingPlansUseCase {

    private final FeedingPlanRepository repository;

    public ListFeedingPlansService(FeedingPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public FeedingPlanPage list(UUID animalId, int page, int size) {
        if (page < 0) {
            throw new InvalidFeedingDataException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidFeedingDataException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidFeedingDataException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        List<FeedingPlan> items = repository.findPage(animalId, page, size);
        return new FeedingPlanPage(items, page, size, repository.count(animalId));
    }
}
