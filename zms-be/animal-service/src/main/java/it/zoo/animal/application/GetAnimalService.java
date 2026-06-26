package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.GetAnimalUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class GetAnimalService implements GetAnimalUseCase {

    private final AnimalRepository repository;

    public GetAnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    public Animal getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AnimalNotFoundException(id));
    }
}
