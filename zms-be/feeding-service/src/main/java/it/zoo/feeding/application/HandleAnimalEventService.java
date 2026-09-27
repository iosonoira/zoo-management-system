package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.InvalidAnimalEventException;
import it.zoo.feeding.domain.model.DeceasedAnimal;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.HandleAnimalEventCommand;
import it.zoo.feeding.domain.port.in.HandleAnimalEventUseCase;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
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
    private final FeedingPlanRepository feedingPlans;

    public HandleAnimalEventService(DeceasedAnimalRepository deceasedAnimals, FeedingPlanRepository feedingPlans) {
        this.deceasedAnimals = deceasedAnimals;
        this.feedingPlans = feedingPlans;
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
        if (deceasedAnimals.existsByAnimalId(command.animalId())) {
            return;
        }

        deceasedAnimals.save(new DeceasedAnimal(command.animalId(), command.eventId(), command.occurredAt()));

        String updatedBy = (command.performedBy() == null || command.performedBy().isBlank())
                ? DEFAULT_PERFORMED_BY
                : command.performedBy();
        LocalDate diedOn = LocalDate.ofInstant(command.occurredAt(), ZoneId.systemDefault());

        List<FeedingPlan> plansToEnd = feedingPlans.findByAnimalIdAndStatusIn(
                command.animalId(), EnumSet.of(PlanStatus.ACTIVE, PlanStatus.SUSPENDED));

        for (FeedingPlan plan : plansToEnd) {
            plan.setStatus(PlanStatus.ENDED);
            plan.setEndedOn(diedOn.isBefore(plan.getStartedOn()) ? plan.getStartedOn() : diedOn);
            plan.setUpdatedBy(updatedBy);
            feedingPlans.save(plan);
        }

        LOG.infof("Ended %d feeding plan(s) for deceased animal %s", plansToEnd.size(), command.animalId());
    }
}
