package it.zoo.animal.infrastructure.rest.dto;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;

import java.time.LocalDate;
import java.util.UUID;

public record AnimalResponse(
        UUID id,
        String name,
        String species,
        boolean dangerous,
        Habitat habitat,
        UUID enclosureId,
        LocalDate arrivalDate,
        AnimalStatus status
) {}
