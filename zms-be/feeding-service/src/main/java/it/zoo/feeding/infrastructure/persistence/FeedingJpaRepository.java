package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FeedingJpaRepository implements FeedingRepository {

    private final EntityManager em;

    public FeedingJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Feeding save(Feeding feeding) {
        FeedingEntity entity = FeedingEntityMapper.toEntity(feeding);
        em.persist(entity);
        em.flush();
        return FeedingEntityMapper.toDomain(entity);
    }

    @Override
    public List<Feeding> findPageByPlanId(UUID planId, int page, int size) {
        // setFirstResult takes an int: an offset past it cannot hold rows, and page * size would overflow.
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            return List.of();
        }
        return FeedingEntityMapper.toDomainList(em
                .createQuery(
                        "SELECT f FROM FeedingEntity f WHERE f.planId = :planId ORDER BY f.fedAt DESC, f.id",
                        FeedingEntity.class)
                .setParameter("planId", planId)
                .setFirstResult((int) offset)
                .setMaxResults(size)
                .getResultList());
    }

    @Override
    public long countByPlanId(UUID planId) {
        return em.createQuery(
                        "SELECT COUNT(f) FROM FeedingEntity f WHERE f.planId = :planId", Long.class)
                .setParameter("planId", planId)
                .getSingleResult();
    }
}
