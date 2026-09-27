package it.zoo.feeding.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.ConcurrentFeedingPlanUpdateException;
import it.zoo.feeding.domain.model.DeceasedAnimal;
import it.zoo.feeding.domain.model.Feeding;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FeedingPersistenceIT {

    @Inject
    FeedingPlanRepository feedingPlanRepository;

    @Inject
    FeedingRepository feedingRepository;

    @Inject
    DeceasedAnimalRepository deceasedAnimalRepository;

    @Inject
    EntityManager em;

    @BeforeEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM FeedingEntity").executeUpdate();
            em.createQuery("DELETE FROM FeedingPlanEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    private FeedingPlan buildPlan(UUID animalId, LocalDate startedOn, List<LocalTime> times) {
        FeedingPlan plan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500, times, null,
                PlanStatus.ACTIVE, startedOn);
        plan.setCreatedBy("keeper");
        plan.setUpdatedBy("keeper");
        return plan;
    }

    private FeedingPlan savePlan(FeedingPlan plan) {
        return QuarkusTransaction.requiringNew().call(() -> feedingPlanRepository.save(plan));
    }

    private Feeding saveFeeding(Feeding feeding) {
        return QuarkusTransaction.requiringNew().call(() -> feedingRepository.save(feeding));
    }

    @Test
    void shouldRoundTripFeedingTimesInOrder() {
        UUID animalId = UUID.randomUUID();
        List<LocalTime> times = List.of(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(18, 0));
        FeedingPlan saved = savePlan(buildPlan(animalId, LocalDate.of(2026, 9, 1), times));

        FeedingPlan reloaded = feedingPlanRepository.findById(saved.getId()).orElseThrow();

        assertEquals(times, reloaded.getFeedingTimes());
    }

    @Test
    void shouldFindPageFilteredByAnimalIdOrderedByStartedOnDescAndCount() {
        UUID animalA = UUID.randomUUID();
        UUID animalB = UUID.randomUUID();
        List<LocalTime> times = List.of(LocalTime.of(8, 0));

        FeedingPlan oldest = savePlan(buildPlan(animalA, LocalDate.of(2026, 8, 1), times));
        FeedingPlan newest = savePlan(buildPlan(animalA, LocalDate.of(2026, 9, 1), times));
        savePlan(buildPlan(animalB, LocalDate.of(2026, 9, 5), times));

        List<FeedingPlan> page = feedingPlanRepository.findPage(animalA, 0, 10);

        assertEquals(2, page.size());
        assertEquals(newest.getId(), page.get(0).getId());
        assertEquals(oldest.getId(), page.get(1).getId());
        assertEquals(2, feedingPlanRepository.count(animalA));
    }

    @Test
    void shouldFindByAnimalIdAndStatusIn() {
        UUID animalId = UUID.randomUUID();
        List<LocalTime> times = List.of(LocalTime.of(8, 0));

        FeedingPlan active = savePlan(buildPlan(animalId, LocalDate.of(2026, 9, 1), times));

        FeedingPlan suspendedToSave = buildPlan(animalId, LocalDate.of(2026, 9, 2), times);
        suspendedToSave.setStatus(PlanStatus.SUSPENDED);
        FeedingPlan suspended = savePlan(suspendedToSave);

        FeedingPlan ended = buildPlan(animalId, LocalDate.of(2026, 9, 3), times);
        ended.setStatus(PlanStatus.ENDED);
        ended.setEndedOn(LocalDate.of(2026, 9, 10));
        savePlan(ended);

        List<FeedingPlan> result = feedingPlanRepository.findByAnimalIdAndStatusIn(
                animalId, List.of(PlanStatus.ACTIVE, PlanStatus.SUSPENDED));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(active.getId())));
        assertTrue(result.stream().anyMatch(p -> p.getId().equals(suspended.getId())));
    }

    @Test
    void shouldFindFeedingPageOrderedByFedAtDescAndCount() {
        UUID animalId = UUID.randomUUID();
        FeedingPlan plan = savePlan(buildPlan(animalId, LocalDate.of(2026, 9, 1), List.of(LocalTime.of(8, 0))));

        Feeding earliest = saveFeeding(new Feeding(UUID.randomUUID(), plan.getId(),
                Instant.parse("2026-09-01T07:00:00Z"), 100, null, "keeper"));
        Feeding latest = saveFeeding(new Feeding(UUID.randomUUID(), plan.getId(),
                Instant.parse("2026-09-01T18:00:00Z"), 100, null, "keeper"));
        Feeding middle = saveFeeding(new Feeding(UUID.randomUUID(), plan.getId(),
                Instant.parse("2026-09-01T12:00:00Z"), 100, null, "keeper"));

        List<Feeding> page = feedingRepository.findPageByPlanId(plan.getId(), 0, 10);

        assertEquals(3, page.size());
        assertEquals(latest.getId(), page.get(0).getId());
        assertEquals(middle.getId(), page.get(1).getId());
        assertEquals(earliest.getId(), page.get(2).getId());
        assertEquals(3, feedingRepository.countByPlanId(plan.getId()));
    }

    @Test
    void shouldTrackDeceasedAnimalExistsAndSave() {
        UUID animalId = UUID.randomUUID();
        assertFalse(deceasedAnimalRepository.existsByAnimalId(animalId));

        DeceasedAnimal deceasedAnimal = new DeceasedAnimal(animalId, UUID.randomUUID(), Instant.now());
        QuarkusTransaction.requiringNew().run(() -> deceasedAnimalRepository.save(deceasedAnimal));

        assertTrue(deceasedAnimalRepository.existsByAnimalId(animalId));
    }

    @Test
    void shouldThrowConcurrentFeedingPlanUpdateExceptionWhenVersionIsStale() {
        UUID animalId = UUID.randomUUID();
        FeedingPlan initial = savePlan(buildPlan(animalId, LocalDate.of(2026, 9, 1), List.of(LocalTime.of(8, 0))));

        FeedingPlan reloaded = QuarkusTransaction.requiringNew()
                .call(() -> feedingPlanRepository.findById(initial.getId()).orElseThrow());
        reloaded.setNotes("updated notes");
        reloaded.setUpdatedBy("admin");
        QuarkusTransaction.requiringNew().run(() -> feedingPlanRepository.save(reloaded));

        assertThrows(ConcurrentFeedingPlanUpdateException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> feedingPlanRepository.save(initial)));
    }
}
