package it.zoo.health.application;

import it.zoo.health.domain.exception.InvalidMedicalDataException;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.port.in.CreateMedicalRecordCommand;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
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
class CreateMedicalRecordServiceTest {

    @Mock
    MedicalRecordRepository repository;

    @InjectMocks
    CreateMedicalRecordService service;

    private CreateMedicalRecordCommand validCommand() {
        return new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");
    }

    @Test
    void shouldCreateMedicalRecord() {
        when(repository.save(any(MedicalRecord.class))).thenAnswer(i -> i.getArgument(0));
        CreateMedicalRecordCommand cmd = validCommand();

        MedicalRecord result = service.create(cmd);

        assertNotNull(result.getId());
        assertEquals(cmd.animalId(), result.getAnimalId());
        assertEquals("Limping", result.getReason());
        assertEquals("Sprained paw", result.getDiagnosis());
        assertEquals("Dr Rossi", result.getVeterinarian());
        assertEquals("vet", result.getCreatedBy());
        assertEquals("vet", result.getUpdatedBy());
    }

    @Test
    void shouldThrowWhenPerformedByIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenAnimalIdIsNull() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                null, "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenReasonIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "  ", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenDiagnosisIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "",
                LocalDate.of(2026, 9, 1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenVeterinarianIsBlank() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenExaminedOnIsNull() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                null, "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }

    @Test
    void shouldThrowWhenExaminedOnIsInTheFuture() {
        CreateMedicalRecordCommand cmd = new CreateMedicalRecordCommand(
                UUID.randomUUID(), "Limping", "Sprained paw",
                LocalDate.now().plusDays(1), "Dr Rossi", "vet");

        assertThrows(InvalidMedicalDataException.class, () -> service.create(cmd));
    }
}
