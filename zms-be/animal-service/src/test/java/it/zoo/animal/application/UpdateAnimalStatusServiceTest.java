package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.exception.InvalidStatusTransitionException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAnimalStatusServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    UpdateAnimalStatusService service;

    private Animal healthyAnimal(UUID id) {
        return new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
    }

    @Test
    void shouldUpdateStatus() {
        UUID id = UUID.randomUUID();
        Animal animal = healthyAnimal(id);
        when(repository.findById(id)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Animal result = service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, "vet");

        assertEquals(AnimalStatus.UNDER_OBSERVATION, result.getStatus());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class,
                () -> service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, "vet"));
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        UUID id = UUID.randomUUID();
        Animal deceased = new Animal(id, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.DECEASED);
        when(repository.findById(id)).thenReturn(Optional.of(deceased));

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateStatus(id, AnimalStatus.HEALTHY, "vet"));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID id = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.updateStatus(id, AnimalStatus.UNDER_OBSERVATION, ""));
    }
}
