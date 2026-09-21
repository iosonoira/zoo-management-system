package it.zoo.health.domain.port.in;

import it.zoo.health.domain.model.MedicalRecordPage;

import java.util.UUID;

public interface ListMedicalRecordsUseCase {

    int MAX_PAGE_SIZE = 100;

    MedicalRecordPage list(UUID animalId, int page, int size);
}
