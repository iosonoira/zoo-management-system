package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    GetAnimalService service;

    @Test
    void shouldReturnAnimalWhenFound() {
        UUID id = UUID.randomUUID();
        Animal animal = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(id)).thenReturn(Optional.of(animal));

        Animal result = service.getById(id);

        assertEquals(id, result.getId());
        assertEquals("Leo", result.getName());
    }

    @Test
    void shouldThrowWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class, () -> service.getById(id));
    }
}
