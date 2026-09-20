package it.zoo.animal.domain.model;

import java.util.List;

public record AnimalPage(List<Animal> items, int page, int size, long total) {}
