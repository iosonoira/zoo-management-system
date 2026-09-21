package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecord;

public interface CreateMedicalRecordUseCase {
    MedicalRecord create(CreateMedicalRecordCommand cmd);
}
