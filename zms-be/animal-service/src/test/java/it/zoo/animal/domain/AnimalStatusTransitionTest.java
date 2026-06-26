package it.zoo.animal.domain;

import it.zoo.animal.domain.model.Animal;
import it.zoo.animal.domain.model.AnimalStatus;
import it.zoo.animal.domain.model.Habitat;
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
    void healthyCanTransitionToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void healthyCanTransitionToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void healthyCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void underObservationCanTransitionToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void underObservationCanTransitionToInTreatment() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void underObservationCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.UNDER_OBSERVATION).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void inTreatmentCanTransitionToHealthy() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.HEALTHY));
    }

    @Test
    void inTreatmentCanTransitionToUnderObservation() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
    }

    @Test
    void inTreatmentCanTransitionToDeceased() {
        assertTrue(animalWithStatus(AnimalStatus.IN_TREATMENT).canTransitionTo(AnimalStatus.DECEASED));
    }

    @Test
    void deceasedIsTerminal() {
        Animal deceased = animalWithStatus(AnimalStatus.DECEASED);
        assertFalse(deceased.canTransitionTo(AnimalStatus.HEALTHY));
        assertFalse(deceased.canTransitionTo(AnimalStatus.UNDER_OBSERVATION));
        assertFalse(deceased.canTransitionTo(AnimalStatus.IN_TREATMENT));
    }

    @Test
    void cannotTransitionToSameStatus() {
        assertFalse(animalWithStatus(AnimalStatus.HEALTHY).canTransitionTo(AnimalStatus.HEALTHY));
    }
}
