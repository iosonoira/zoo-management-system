package it.zoo.health.domain.port.in;

import java.util.UUID;

public record PrescribeTreatmentCommand(
    UUID medicalRecordId,
    String description,
    String performedBy
) {}
