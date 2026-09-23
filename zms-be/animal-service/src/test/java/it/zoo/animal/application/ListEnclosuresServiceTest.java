package it.zoo.animal.application;

import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.model.Enclosure;
import it.zoo.animal.domain.port.out.EnclosureRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListEnclosuresServiceTest {

    @Mock
    EnclosureRepository repository;

    @InjectMocks
    ListEnclosuresService service;

    @Test
    void shouldReturnAllEnclosures() {
        Enclosure enclosure1 = new Enclosure(UUID.randomUUID(), "African Savanna", Habitat.TERRESTRIAL);
        Enclosure enclosure2 = new Enclosure(UUID.randomUUID(), "Aquatic Zone", Habitat.AQUATIC);
        List<Enclosure> expectedList = List.of(enclosure1, enclosure2);

        when(repository.findAll()).thenReturn(expectedList);

        List<Enclosure> result = service.listAll();

        assertEquals(2, result.size());
        assertEquals(expectedList, result);
    }

    @Test
    void shouldReturnEmptyListWhenNoEnclosures() {
        when(repository.findAll()).thenReturn(List.of());

        List<Enclosure> result = service.listAll();

        assertTrue(result.isEmpty());
    }
}
