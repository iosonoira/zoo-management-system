package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.port.in.TransferAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class TransferAnimalService implements TransferAnimalUseCase {

    private final AnimalRepository repository;

    public TransferAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal transfer(UUID animalId, UUID targetEnclosureId) {
        if (targetEnclosureId == null) {
            throw new InvalidAnimalDataException("Target enclosure ID must not be null");
        }

        Animal animal = repository.findById(animalId)
                .orElseThrow(() -> new AnimalNotFoundException(animalId));

        if (animal.getStatus() == AnimalStatus.DECEASED) {
            throw new InvalidAnimalDataException("Cannot transfer a deceased animal");
        }

        animal.setEnclosureId(targetEnclosureId);
        return repository.save(animal);
    }
}
