package it.zoo.feeding.application;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.AnimalDeceasedException;
import it.zoo.feeding.domain.exception.InvalidFeedingDataException;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.CreateFeedingPlanCommand;
import it.zoo.feeding.domain.port.in.CreateFeedingPlanUseCase;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CreateFeedingPlanService implements CreateFeedingPlanUseCase {

    private final FeedingPlanRepository repository;
    private final DeceasedAnimalRepository deceasedAnimals;

    public CreateFeedingPlanService(FeedingPlanRepository repository, DeceasedAnimalRepository deceasedAnimals) {
        this.repository = repository;
        this.deceasedAnimals = deceasedAnimals;
    }

    @Override
    @Transactional
    public FeedingPlan create(CreateFeedingPlanCommand cmd) {
        if (cmd.performedBy() == null || cmd.performedBy().isBlank()) {
            throw new InvalidFeedingDataException("Actor must not be blank");
        }
        if (cmd.animalId() == null) {
            throw new InvalidFeedingDataException("Animal ID must not be null");
        }
        if (cmd.food() == null || cmd.food().isBlank()) {
            throw new InvalidFeedingDataException("Food must not be blank");
        }
        if (cmd.quantityGrams() == null || cmd.quantityGrams() <= 0) {
            throw new InvalidFeedingDataException("Quantity must be greater than zero");
        }
        if (cmd.feedingTimes() == null || cmd.feedingTimes().isEmpty()) {
            throw new InvalidFeedingDataException("At least one feeding time is required");
        }
        if (cmd.feedingTimes().stream().anyMatch(t -> t == null)) {
            throw new InvalidFeedingDataException("Feeding times must not contain null elements");
        }
        if (cmd.feedingTimes().size() > 6) {
            throw new InvalidFeedingDataException("At most 6 feeding times are allowed");
        }

        Set<LocalTime> uniqueTimes = new HashSet<>(cmd.feedingTimes());
        if (uniqueTimes.size() != cmd.feedingTimes().size()) {
            throw new InvalidFeedingDataException("Feeding times must not repeat");
        }

        if (deceasedAnimals.existsByAnimalId(cmd.animalId())) {
            throw new AnimalDeceasedException(cmd.animalId());
        }

        List<LocalTime> sortedTimes = cmd.feedingTimes().stream()
                .sorted()
                .collect(Collectors.toList());

        FeedingPlan plan = new FeedingPlan(
                UUID.randomUUID(),
                cmd.animalId(),
                cmd.food(),
                cmd.quantityGrams(),
                sortedTimes,
                cmd.notes(),
                PlanStatus.ACTIVE,
                LocalDate.now()
        );
        plan.setCreatedBy(cmd.performedBy());
        plan.setUpdatedBy(cmd.performedBy());
        return repository.save(plan);
    }
}
