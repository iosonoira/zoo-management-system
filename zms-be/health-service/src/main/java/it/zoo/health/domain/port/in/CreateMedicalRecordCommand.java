package it.zoo.health.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMedicalRecordCommand(
    UUID animalId,
    String reason,
    String diagnosis,
    LocalDate examinedOn,
    String veterinarian,
    String performedBy
) {}
