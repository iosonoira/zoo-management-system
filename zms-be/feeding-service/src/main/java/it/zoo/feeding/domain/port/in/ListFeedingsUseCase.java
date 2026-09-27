package it.zoo.feeding.domain.port.in;

import it.zoo.feeding.domain.model.FeedingPage;

import java.util.UUID;

public interface ListFeedingsUseCase {

    int MAX_PAGE_SIZE = 100;

    FeedingPage list(UUID planId, int page, int size);
}
