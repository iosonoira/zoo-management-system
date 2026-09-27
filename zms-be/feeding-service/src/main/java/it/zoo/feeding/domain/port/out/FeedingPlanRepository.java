package it.zoo.feeding.domain.port.out;

import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.model.FeedingPlan;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedingPlanRepository {
    FeedingPlan save(FeedingPlan plan);
    Optional<FeedingPlan> findById(UUID id);
    List<FeedingPlan> findPage(UUID animalId, int page, int size);
    long count(UUID animalId);
    boolean existsById(UUID id);
    List<FeedingPlan> findByAnimalIdAndStatusIn(UUID animalId, Collection<PlanStatus> statuses);
}
