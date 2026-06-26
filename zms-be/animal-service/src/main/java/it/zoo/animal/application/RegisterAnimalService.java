package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.in.RegisterAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class RegisterAnimalService implements RegisterAnimalUseCase {

    private final AnimalRepository repository;

    public RegisterAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Animal register(RegisterAnimalCommand cmd) {
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
        return repository.save(animal);
    }
}
