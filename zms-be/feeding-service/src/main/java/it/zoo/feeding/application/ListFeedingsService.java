package it.zoo.feeding.application;

import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPage;
import it.zoo.feeding.domain.port.in.ListFeedingsUseCase;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListFeedingsService implements ListFeedingsUseCase {

    private final FeedingPlanRepository planRepository;
    private final FeedingRepository feedingRepository;

    public ListFeedingsService(FeedingPlanRepository planRepository, FeedingRepository feedingRepository) {
        this.planRepository = planRepository;
        this.feedingRepository = feedingRepository;
    }

    @Override
    public FeedingPage list(UUID planId, int page, int size) {
        if (page < 0) {
            throw new InvalidFeedingDataException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidFeedingDataException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidFeedingDataException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        if (!planRepository.existsById(planId)) {
            throw new FeedingPlanNotFoundException(planId);
        }

        List<Feeding> feedings = feedingRepository.findPageByPlanId(planId, page, size);
        return new FeedingPage(feedings, page, size, feedingRepository.countByPlanId(planId));
    }
}
