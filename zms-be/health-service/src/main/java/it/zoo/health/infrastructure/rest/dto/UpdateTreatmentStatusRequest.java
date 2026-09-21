package it.zoo.health.infrastructure.rest.dto;

import it.zoo.health.domain.enums.TreatmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTreatmentStatusRequest(
        @NotNull TreatmentStatus status
) {}
