package it.zoo.health.infrastructure.rest.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MedicalRecordDetailResponse(
        UUID id,
        UUID animalId,
        String reason,
        String diagnosis,
        LocalDate examinedOn,
        String veterinarian,
        String createdBy,
        String updatedBy,
        List<TreatmentResponse> treatments
) {}
