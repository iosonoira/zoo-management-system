package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.AnimalStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAnimalStatusRequest(
        @NotNull AnimalStatus status
) {}
