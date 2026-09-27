package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.FeedingPlanNotActiveException;
import it.zoo.feeding.domain.exception.FeedingPlanNotFoundException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.RecordFeedingCommand;
import it.zoo.feeding.domain.port.in.RecordFeedingUseCase;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@ApplicationScoped
public class RecordFeedingService implements RecordFeedingUseCase {

    private final FeedingPlanRepository planRepository;
    private final FeedingRepository feedingRepository;

    public RecordFeedingService(FeedingPlanRepository planRepository, FeedingRepository feedingRepository) {
        this.planRepository = planRepository;
        this.feedingRepository = feedingRepository;
    }

    @Override
    @Transactional
    public Feeding record(RecordFeedingCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidFeedingDataException("Actor must not be blank");
        }
        if (cmd.planId() == null) {
            throw new InvalidFeedingDataException("Plan ID must not be null");
        }
        if (cmd.quantityGrams() == null || cmd.quantityGrams() < 0) {
            throw new InvalidFeedingDataException("Quantity must not be negative");
        }

        Instant now = Instant.now();
        Instant fedAt = cmd.fedAt() == null ? now : cmd.fedAt();

        if (fedAt.isAfter(now.plus(1, ChronoUnit.MINUTES))) {
            throw new InvalidFeedingDataException("Feeding time must not be in the future");
        }

        FeedingPlan plan = planRepository.findById(cmd.planId())
                .orElseThrow(() -> new FeedingPlanNotFoundException(cmd.planId()));

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new FeedingPlanNotActiveException(plan.getId(), plan.getStatus());
        }

        Feeding feeding = new Feeding(
                UUID.randomUUID(),
                plan.getId(),
                fedAt,
                cmd.quantityGrams(),
                cmd.notes(),
                cmd.performedBy()
        );
        return feedingRepository.save(feeding);
    }
}
