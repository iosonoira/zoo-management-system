package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Animal;
import java.util.UUID;

public interface GetAnimalUseCase {
    Animal getById(UUID id);
}