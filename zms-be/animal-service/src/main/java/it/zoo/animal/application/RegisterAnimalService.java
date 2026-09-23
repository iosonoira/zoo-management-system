package it.zoo.animal.application;

import it.zoo.animal.domain.event.AnimalRegistered;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.UnknownEnclosureException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.in.RegisterAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalEventPublisher;
import it.zoo.animal.domain.port.out.AnimalRepository;
import it.zoo.animal.domain.port.out.EnclosureRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class RegisterAnimalService implements RegisterAnimalUseCase {

    private final AnimalRepository repository;
    private final AnimalEventPublisher eventPublisher;
    private final EnclosureRepository enclosureRepository;

    public RegisterAnimalService(AnimalRepository repository, AnimalEventPublisher eventPublisher,
                                  EnclosureRepository enclosureRepository) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.enclosureRepository = enclosureRepository;
    }

    @Override
    @Transactional
    public Animal register(RegisterAnimalCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidAnimalDataException("Actor must not be blank");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new InvalidAnimalDataException("Animal name must not be blank");
        }
        if (cmd.species() == null || cmd.species().isBlank()) {
            throw new InvalidAnimalDataException("Animal species must not be blank");
        }
        if (cmd.habitat() == null) {
            throw new InvalidAnimalDataException("Animal habitat must not be null");
        }
        if (cmd.enclosureId() == null) {
            throw new InvalidAnimalDataException("Enclosure ID must not be null");
        }
        if (cmd.arrivalDate() == null) {
            throw new InvalidAnimalDataException("Arrival date must not be null");
        }
        if (!enclosureRepository.existsById(cmd.enclosureId())) {
            throw new UnknownEnclosureException(cmd.enclosureId());
        }

        Animal animal = new Animal(
                UUID.randomUUID(),
                cmd.name(),
                cmd.species(),
                cmd.dangerous(),
                cmd.habitat(),
                cmd.enclosureId(),
                cmd.arrivalDate(),
                AnimalStatus.HEALTHY
        );
        animal.setCreatedBy(cmd.performedBy());
        animal.setUpdatedBy(cmd.performedBy());
        Animal savedAnimal = repository.save(animal);

        AnimalRegistered event = new AnimalRegistered(
                UUID.randomUUID(),
                Instant.now(),
                savedAnimal.getId(),
                cmd.performedBy(),
                savedAnimal.getName(),
                savedAnimal.getSpecies(),
                savedAnimal.isDangerous(),
                savedAnimal.getHabitat(),
                savedAnimal.getEnclosureId()
        );
        eventPublisher.publish(event);

        return savedAnimal;
    }
}
