package it.zoo.health.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrescribeTreatmentRequest(
        @NotBlank @Size(max = 500) String description
) {}
