package it.zoo.animal.infrastructure.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferAnimalRequest(
        @NotNull UUID targetEnclosureId
) {}
