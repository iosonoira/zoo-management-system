package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.AnimalDeceasedException;
import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.exception.TreatmentNotFoundException;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class UpdateTreatmentStatusService implements UpdateTreatmentStatusUseCase {

    private final TreatmentRepository repository;
    private final DeceasedAnimalRepository deceasedAnimals;
    private final AnimalLock animalLock;

    public UpdateTreatmentStatusService(TreatmentRepository repository,
                                        DeceasedAnimalRepository deceasedAnimals,
                                        AnimalLock animalLock) {
        this.repository = repository;
        this.deceasedAnimals = deceasedAnimals;
        this.animalLock = animalLock;
    }

    @Override
    @Transactional
    public Treatment updateStatus(UUID id, TreatmentStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidMedicalDataException("Actor must not be blank");
        }

        // The treatment is read only after the lock, so a DECEASED event that cancelled it in the
        // meantime is seen here, and this change never races the event on the treatment's version.
        UUID animalId = repository.findAnimalIdByTreatmentId(id)
                .orElseThrow(() -> new TreatmentNotFoundException(id));
        animalLock.acquire(animalId);

        Treatment treatment = repository.findById(id)
                .orElseThrow(() -> new TreatmentNotFoundException(id));

        if (!treatment.canTransitionTo(newStatus)) {
            throw new InvalidTreatmentStatusTransitionException(treatment.getStatus(), newStatus);
        }
        if (newStatus == TreatmentStatus.ACTIVE && deceasedAnimals.existsByAnimalId(animalId)) {
            throw new AnimalDeceasedException(animalId);
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
