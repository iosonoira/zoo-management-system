package it.zoo.health.infrastructure.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMedicalRecordRequest(
        @NotNull UUID animalId,
        @NotBlank @Size(max = 200) String reason,
        @NotBlank @Size(max = 1000) String diagnosis,
        @NotNull LocalDate examinedOn,
        @NotBlank @Size(max = 100) String veterinarian
) {}
