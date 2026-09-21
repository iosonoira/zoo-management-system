package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.Treatment;

import java.util.List;

public class TreatmentEntityMapper {

    private TreatmentEntityMapper() {}

    public static Treatment toDomain(TreatmentEntity entity) {
        Treatment treatment = new Treatment(
                entity.getId(),
                entity.getMedicalRecordId(),
                entity.getDescription(),
                entity.getStatus()
        );
        treatment.setStartedOn(entity.getStartedOn());
        treatment.setEndedOn(entity.getEndedOn());
        treatment.setCreatedBy(entity.getCreatedBy());
        treatment.setUpdatedBy(entity.getUpdatedBy());
        treatment.setVersion(entity.getVersion());
        return treatment;
    }

    public static TreatmentEntity toEntity(Treatment treatment) {
        TreatmentEntity entity = new TreatmentEntity();
        entity.setId(treatment.getId());
        entity.setMedicalRecordId(treatment.getMedicalRecordId());
        entity.setDescription(treatment.getDescription());
        entity.setStatus(treatment.getStatus());
        entity.setStartedOn(treatment.getStartedOn());
        entity.setEndedOn(treatment.getEndedOn());
        entity.setCreatedBy(treatment.getCreatedBy());
        entity.setUpdatedBy(treatment.getUpdatedBy());
        entity.setVersion(treatment.getVersion());
        return entity;
    }

    public static List<Treatment> toDomainList(List<TreatmentEntity> entities) {
        return entities.stream().map(TreatmentEntityMapper::toDomain).toList();
    }
}
