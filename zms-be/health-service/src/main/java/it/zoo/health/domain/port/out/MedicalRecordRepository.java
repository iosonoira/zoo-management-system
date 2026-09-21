package it.zoo.health.domain.port.out;

import it.zoo.health.domain.model.MedicalRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository {
    MedicalRecord save(MedicalRecord record);
    Optional<MedicalRecord> findById(UUID id);
    List<MedicalRecord> findPage(UUID animalId, int page, int size);
    long count(UUID animalId);
    boolean existsById(UUID id);
}
