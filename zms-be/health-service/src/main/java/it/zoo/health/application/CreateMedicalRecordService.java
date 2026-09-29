package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.in.CreateMedicalRecordCommand;
import it.zoo.health.domain.port.in.CreateMedicalRecordUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
public class CreateMedicalRecordService implements CreateMedicalRecordUseCase {

    /**
     * The vet picks the date in their own time zone, which can be ahead of the server's.
     * UTC+14 is the first zone to reach a new day, so only a date that is in the future
     * everywhere is rejected.
     */
    static final ZoneOffset EARLIEST_ZONE = ZoneOffset.ofHours(14);

    private final MedicalRecordRepository repository;

    public CreateMedicalRecordService(MedicalRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MedicalRecord create(CreateMedicalRecordCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }
        if (cmd.animalId() == null) {
            throw new InvalidMedicalDataException("Animal ID must not be null");
        }
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new InvalidMedicalDataException("Reason must not be blank");
        }
        if (cmd.diagnosis() == null || cmd.diagnosis().isBlank()) {
            throw new InvalidMedicalDataException("Diagnosis must not be blank");
        }
        if (cmd.veterinarian() == null || cmd.veterinarian().isBlank()) {
            throw new InvalidMedicalDataException("Veterinarian must not be blank");
        }
        if (cmd.examinedOn() == null) {
            throw new InvalidMedicalDataException("Examination date must not be null");
        }
        if (cmd.examinedOn().isAfter(LocalDate.now(EARLIEST_ZONE))) {
            throw new InvalidMedicalDataException("Examination date must not be in the future");
        }

        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(),
                cmd.animalId(),
                cmd.reason(),
                cmd.diagnosis(),
                cmd.examinedOn(),
                cmd.veterinarian()
        );
        record.setCreatedBy(cmd.performedBy());
        record.setUpdatedBy(cmd.performedBy());
        return repository.save(record);
    }
}
