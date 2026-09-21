package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.port.in.ListMedicalRecordsUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ListMedicalRecordsService implements ListMedicalRecordsUseCase {

    private final MedicalRecordRepository repository;

    public ListMedicalRecordsService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public MedicalRecordPage list(UUID animalId, int page, int size) {
        if (page < 0) {
            throw new InvalidMedicalDataException("Page must not be negative");
        }
        if (size < 1) {
            throw new InvalidMedicalDataException("Size must be at least 1");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidMedicalDataException("Size must not exceed " + MAX_PAGE_SIZE);
        }

        List<MedicalRecord> items = repository.findPage(animalId, page, size);
        return new MedicalRecordPage(items, page, size, repository.count(animalId));
    }
}
