package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.AnimalDeceasedException;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.in.PrescribeTreatmentUseCase;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class PrescribeTreatmentService implements PrescribeTreatmentUseCase {

    private final MedicalRecordRepository recordRepository;
    private final TreatmentRepository treatmentRepository;
    private final DeceasedAnimalRepository deceasedAnimals;
    private final AnimalLock animalLock;

    public PrescribeTreatmentService(MedicalRecordRepository recordRepository,
                                     TreatmentRepository treatmentRepository,
                                     DeceasedAnimalRepository deceasedAnimals,
                                     AnimalLock animalLock) {
        this.recordRepository = recordRepository;
        this.treatmentRepository = treatmentRepository;
        this.deceasedAnimals = deceasedAnimals;
        this.animalLock = animalLock;
    }

    @Override
    @Transactional
    public Treatment prescribe(PrescribeTreatmentCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }
        if (cmd.medicalRecordId() == null) {
            throw new InvalidMedicalDataException("Medical record ID must not be null");
        }
        if (cmd.description() == null || cmd.description().isBlank()) {
            throw new InvalidMedicalDataException("Treatment description must not be blank");
        }
        MedicalRecord record = recordRepository.findById(cmd.medicalRecordId())
                .orElseThrow(() -> new MedicalRecordNotFoundException(cmd.medicalRecordId()));

        // Serializes with HandleAnimalEventService: a DECEASED event either commits first and
        // is seen here, or waits and then cancels this treatment too.
        animalLock.acquire(record.getAnimalId());
        if (deceasedAnimals.existsByAnimalId(record.getAnimalId())) {
            throw new AnimalDeceasedException(record.getAnimalId());
        }

        Treatment treatment = new Treatment(
                UUID.randomUUID(),
                cmd.medicalRecordId(),
                cmd.description(),
                TreatmentStatus.PRESCRIBED
        );
        treatment.setCreatedBy(cmd.performedBy());
        treatment.setUpdatedBy(cmd.performedBy());
        return treatmentRepository.save(treatment);
    }
}
