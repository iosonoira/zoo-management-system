package it.zoo.animal.domain.port.out;

import it.zoo.animal.domain.model.Animal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalRepository {
    Animal save(Animal animal);
    Optional<Animal> findById(UUID id);
    List<Animal> findAll();
    boolean existsById(UUID id);
}
