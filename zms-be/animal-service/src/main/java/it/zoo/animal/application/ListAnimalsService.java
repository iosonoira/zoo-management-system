package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalPage;
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
    public AnimalPage list(int page, int size) {
        if (page < 0) {
            throw new InvalidAnimalDataException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidAnimalDataException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidAnimalDataException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        List<Animal> items = repository.findPage(page, size);
        return new AnimalPage(items, page, size, repository.count());
    }
}
