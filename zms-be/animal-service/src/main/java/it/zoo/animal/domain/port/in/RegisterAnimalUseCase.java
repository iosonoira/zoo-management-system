package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Animal;

public interface RegisterAnimalUseCase {
    Animal register(RegisterAnimalCommand command);
}
