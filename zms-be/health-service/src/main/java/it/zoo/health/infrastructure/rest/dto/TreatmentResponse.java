package it.zoo.health.infrastructure.rest.dto;

import it.zoo.health.domain.enums.TreatmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TreatmentResponse(
        UUID id,
        UUID medicalRecordId,
        String description,
        TreatmentStatus status,
        LocalDate startedOn,
        LocalDate endedOn,
        String createdBy,
        String updatedBy
) {}
