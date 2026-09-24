package it.zoo.animal.domain.model;

import it.zoo.animal.domain.enums.Habitat;
import java.util.UUID;

public record Enclosure(UUID id, String name, Habitat habitat) {}
