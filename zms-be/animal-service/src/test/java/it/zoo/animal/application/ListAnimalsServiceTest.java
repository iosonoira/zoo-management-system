package it.zoo.animal.application;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAnimalsServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    ListAnimalsService service;

    @Test
    void shouldReturnAllAnimals() {
        Animal a1 = new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        Animal a2 = new Animal(UUID.randomUUID(), "Nemo", "Fish", false,
                Habitat.AQUATIC, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findAll()).thenReturn(List.of(a1, a2));

        List<Animal> result = service.listAll();

        assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoAnimals() {
        when(repository.findAll()).thenReturn(List.of());

        List<Animal> result = service.listAll();

        assertTrue(result.isEmpty());
    }
}
