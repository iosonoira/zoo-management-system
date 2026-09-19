package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
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
class TransferAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    TransferAnimalService service;

    @Test
    void shouldTransferAnimalToNewEnclosure() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        Animal animal = new Animal(animalId, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(animalId)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Animal result = service.transfer(animalId, newEnclosureId, "keeper");

        assertEquals(newEnclosureId, result.getEnclosureId());
        assertEquals("keeper", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        when(repository.findById(animalId)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class,
                () -> service.transfer(animalId, newEnclosureId, "keeper"));
    }

    @Test
    void shouldThrowWhenTargetEnclosureIdIsNull() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, null, "keeper"));
    }

    @Test
    void shouldThrowWhenTransferringDeceasedAnimal() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        Animal deceased = new Animal(animalId, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.DECEASED);
        when(repository.findById(animalId)).thenReturn(Optional.of(deceased));

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, newEnclosureId, "keeper"));
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, newEnclosureId, null));
    }
}
