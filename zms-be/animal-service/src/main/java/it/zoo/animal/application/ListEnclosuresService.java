package it.zoo.animal.application;

import it.zoo.animal.domain.model.Enclosure;
import it.zoo.animal.domain.port.in.ListEnclosuresUseCase;
import it.zoo.animal.domain.port.out.EnclosureRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ListEnclosuresService implements ListEnclosuresUseCase {

    private final EnclosureRepository repository;

    public ListEnclosuresService(EnclosureRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Enclosure> listAll() {
        return repository.findAll();
    }
}
