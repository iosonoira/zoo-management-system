package it.zoo.animal.application;

import it.zoo.animal.domain.event.AnimalStatusChanged;
import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.port.in.UpdateAnimalStatusUseCase;
import it.zoo.animal.domain.port.out.AnimalEventPublisher;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class UpdateAnimalStatusService implements UpdateAnimalStatusUseCase {

    private final AnimalRepository repository;
    private final AnimalEventPublisher eventPublisher;

    public UpdateAnimalStatusService(AnimalRepository repository, AnimalEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Animal updateStatus(UUID id, AnimalStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }

        Animal animal = repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));

        if (!animal.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(animal.getStatus(), newStatus);
        }

        AnimalStatus previousStatus = animal.getStatus();
        animal.setStatus(newStatus);
        animal.setUpdatedBy(performedBy);
        Animal savedAnimal = repository.save(animal);

        AnimalStatusChanged event = new AnimalStatusChanged(
                UUID.randomUUID(),
                Instant.now(),
                savedAnimal.getId(),
                performedBy,
                savedAnimal.getName(),
                savedAnimal.getSpecies(),
                previousStatus,
                newStatus
        );
        eventPublisher.publish(event);

        return savedAnimal;
    }
}
