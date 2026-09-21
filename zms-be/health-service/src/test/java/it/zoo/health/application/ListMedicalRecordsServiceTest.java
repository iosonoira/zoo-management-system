package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.MedicalRecordPage;
import it.zoo.health.domain.port.in.ListMedicalRecordsUseCase;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
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
class ListMedicalRecordsServiceTest {

    @Mock
    MedicalRecordRepository repository;

    @InjectMocks
    ListMedicalRecordsService service;

    private MedicalRecord record(UUID animalId) {
        return new MedicalRecord(UUID.randomUUID(), animalId, "Limping",
                "Sprained paw", LocalDate.of(2026, 9, 1), "Dr Rossi");
    }

    @Test
    void shouldReturnPageFilteredByAnimal() {
        UUID animalId = UUID.randomUUID();
        when(repository.findPage(animalId, 0, 20)).thenReturn(List.of(record(animalId)));
        when(repository.count(animalId)).thenReturn(1L);

        MedicalRecordPage result = service.list(animalId, 0, 20);

        assertEquals(1, result.items().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1L, result.total());
    }

    @Test
    void shouldListEveryRecordWhenAnimalIdIsNull() {
        when(repository.findPage(null, 0, 20)).thenReturn(List.of(record(UUID.randomUUID())));
        when(repository.count(null)).thenReturn(1L);

        MedicalRecordPage result = service.list(null, 0, 20);

        assertEquals(1, result.items().size());
    }

    @Test
    void shouldThrowWhenPageIsNegative() {
        assertThrows(InvalidMedicalDataException.class, () -> service.list(null, -1, 20));
    }

    @Test
    void shouldThrowWhenSizeIsBelowOne() {
        assertThrows(InvalidMedicalDataException.class, () -> service.list(null, 0, 0));
    }

    @Test
    void shouldThrowWhenSizeExceedsMaximum() {
        assertThrows(InvalidMedicalDataException.class,
                () -> service.list(null, 0, ListMedicalRecordsUseCase.MAX_PAGE_SIZE + 1));
    }
}
