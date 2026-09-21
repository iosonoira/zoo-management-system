package it.zoo.health.infrastructure.rest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordResponse(
        UUID id,
        UUID animalId,
        String reason,
        String diagnosis,
        LocalDate examinedOn,
        String veterinarian,
        String createdBy,
        String updatedBy
) {}
