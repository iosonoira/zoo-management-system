package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.exception.InvalidPlanStatusTransitionException;
import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.UpdateFeedingPlanStatusUseCase;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class UpdateFeedingPlanStatusService implements UpdateFeedingPlanStatusUseCase {

    private final FeedingPlanRepository repository;

    public UpdateFeedingPlanStatusService(FeedingPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FeedingPlan updateStatus(UUID id, PlanStatus newStatus, String performedBy) {
        if (performedBy == null || performedBy.isBlank()) {
            throw new InvalidFeedingDataException("Actor must not be blank");
        }

        FeedingPlan plan = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new FeedingPlanNotFoundException(id));

        if (!plan.canTransitionTo(newStatus)) {
            throw new InvalidPlanStatusTransitionException(plan.getStatus(), newStatus);
        }

        plan.setStatus(newStatus);
        if (newStatus == PlanStatus.ENDED) {
            plan.setEndedOn(LocalDate.now());
        }
        plan.setUpdatedBy(performedBy);
        return repository.save(plan);
    }
}
