package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.model.FeedingPlan;

import java.util.List;

public class FeedingPlanEntityMapper {

    private FeedingPlanEntityMapper() {}

    public static FeedingPlan toDomain(FeedingPlanEntity entity) {
        FeedingPlan plan = new FeedingPlan(
                entity.getId(),
                entity.getAnimalId(),
                entity.getFood(),
                entity.getQuantityGrams(),
                entity.getFeedingTimes(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getStartedOn()
        );
        plan.setEndedOn(entity.getEndedOn());
        plan.setCreatedBy(entity.getCreatedBy());
        plan.setUpdatedBy(entity.getUpdatedBy());
        plan.setVersion(entity.getVersion());
        return plan;
    }

    public static FeedingPlanEntity toEntity(FeedingPlan plan) {
        FeedingPlanEntity entity = new FeedingPlanEntity();
        entity.setId(plan.getId());
        entity.setAnimalId(plan.getAnimalId());
        entity.setFood(plan.getFood());
        entity.setQuantityGrams(plan.getQuantityGrams());
        entity.setFeedingTimes(plan.getFeedingTimes());
        entity.setNotes(plan.getNotes());
        entity.setStatus(plan.getStatus());
        entity.setStartedOn(plan.getStartedOn());
        entity.setEndedOn(plan.getEndedOn());
        entity.setCreatedBy(plan.getCreatedBy());
        entity.setUpdatedBy(plan.getUpdatedBy());
        entity.setVersion(plan.getVersion());
        return entity;
    }

    public static List<FeedingPlan> toDomainList(List<FeedingPlanEntity> entities) {
        return entities.stream().map(FeedingPlanEntityMapper::toDomain).toList();
    }
}
