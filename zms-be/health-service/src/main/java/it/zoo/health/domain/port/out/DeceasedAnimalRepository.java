package it.zoo.health.domain.port.out;

import it.zoo.health.domain.model.DeceasedAnimal;

import java.util.UUID;

public interface DeceasedAnimalRepository {
    boolean existsByAnimalId(UUID animalId);
    void save(DeceasedAnimal deceasedAnimal);
}
