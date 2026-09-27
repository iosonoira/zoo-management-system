package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.model.FeedingPlanPage;

import java.util.UUID;

public interface ListFeedingPlansUseCase {

    int MAX_PAGE_SIZE = 100;

    FeedingPlanPage list(UUID animalId, int page, int size);
}
