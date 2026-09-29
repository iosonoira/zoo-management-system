package it.zoo.health.application;

import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.InvalidAnimalEventException;
import it.zoo.health.domain.model.DeceasedAnimal;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.HandleAnimalEventCommand;
import it.zoo.health.domain.port.in.HandleAnimalEventUseCase;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

@ApplicationScoped
public class HandleAnimalEventService implements HandleAnimalEventUseCase {

    private static final String STATUS_CHANGED_EVENT_TYPE = "ANIMAL_STATUS_CHANGED";
    private static final String DECEASED_STATUS = "DECEASED";
    private static final String DEFAULT_PERFORMED_BY = "animal-service";

    private static final Logger LOG = Logger.getLogger(HandleAnimalEventService.class);

    private final DeceasedAnimalRepository deceasedAnimals;
    private final TreatmentRepository treatments;
    private final AnimalLock animalLock;

    public HandleAnimalEventService(DeceasedAnimalRepository deceasedAnimals, TreatmentRepository treatments,
            AnimalLock animalLock) {
        this.deceasedAnimals = deceasedAnimals;
        this.treatments = treatments;
        this.animalLock = animalLock;
    }

    @Override
    @Transactional
    public void handle(HandleAnimalEventCommand command) {
        if (command == null) {
            throw new InvalidAnimalEventException("Event command must not be null");
        }
        if (command.eventId() == null) {
            throw new InvalidAnimalEventException("Event ID must not be null");
        }
        if (command.eventType() == null) {
            throw new InvalidAnimalEventException("Event type must not be null");
        }
        if (command.animalId() == null) {
            throw new InvalidAnimalEventException("Animal ID must not be null");
        }
        if (command.occurredAt() == null) {
            throw new InvalidAnimalEventException("Occurred at must not be null");
        }

        if (!STATUS_CHANGED_EVENT_TYPE.equals(command.eventType())) {
            return;
        }
        if (command.newStatus() == null) {
            throw new InvalidAnimalEventException("New status must not be null for " + STATUS_CHANGED_EVENT_TYPE + " events");
        }
        if (!DECEASED_STATUS.equals(command.newStatus())) {
            return;
        }

        // Taken before any read, like PrescribeTreatmentService and UpdateTreatmentStatusService:
        // the treatments below are read after any concurrent write on this animal has committed.
        animalLock.acquire(command.animalId());

        if (deceasedAnimals.existsByAnimalId(command.animalId())) {
            return;
        }

        deceasedAnimals.save(new DeceasedAnimal(command.animalId(), command.eventId(), command.occurredAt()));

        String updatedBy = (command.performedBy() == null || command.performedBy().isBlank())
                ? DEFAULT_PERFORMED_BY
                : command.performedBy();
        LocalDate diedOn = LocalDate.ofInstant(command.occurredAt(), ZoneId.systemDefault());

        List<Treatment> toCancel = treatments.findByAnimalIdAndStatusIn(
                command.animalId(), EnumSet.of(TreatmentStatus.PRESCRIBED, TreatmentStatus.ACTIVE));

        for (Treatment treatment : toCancel) {
            LocalDate startedOn = treatment.getStartedOn();
            treatment.setStatus(TreatmentStatus.CANCELLED);
            treatment.setEndedOn(startedOn != null && diedOn.isBefore(startedOn) ? startedOn : diedOn);
            treatment.setUpdatedBy(updatedBy);
            treatments.save(treatment);
        }

        LOG.infof("Cancelled %d treatment(s) for deceased animal %s", toCancel.size(), command.animalId());
    }
}
