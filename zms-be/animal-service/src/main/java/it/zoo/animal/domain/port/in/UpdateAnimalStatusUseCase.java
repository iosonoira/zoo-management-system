package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import java.util.UUID;

public interface UpdateAnimalStatusUseCase {
    Animal updateStatus(UUID id, AnimalStatus newStatus);
}
