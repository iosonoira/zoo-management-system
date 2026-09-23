package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.model.Enclosure;

import java.util.List;

public class EnclosureEntityMapper {

    private EnclosureEntityMapper() {}

    public static Enclosure toDomain(EnclosureEntity entity) {
        return new Enclosure(entity.getId(), entity.getName(), entity.getHabitat());
    }

    public static EnclosureEntity toEntity(Enclosure enclosure) {
        EnclosureEntity entity = new EnclosureEntity();
        entity.setId(enclosure.id());
        entity.setName(enclosure.name());
        entity.setHabitat(enclosure.habitat());
        return entity;
    }

    public static List<Enclosure> toDomainList(List<EnclosureEntity> entities) {
        return entities.stream().map(EnclosureEntityMapper::toDomain).toList();
    }
}
