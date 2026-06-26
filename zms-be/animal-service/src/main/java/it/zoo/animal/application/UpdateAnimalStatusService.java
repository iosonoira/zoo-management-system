package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.port.in.UpdateAnimalStatusUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class UpdateAnimalStatusService implements UpdateAnimalStatusUseCase {

    private final AnimalRepository repository;

    public UpdateAnimalStatusService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal updateStatus(UUID id, AnimalStatus newStatus) {
        Animal animal = repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));

        if (!animal.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(animal.getStatus(), newStatus);
        }

        animal.setStatus(newStatus);
        return repository.save(animal);
    }
}
