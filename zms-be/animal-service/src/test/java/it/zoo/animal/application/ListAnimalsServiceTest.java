package it.zoo.animal.application;

import it.zoo.animal.domain.exception.InvalidAnimalDataException;
import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalPage;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import it.zoo.animal.domain.port.in.ListAnimalsUseCase;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListAnimalsServiceTest {

    @Mock
    AnimalRepository repository;

    @InjectMocks
    ListAnimalsService service;

    @Test
    void shouldReturnRequestedPageWithTotal() {
        when(repository.findPage(0, 20)).thenReturn(List.of(animal("Leo"), animal("Nemo")));
        when(repository.count()).thenReturn(137L);

        AnimalPage result = service.list(0, 20);

        assertEquals(2, result.items().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(137L, result.total());
    }

    @Test
    void shouldReturnEmptyPageWhenNoAnimals() {
        when(repository.findPage(3, 20)).thenReturn(List.of());
        when(repository.count()).thenReturn(0L);

        AnimalPage result = service.list(3, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.total());
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        assertThrows(InvalidAnimalDataException.class, () -> service.list(-1, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSizeIsBelowOne() {
        assertThrows(InvalidAnimalDataException.class, () -> service.list(0, 0));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldThrowWhenSizeExceedsMaximum() {
        assertThrows(InvalidAnimalDataException.class,
                () -> service.list(0, ListAnimalsUseCase.MAX_PAGE_SIZE + 1));
        verifyNoInteractions(repository);
    }

    private Animal animal(String name) {
        return new Animal(UUID.randomUUID(), name, "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), AnimalStatus.HEALTHY);
    }
}
