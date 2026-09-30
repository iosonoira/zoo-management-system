package it.zoo.health.domain.port.out;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.model.Treatment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentRepository {
    Treatment save(Treatment treatment);
    Optional<Treatment> findById(UUID id);
    List<Treatment> findByMedicalRecordId(UUID medicalRecordId);
    Optional<UUID> findAnimalIdByTreatmentId(UUID treatmentId);
    List<Treatment> findByAnimalIdAndStatusIn(UUID animalId, Collection<TreatmentStatus> statuses);
}
