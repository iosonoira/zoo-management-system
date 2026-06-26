package it.zoo.animal.application;

import it.zoo.animal.domain.exception.AnimalNotFoundException;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
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
        Animal transferred = new Animal(animalId, "Leo", "Lion", true,
                Habitat.TERRESTRIAL, newEnclosureId, LocalDate.now(), AnimalStatus.HEALTHY);
        when(repository.findById(animalId)).thenReturn(Optional.of(animal));
        when(repository.save(any(Animal.class))).thenReturn(transferred);

        Animal result = service.transfer(animalId, newEnclosureId);

        assertEquals(newEnclosureId, result.getEnclosureId());
    }

    @Test
    void shouldThrowWhenAnimalNotFound() {
        UUID animalId = UUID.randomUUID();
        UUID newEnclosureId = UUID.randomUUID();
        when(repository.findById(animalId)).thenReturn(Optional.empty());

        assertThrows(AnimalNotFoundException.class,
                () -> service.transfer(animalId, newEnclosureId));
    }

    @Test
    void shouldThrowWhenTargetEnclosureIdIsNull() {
        UUID animalId = UUID.randomUUID();

        assertThrows(InvalidAnimalDataException.class,
                () -> service.transfer(animalId, null));
    }
}
