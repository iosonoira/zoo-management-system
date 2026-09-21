package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.exception.ConcurrentTreatmentUpdateException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TreatmentPanacheRepository implements TreatmentRepository {

    private final EntityManager em;

    public TreatmentPanacheRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Treatment save(Treatment treatment) {
        TreatmentEntity entity = TreatmentEntityMapper.toEntity(treatment);
        try {
            entity = em.merge(entity);
            em.flush();
        } catch (OptimisticLockException e) {
            throw new ConcurrentTreatmentUpdateException(treatment.getId());
        }
        return TreatmentEntityMapper.toDomain(entity);
    }

    @Override
    public Optional<Treatment> findById(UUID id) {
        return Optional.ofNullable(em.find(TreatmentEntity.class, id))
                .map(TreatmentEntityMapper::toDomain);
    }

    @Override
    public List<Treatment> findByMedicalRecordId(UUID medicalRecordId) {
        return TreatmentEntityMapper.toDomainList(em
                .createQuery("SELECT t FROM TreatmentEntity t WHERE t.medicalRecordId = :recordId ORDER BY t.id",
                        TreatmentEntity.class)
                .setParameter("recordId", medicalRecordId)
                .getResultList());
    }
}
