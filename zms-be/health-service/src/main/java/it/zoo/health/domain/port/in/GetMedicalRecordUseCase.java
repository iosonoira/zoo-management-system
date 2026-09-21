package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecordDetail;

import java.util.UUID;

public interface GetMedicalRecordUseCase {
    MedicalRecordDetail getById(UUID id);
}
