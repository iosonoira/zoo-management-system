package it.zoo.health.application;

import it.zoo.health.domain.exception.MedicalRecordNotFoundException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordDetail;
import it.zoo.health.domain.port.in.GetMedicalRecordUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class GetMedicalRecordService implements GetMedicalRecordUseCase {

    private final MedicalRecordRepository recordRepository;
    private final TreatmentRepository treatmentRepository;

    public GetMedicalRecordService(MedicalRecordRepository recordRepository,
                                   TreatmentRepository treatmentRepository) {
        this.recordRepository = recordRepository;
        this.treatmentRepository = treatmentRepository;
    }

    @Override
    public MedicalRecordDetail getById(UUID id) {
        MedicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new MedicalRecordNotFoundException(id));
        return new MedicalRecordDetail(record, treatmentRepository.findByMedicalRecordId(id));
    }
}
