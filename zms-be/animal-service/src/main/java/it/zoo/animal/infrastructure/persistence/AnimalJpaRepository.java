package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.exception.ConcurrentAnimalUpdateException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.port.out.AnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AnimalJpaRepository implements AnimalRepository {

    private final EntityManager em;

    public AnimalJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Animal save(Animal animal) {
        AnimalEntity entity = AnimalEntityMapper.toEntity(animal);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentAnimalUpdateException(animal.getId());
        }
        return AnimalEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Animal> findById(UUID id) {
        return Optional.ofNullable(em.find(AnimalEntity.class, id))
                .map(AnimalEntityMapper::toDomain);
    }

    @Override
    public List<Animal> findPage(int page, int size) {
        // setFirstResult takes an int: an offset past it cannot hold rows, and page * size would overflow.
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            return List.of();
        }
        List<AnimalEntity> entities = em
                .createQuery("SELECT a FROM AnimalEntity a ORDER BY a.name, a.id", AnimalEntity.class)
                .setFirstResult((int) offset)
                .setMaxResults(size)
                .getResultList();
        return AnimalEntityMapper.toDomainList(entities);
    }

    @Override
    public long count() {
        return em.createQuery("SELECT COUNT(a) FROM AnimalEntity a", Long.class).getSingleResult();
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(AnimalEntity.class, id) != null;
    }
}
