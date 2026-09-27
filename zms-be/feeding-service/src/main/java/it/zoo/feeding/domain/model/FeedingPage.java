package it.zoo.feeding.domain.model;

import java.util.List;

public record FeedingPage(List<Feeding> items, int page, int size, long total) {}
