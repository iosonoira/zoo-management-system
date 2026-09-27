package it.zoo.feeding.domain.model;

import java.util.List;

public record FeedingPlanPage(List<FeedingPlan> items, int page, int size, long total) {}
