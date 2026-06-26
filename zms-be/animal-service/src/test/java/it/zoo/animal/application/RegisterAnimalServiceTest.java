package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    RegisterAnimalService service;

    private final UUID enclosureId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @Test
    void shouldRegisterAnimalWithHealthyStatus() {
        Animal saved = new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, enclosureId, today, AnimalStatus.HEALTHY);
        when(repository.save(any(Animal.class))).thenReturn(saved);

        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        Animal result = service.register(cmd);

        assertEquals(AnimalStatus.HEALTHY, result.getStatus());
        assertEquals("Leo", result.getName());
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                null, "Lion", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenSpeciesIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "", true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenSpeciesIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", null, true, Habitat.TERRESTRIAL, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenHabitatIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, null, enclosureId, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenEnclosureIdIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, null, today);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }

    @Test
    void shouldThrowWhenArrivalDateIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, null);
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
    }
}
