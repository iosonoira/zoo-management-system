package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.model.DeceasedAnimal;

public class DeceasedAnimalEntityMapper {

    private DeceasedAnimalEntityMapper() {}

    public static DeceasedAnimal toDomain(DeceasedAnimalEntity entity) {
        return new DeceasedAnimal(entity.getAnimalId(), entity.getEventId(), entity.getOccurredAt());
    }

    public static DeceasedAnimalEntity toEntity(DeceasedAnimal deceasedAnimal) {
        DeceasedAnimalEntity entity = new DeceasedAnimalEntity();
        entity.setAnimalId(deceasedAnimal.animalId());
        entity.setEventId(deceasedAnimal.eventId());
        entity.setOccurredAt(deceasedAnimal.occurredAt());
        return entity;
    }
}
