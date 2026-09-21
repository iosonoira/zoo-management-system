package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.exception.ConcurrentMedicalRecordUpdateException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MedicalRecordPanacheRepository implements MedicalRecordRepository {

    private final EntityManager em;

    public MedicalRecordPanacheRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public MedicalRecord save(MedicalRecord record) {
        MedicalRecordEntity entity = MedicalRecordEntityMapper.toEntity(record);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentMedicalRecordUpdateException(record.getId());
        }
        return MedicalRecordEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<MedicalRecord> findById(UUID id) {
        return Optional.ofNullable(em.find(MedicalRecordEntity.class, id))
                .map(MedicalRecordEntityMapper::toDomain);
    }

    @Override
    public List<MedicalRecord> findPage(UUID animalId, int page, int size) {
        String jpql = animalId == null
                ? "SELECT m FROM MedicalRecordEntity m ORDER BY m.examinedOn DESC, m.id"
                : "SELECT m FROM MedicalRecordEntity m WHERE m.animalId = :animalId ORDER BY m.examinedOn DESC, m.id";
        TypedQuery<MedicalRecordEntity> query = em.createQuery(jpql, MedicalRecordEntity.class);
        if (animalId != null) {
            query.setParameter("animalId", animalId);
        }
        return MedicalRecordEntityMapper.toDomainList(query
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList());
    }

    @Override
    public long count(UUID animalId) {
        if (animalId == null) {
            return em.createQuery("SELECT COUNT(m) FROM MedicalRecordEntity m", Long.class)
                    .getSingleResult();
        }
        return em.createQuery(
                        "SELECT COUNT(m) FROM MedicalRecordEntity m WHERE m.animalId = :animalId", Long.class)
                .setParameter("animalId", animalId)
                .getSingleResult();
    }

    @Override
    public boolean existsById(UUID id) {
        return em.find(MedicalRecordEntity.class, id) != null;
    }
}
