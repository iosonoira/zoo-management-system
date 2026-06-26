package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Habitat;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterAnimalCommand(
    String name,
    String species,
    boolean dangerous,
    Habitat habitat,
    UUID enclosureId,
    LocalDate arrivalDate
) {}
