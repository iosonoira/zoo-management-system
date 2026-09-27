package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.model.Feeding;

import java.util.List;

public class FeedingEntityMapper {

    private FeedingEntityMapper() {}

    public static Feeding toDomain(FeedingEntity entity) {
        return new Feeding(
                entity.getId(),
                entity.getPlanId(),
                entity.getFedAt(),
                entity.getQuantityGrams(),
                entity.getNotes(),
                entity.getRecordedBy()
        );
    }

    public static FeedingEntity toEntity(Feeding feeding) {
        FeedingEntity entity = new FeedingEntity();
        entity.setId(feeding.getId());
        entity.setPlanId(feeding.getPlanId());
        entity.setFedAt(feeding.getFedAt());
        entity.setQuantityGrams(feeding.getQuantityGrams());
        entity.setNotes(feeding.getNotes());
        entity.setRecordedBy(feeding.getRecordedBy());
        return entity;
    }

    public static List<Feeding> toDomainList(List<FeedingEntity> entities) {
        return entities.stream().map(FeedingEntityMapper::toDomain).toList();
    }
}
