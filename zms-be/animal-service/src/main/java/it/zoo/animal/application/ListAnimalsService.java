package it.zoo.animal.application;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.in.ListAnimalsUseCase;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ListAnimalsService implements ListAnimalsUseCase {

    private final AnimalRepository repository;

    public ListAnimalsService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Animal> listAll() {
        return repository.findAll();
    }
}
