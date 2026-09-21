package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class UpdateTreatmentStatusService implements UpdateTreatmentStatusUseCase {

    private final TreatmentRepository repository;

    public UpdateTreatmentStatusService(TreatmentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Treatment updateStatus(UUID id, TreatmentStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }

        Treatment treatment = repository.findById(id)
                .orElseThrow(() -> new TreatmentNotFoundException(id));

        if (!treatment.canTransitionTo(newStatus)) {
            throw new InvalidTreatmentStatusTransitionException(treatment.getStatus(), newStatus);
        }

        treatment.setStatus(newStatus);
        if (newStatus == TreatmentStatus.ACTIVE && treatment.getStartedOn() == null) {
            treatment.setStartedOn(LocalDate.now());
        }
        if (newStatus == TreatmentStatus.COMPLETED || newStatus == TreatmentStatus.CANCELLED) {
            treatment.setEndedOn(LocalDate.now());
        }
        treatment.setUpdatedBy(performedBy);
        return repository.save(treatment);
    }
}
