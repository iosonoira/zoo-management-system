package it.zoo.animal.domain;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnimalStatusTransitionTest {

    private Animal animalWithStatus(AnimalStatus status) {
        return new Animal(UUID.randomUUID(), "Leo", "Lion", true,
                Habitat.TERRESTRIAL, UUID.randomUUID(), LocalDate.now(), status);
    }

    @Test
    void shouldAllowTransitionFromHealthyToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void shouldAllowTransitionFromHealthyToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void shouldAllowTransitionFromHealthyToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void shouldAllowTransitionFromUnderObservationToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void shouldAllowTransitionFromUnderObservationToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void shouldAllowTransitionFromUnderObservationToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void shouldAllowTransitionFromInTreatmentToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void shouldAllowTransitionFromInTreatmentToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void shouldAllowTransitionFromInTreatmentToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void shouldBlockAllTransitionsWhenDeceased() {
        Animal deceased = animalWithStatus(AnimalStatus.DECEASED);
        assertFalse(deceased.canTransitionTo(AnimalStatus.HEALTHY));
        assertFalse(deceased.canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
        assertFalse(deceased.canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void shouldBlockTransitionToSameStatus() {
        assertFalse(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.HEALTHY));
    }
}
