package it.zoo.animal.application;

import it.zoo.animal.domain.event.AnimalTransferred;
import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.UnknownEnclosureException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.TransferAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalEventPublisher;
import it.zoo.animal.domain.port.out.AnimalRepository;
import it.zoo.animal.domain.port.out.EnclosureRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class TransferAnimalService implements TransferAnimalUseCase {

    private final AnimalRepository repository;
    private final AnimalEventPublisher eventPublisher;
    private final EnclosureRepository enclosureRepository;

    public TransferAnimalService(AnimalRepository repository, AnimalEventPublisher eventPublisher,
                                  EnclosureRepository enclosureRepository) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.enclosureRepository = enclosureRepository;
    }

    @Override
    @Transactional
    public Animal transfer(UUID animalId, UUID targetEnclosureId, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }
        if (targetEnclosureId == null) {
            throw new InvalidAnimalDataException("Target enclosure ID must not be null");
        }

        Animal animal = repository.findById(animalId)
                .orElseThrow(() -> new AnimalNotFoundException(animalId));

        if (!animal.canBeTransferred()) {
            throw new InvalidAnimalDataException("Cannot transfer a deceased animal");
        }
        if (!enclosureRepository.existsById(targetEnclosureId)) {
            throw new UnknownEnclosureException(targetEnclosureId);
        }

        UUID fromEnclosureId = animal.getEnclosureId();
        animal.setEnclosureId(targetEnclosureId);
        animal.setUpdatedBy(performedBy);
        Animal savedAnimal = repository.save(animal);

        AnimalTransferred event = new AnimalTransferred(
                UUID.randomUUID(),
                Instant.now(),
                savedAnimal.getId(),
                performedBy,
                savedAnimal.getName(),
                savedAnimal.getSpecies(),
                savedAnimal.isDangerous(),
                fromEnclosureId,
                targetEnclosureId
        );
        eventPublisher.publish(event);

        return savedAnimal;
    }
}
