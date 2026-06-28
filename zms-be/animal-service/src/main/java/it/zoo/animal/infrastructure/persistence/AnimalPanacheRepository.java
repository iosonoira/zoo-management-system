package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AnimalPanacheRepository implements AnimalRepository {

    @Inject
    EntityManager em;

    @Override
    public Animal save(Animal animal) {
        AnimalEntity entity = AnimalEntityMapper.toEntity(animal);
        entity = em.merge(entity);
        return AnimalEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Animal> findById(UUID id) {
        return Optional.ofNullable(em.find(AnimalEntity.class, id))
                .map(AnimalEntityMapper::toDomain);
    }

    @Override
    public List<Animal> findAll() {
        List<AnimalEntity> entities = em.createQuery("SELECT a FROM AnimalEntity a", AnimalEntity.class)
                .getResultList();
        return AnimalEntityMapper.toDomainList(entities);
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(AnimalEntity.class, id) != null;
    }
}
