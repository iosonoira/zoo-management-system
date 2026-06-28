package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.model.Animal;

import java.util.List;

public class AnimalEntityMapper {

    private AnimalEntityMapper() {}

    public static Animal toDomain(AnimalEntity entity) {
        return new Animal(
                entity.getId(),
                entity.getName(),
                entity.getSpecies(),
                entity.isDangerous(),
                entity.getHabitat(),
                entity.getEnclosureId(),
                entity.getArrivalDate(),
                entity.getStatus()
        );
    }

    public static AnimalEntity toEntity(Animal animal) {
        AnimalEntity entity = new AnimalEntity();
        entity.setId(animal.getId());
        entity.setName(animal.getName());
        entity.setSpecies(animal.getSpecies());
        entity.setDangerous(animal.isDangerous());
        entity.setHabitat(animal.getHabitat());
        entity.setEnclosureId(animal.getEnclosureId());
        entity.setArrivalDate(animal.getArrivalDate());
        entity.setStatus(animal.getStatus());
        return entity;
    }

    public static List<Animal> toDomainList(List<AnimalEntity> entities) {
        return entities.stream().map(AnimalEntityMapper::toDomain).toList();
    }
}
