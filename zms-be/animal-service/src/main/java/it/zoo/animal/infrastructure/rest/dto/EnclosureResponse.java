package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.Habitat;

import java.util.UUID;

public record EnclosureResponse(
        UUID id,
        String name,
        Habitat habitat
) {}
