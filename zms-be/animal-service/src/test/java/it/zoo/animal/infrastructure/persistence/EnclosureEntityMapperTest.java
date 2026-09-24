package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.model.Enclosure;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnclosureEntityMapperTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void shouldMapEntityToDomain() {
        EnclosureEntity entity = new EnclosureEntity();
        entity.setId(id);
        entity.setName("Savanna Paddock");
        entity.setHabitat(Habitat.TERRESTRIAL);

        Enclosure domain = EnclosureEntityMapper.toDomain(entity);

        assertEquals(entity.getId(), domain.id());
        assertEquals(entity.getName(), domain.name());
        assertEquals(entity.getHabitat(), domain.habitat());
    }

    @Test
    void shouldMapDomainToEntity() {
        Enclosure domain = new Enclosure(id, "Lagoon Pool", Habitat.AQUATIC);

        EnclosureEntity entity = EnclosureEntityMapper.toEntity(domain);

        assertEquals(domain.id(), entity.getId());
        assertEquals(domain.name(), entity.getName());
        assertEquals(domain.habitat(), entity.getHabitat());
    }
}
