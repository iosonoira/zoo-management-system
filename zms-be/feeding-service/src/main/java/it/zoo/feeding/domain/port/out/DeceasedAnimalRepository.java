package it.zoo.feeding.domain.port.out;

import it.zoo.feeding.domain.model.DeceasedAnimal;

import java.util.UUID;

public interface DeceasedAnimalRepository {
    boolean existsByAnimalId(UUID animalId);
    void save(DeceasedAnimal deceasedAnimal);
}
