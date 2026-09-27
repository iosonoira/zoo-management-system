package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.ConcurrentFeedingPlanUpdateException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FeedingPlanJpaRepository implements FeedingPlanRepository {

    private final EntityManager em;

    public FeedingPlanJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public FeedingPlan save(FeedingPlan plan) {
        FeedingPlanEntity entity = FeedingPlanEntityMapper.toEntity(plan);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentFeedingPlanUpdateException(plan.getId());
        }
        return FeedingPlanEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<FeedingPlan> findById(UUID id) {
        return Optional.ofNullable(em.find(FeedingPlanEntity.class, id))
                .map(FeedingPlanEntityMapper::toDomain);
    }

    @Override
    public Optional<FeedingPlan> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(em.find(FeedingPlanEntity.class, id, LockModeType.PESSIMISTIC_WRITE))
                .map(FeedingPlanEntityMapper::toDomain);
    }

    @Override
    public List<FeedingPlan> findPage(UUID animalId, int page, int size) {
        String jpql = animalId == null
                ? "SELECT p FROM FeedingPlanEntity p ORDER BY p.startedOn DESC, p.id"
                : "SELECT p FROM FeedingPlanEntity p WHERE p.animalId = :animalId ORDER BY p.startedOn DESC, p.id";
        TypedQuery<FeedingPlanEntity> query = em.createQuery(jpql, FeedingPlanEntity.class);
        if (animalId != null) {
            query.setParameter("animalId", animalId);
        }
        return FeedingPlanEntityMapper.toDomainList(query
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList());
    }

    @Override
    public long count(UUID animalId) {
        if (animalId == null) {
            return em.createQuery("SELECT COUNT(p) FROM FeedingPlanEntity p", Long.class)
                    .getSingleResult();
        }
        return em.createQuery(
                        "SELECT COUNT(p) FROM FeedingPlanEntity p WHERE p.animalId = :animalId", Long.class)
                .setParameter("animalId", animalId)
                .getSingleResult();
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(FeedingPlanEntity.class, id) != null;
    }

    @Override
    public List<FeedingPlan> findByAnimalIdAndStatusInForUpdate(UUID animalId, Collection<PlanStatus> statuses) {
        return FeedingPlanEntityMapper.toDomainList(em
                .createQuery(
                        "SELECT p FROM FeedingPlanEntity p WHERE p.animalId = :animalId AND p.status IN :statuses "
                                + "ORDER BY p.startedOn DESC, p.id",
                        FeedingPlanEntity.class)
                .setParameter("animalId", animalId)
                .setParameter("statuses", statuses)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList());
    }
}
