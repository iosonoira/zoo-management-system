package it.zoo.animal.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AnimalPanacheRepository implements PanacheRepositoryBase<AnimalEntity, UUID>, AnimalRepository {

    @Override
    public Animal save(Animal animal) {
        AnimalEntity entity = AnimalEntityMapper.toEntity(animal);
        entity = getEntityManager().merge(entity);
        return AnimalEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Animal> findById(UUID id) {
        return findByIdOptional(id).map(AnimalEntityMapper::toDomain);
    }

    @Override
    public List<Animal> findAll() {
        return AnimalEntityMapper.toDomainList(listAll());
    }

    @Override
    public boolean existsById(UUID id) {
        return findByIdOptional(id).isPresent();
    }
}
