package it.zoo.animal.domain.port.in;

import it.zoo.animal.domain.model.Animal;
import java.util.UUID;

public interface TransferAnimalUseCase {
    Animal transfer(UUID animalId, UUID targetEnclosureId);
}
