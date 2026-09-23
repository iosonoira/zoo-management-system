package it.zoo.animal.application;

import it.zoo.animal.domain.event.AnimalEvent;
import it.zoo.animal.domain.event.AnimalRegistered;
import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.port.in.RegisterAnimalCommand;
import it.zoo.animal.domain.port.out.AnimalEventPublisher;
import it.zoo.animal.domain.port.out.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAnimalServiceTest {

    @Mock
    AnimalRepository repository;

    @Mock
    AnimalEventPublisher eventPublisher;

    @InjectMocks
    RegisterAnimalService service;

    private final UUID enclosureId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @Test
    void shouldRegisterAnimalWithHealthyStatus() {
        when(repository.save(any(Animal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        Animal result = service.register(cmd);

        assertEquals(AnimalStatus.HEALTHY, result.getStatus());
        assertEquals("Leo", result.getName());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());

        ArgumentCaptor<AnimalEvent> captor = ArgumentCaptor.forClass(AnimalEvent.class);
        verify(eventPublisher).publish(captor.capture());
        AnimalEvent event = captor.getValue();
        assertTrue(event instanceof AnimalRegistered);
        AnimalRegistered registered = (AnimalRegistered) event;
        assertEquals(result.getId(), registered.animalId());
        assertEquals("Leo", registered.name());
        assertEquals("Lion", registered.species());
        assertTrue(registered.dangerous());
        assertEquals(Habitat.TERRESTRIAL, registered.habitat());
        assertEquals(enclosureId, registered.enclosureId());
        assertEquals("vet", registered.performedBy());
        assertNotNull(registered.eventId());
        assertNotNull(registered.occurredAt());
    }

    @Test
    void shouldThrowWhenNameIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                null, "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenSpeciesIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "", true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenSpeciesIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", null, true, Habitat.TERRESTRIAL, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenHabitatIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, null, enclosureId, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenEnclosureIdIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, null, today, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenArrivalDateIsNull() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, null, "vet");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        RegisterAnimalCommand cmd = new RegisterAnimalCommand(
                "Leo", "Lion", true, Habitat.TERRESTRIAL, enclosureId, today, "  ");
        assertThrows(InvalidAnimalDataException.class, () -> service.register(cmd));
        verifyNoInteractions(eventPublisher);
    }
}
