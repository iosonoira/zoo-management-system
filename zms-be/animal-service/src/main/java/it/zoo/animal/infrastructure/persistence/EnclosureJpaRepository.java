package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.model.Enclosure;
import it.zoo.animal.domain.port.out.EnclosureRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EnclosureJpaRepository implements EnclosureRepository {

    private final EntityManager em;

    public EnclosureJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Enclosure> findAll() {
        List<EnclosureEntity> entities = em
                .createQuery("select e from EnclosureEntity e order by e.name", EnclosureEntity.class)
                .getResultList();
        return EnclosureEntityMapper.toDomainList(entities);
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(EnclosureEntity.class, id) != null;
    }
}
