package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.model.Animal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalEntityMapperTest {

    private final UUID id = UUID.randomUUID();
    private final UUID enclosureId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2024, 1, 15);

    @Test
    void shouldMapEntityToDomain() {
        AnimalEntity entity = buildEntity();

        Animal domain = AnimalEntityMapper.toDomain(entity);

        assertEquals(entity.getId(), domain.getId());
        assertEquals(entity.getName(), domain.getName());
        assertEquals(entity.getSpecies(), domain.getSpecies());
        assertEquals(entity.isDangerous(), domain.isDangerous());
        assertEquals(entity.getHabitat(), domain.getHabitat());
        assertEquals(entity.getEnclosureId(), domain.getEnclosureId());
        assertEquals(entity.getArrivalDate(), domain.getArrivalDate());
        assertEquals(entity.getStatus(), domain.getStatus());
        assertEquals(entity.getCreatedBy(), domain.getCreatedBy());
        assertEquals(entity.getUpdatedBy(), domain.getUpdatedBy());
        assertEquals(entity.getVersion(), domain.getVersion());
    }

    @Test
    void shouldMapDomainToEntity() {
        Animal domain = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, enclosureId, date, AnimalStatus.HEALTHY);
        domain.setCreatedBy("admin");
        domain.setUpdatedBy("vet");
        domain.setVersion(3L);

        AnimalEntity entity = AnimalEntityMapper.toEntity(domain);

        assertEquals(domain.getId(), entity.getId());
        assertEquals(domain.getName(), entity.getName());
        assertEquals(domain.getSpecies(), entity.getSpecies());
        assertEquals(domain.isDangerous(), entity.isDangerous());
        assertEquals(domain.getHabitat(), entity.getHabitat());
        assertEquals(domain.getEnclosureId(), entity.getEnclosureId());
        assertEquals(domain.getArrivalDate(), entity.getArrivalDate());
        assertEquals(domain.getStatus(), entity.getStatus());
        assertEquals("admin", entity.getCreatedBy());
        assertEquals("vet", entity.getUpdatedBy());
        assertEquals(3L, entity.getVersion());
    }

    private AnimalEntity buildEntity() {
        AnimalEntity entity = new AnimalEntity();
        entity.setId(id);
        entity.setName("Leo");
        entity.setSpecies("Lion");
        entity.setDangerous(true);
        entity.setHabitat(Habitat.TERRESTRIAL);
        entity.setEnclosureId(enclosureId);
        entity.setArrivalDate(date);
        entity.setStatus(AnimalStatus.HEALTHY);
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("vet");
        entity.setVersion(2L);
        return entity;
    }
}
