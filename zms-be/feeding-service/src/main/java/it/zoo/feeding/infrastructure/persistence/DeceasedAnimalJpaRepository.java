package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.model.DeceasedAnimal;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@ApplicationScoped
public class DeceasedAnimalJpaRepository implements DeceasedAnimalRepository {

    private final EntityManager em;

    public DeceasedAnimalJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean existsByAnimalId(UUID animalId) {
        return em.find(DeceasedAnimalEntity.class, animalId) != null;
    }

    @Override
    public void save(DeceasedAnimal deceasedAnimal) {
        em.persist(DeceasedAnimalEntityMapper.toEntity(deceasedAnimal));
    }
}
