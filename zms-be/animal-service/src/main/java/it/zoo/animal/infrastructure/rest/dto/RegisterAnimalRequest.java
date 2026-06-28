package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.Habitat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterAnimalRequest(
        @NotBlank String name,
        @NotBlank String species,
        boolean dangerous,
        @NotNull Habitat habitat,
        @NotNull UUID enclosureId,
        @NotNull LocalDate arrivalDate
) {}
