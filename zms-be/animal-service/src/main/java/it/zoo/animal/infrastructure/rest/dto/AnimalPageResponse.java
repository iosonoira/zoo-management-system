package it.zoo.animal.infrastructure.rest.dto;

import java.util.List;

public record AnimalPageResponse(
        List<AnimalResponse> items,
        int page,
        int size,
        long total
) {}
