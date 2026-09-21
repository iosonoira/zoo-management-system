package it.zoo.health.infrastructure.rest.mapper;

import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.infrastructure.rest.dto.MedicalRecordResponse;
import it.zoo.health.infrastructure.rest.dto.TreatmentResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface HealthDtoMapper {
    MedicalRecordResponse toResponse(MedicalRecord record);
    List<MedicalRecordResponse> toResponseList(List<MedicalRecord> records);
    TreatmentResponse toResponse(Treatment treatment);
    List<TreatmentResponse> toTreatmentResponseList(List<Treatment> treatments);
}
