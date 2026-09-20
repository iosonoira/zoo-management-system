package it.zoo.animal.domain.port.out;

import it.zoo.animal.domain.model.Animal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalRepository {
    Animal save(Animal animal);
    Optional<Animal> findById(UUID id);
    List<Animal> findPage(int page, int size);
    long count();
    boolean existsById(UUID id);
}
