package it.zoo.feeding.infrastructure.rest.dto;

import java.util.List;

public record FeedingPlanPageResponse(
        List<FeedingPlanResponse> items,
        int page,
        int size,
        long total
) {}
