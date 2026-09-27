package it.zoo.feeding.infrastructure.rest.dto;

import java.util.List;

public record FeedingPageResponse(
        List<FeedingResponse> items,
        int page,
        int size,
        long total
) {}
