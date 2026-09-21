package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.MedicalRecord;

import java.util.List;

public class MedicalRecordEntityMapper {

    private MedicalRecordEntityMapper() {}

    public static MedicalRecord toDomain(MedicalRecordEntity entity) {
        MedicalRecord record = new MedicalRecord(
                entity.getId(),
                entity.getAnimalId(),
                entity.getReason(),
                entity.getDiagnosis(),
                entity.getExaminedOn(),
                entity.getVeterinarian()
        );
        record.setCreatedBy(entity.getCreatedBy());
        record.setUpdatedBy(entity.getUpdatedBy());
        record.setVersion(entity.getVersion());
        return record;
    }

    public static MedicalRecordEntity toEntity(MedicalRecord record) {
        MedicalRecordEntity entity = new MedicalRecordEntity();
        entity.setId(record.getId());
        entity.setAnimalId(record.getAnimalId());
        entity.setReason(record.getReason());
        entity.setDiagnosis(record.getDiagnosis());
        entity.setExaminedOn(record.getExaminedOn());
        entity.setVeterinarian(record.getVeterinarian());
        entity.setCreatedBy(record.getCreatedBy());
        entity.setUpdatedBy(record.getUpdatedBy());
        entity.setVersion(record.getVersion());
        return entity;
    }

    public static List<MedicalRecord> toDomainList(List<MedicalRecordEntity> entities) {
        return entities.stream().map(MedicalRecordEntityMapper::toDomain).toList();
    }
}
