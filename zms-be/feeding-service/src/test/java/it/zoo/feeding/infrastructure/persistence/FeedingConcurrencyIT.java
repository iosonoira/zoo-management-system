package it.zoo.feeding.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.feeding.domain.enums.PlanStatus;
import it.zoo.feeding.domain.exception.AnimalDeceasedException;
import it.zoo.feeding.domain.exception.FeedingPlanNotActiveException;
import it.zoo.feeding.domain.model.DeceasedAnimal;
import it.zoo.feeding.domain.model.FeedingPlan;
import it.zoo.feeding.domain.port.in.CreateFeedingPlanCommand;
import it.zoo.feeding.domain.port.in.CreateFeedingPlanUseCase;
import it.zoo.feeding.domain.port.in.HandleAnimalEventCommand;
import it.zoo.feeding.domain.port.in.HandleAnimalEventUseCase;
import it.zoo.feeding.domain.port.in.RecordFeedingCommand;
import it.zoo.feeding.domain.port.in.RecordFeedingUseCase;
import it.zoo.feeding.domain.port.out.AnimalLock;
import it.zoo.feeding.domain.port.out.DeceasedAnimalRepository;
import it.zoo.feeding.domain.port.out.FeedingPlanRepository;
import it.zoo.feeding.domain.port.out.FeedingRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises the locking added to close the race conditions found in code review:
 * a per-animal advisory lock ({@link AnimalLock}) and pessimistic row locks on
 * {@code feeding_plans} ({@link FeedingPlanRepository#findByIdForUpdate},
 * {@link FeedingPlanRepository#findByAnimalIdAndStatusInForUpdate}).
 *
 * Each test uses two {@link CountDownLatch}es and an {@link ExecutorService} of two threads to
 * make the interleaving deterministic: thread A takes a lock and parks, thread B is only started
 * once A signals it holds the lock. Blocking is proved with a bounded {@code Future#get} timeout
 * that is expected to throw {@link TimeoutException}, never with {@code Thread.sleep} as the
 * synchronization primitive.
 */
@QuarkusTest
class FeedingConcurrencyIT {

    private static final long BLOCK_CHECK_TIMEOUT_MS = 500;
    private static final long COMPLETION_TIMEOUT_S = 15;
    private static final long SIGNAL_TIMEOUT_S = 15;

    @Inject
    CreateFeedingPlanUseCase createFeedingPlanUseCase;

    @Inject
    RecordFeedingUseCase recordFeedingUseCase;

    @Inject
    HandleAnimalEventUseCase handleAnimalEventUseCase;

    @Inject
    AnimalLock animalLock;

    @Inject
    DeceasedAnimalRepository deceasedAnimalRepository;

    @Inject
    FeedingPlanRepository feedingPlanRepository;

    @Inject
    FeedingRepository feedingRepository;

    @Inject
    EntityManager em;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM FeedingEntity").executeUpdate();
            em.createQuery("DELETE FROM FeedingPlanEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private FeedingPlan buildActivePlan(UUID animalId) {
        FeedingPlan plan = new FeedingPlan(UUID.randomUUID(), animalId, "Hay", 500,
                List.of(LocalTime.of(8, 0)), null, PlanStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        plan.setCreatedBy("keeper");
        plan.setUpdatedBy("keeper");
        return plan;
    }

    private FeedingPlan savePlan(FeedingPlan plan) {
        return QuarkusTransaction.requiringNew().call(() -> feedingPlanRepository.save(plan));
    }

    // (a) A holds the per-animal advisory lock while inserting deceased_animals; B's plan
    // creation for the same animal must wait for A, then see the animal as deceased.
    @Test
    void shouldBlockPlanCreationWhileConcurrentDeceasedEventHoldsTheAnimalLock() throws Exception {
        UUID animalId = UUID.randomUUID();
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                animalLock.acquire(animalId);
                deceasedAnimalRepository.save(new DeceasedAnimal(animalId, UUID.randomUUID(), Instant.now()));
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        CreateFeedingPlanCommand cmd = new CreateFeedingPlanCommand(
                animalId, "Hay", 500, List.of(LocalTime.of(9, 0)), null, "keeper");
        Future<FeedingPlan> threadB = executor.submit(() -> createFeedingPlanUseCase.create(cmd));

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "plan creation should still be blocked on the animal lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS));
        assertInstanceOf(AnimalDeceasedException.class, ex.getCause());
    }

    // (b) A holds the row lock on an ACTIVE plan while moving it to ENDED; B's feeding record on
    // the same plan must wait for A, then see it as no longer ACTIVE.
    @Test
    void shouldBlockRecordFeedingWhileConcurrentStatusChangeHoldsThePlanRowLock() throws Exception {
        UUID animalId = UUID.randomUUID();
        FeedingPlan plan = savePlan(buildActivePlan(animalId));
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                FeedingPlan locked = feedingPlanRepository.findByIdForUpdate(plan.getId()).orElseThrow();
                locked.setStatus(PlanStatus.ENDED);
                locked.setEndedOn(LocalDate.now());
                locked.setUpdatedBy("vet");
                feedingPlanRepository.save(locked);
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        RecordFeedingCommand cmd = new RecordFeedingCommand(plan.getId(), Instant.now(), 500, null, "keeper");
        Future<?> threadB = executor.submit(() -> recordFeedingUseCase.record(cmd));

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "recording a feeding should still be blocked on the plan row lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS));
        assertInstanceOf(FeedingPlanNotActiveException.class, ex.getCause());

        assertEquals(0, feedingRepository.countByPlanId(plan.getId()));
    }

    // (c) A holds the row lock on a plan it is suspending; B's DECEASED event handling for the
    // same animal must wait for A, then still end the (now SUSPENDED) plan.
    @Test
    void shouldEndSuspendedPlanAfterConcurrentStatusChangeReleasesThePlanRowLock() throws Exception {
        UUID animalId = UUID.randomUUID();
        FeedingPlan plan = savePlan(buildActivePlan(animalId));
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                FeedingPlan locked = feedingPlanRepository.findByIdForUpdate(plan.getId()).orElseThrow();
                locked.setStatus(PlanStatus.SUSPENDED);
                locked.setUpdatedBy("vet");
                feedingPlanRepository.save(locked);
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        HandleAnimalEventCommand cmd = new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, Instant.now(), "zoo-vet", "DECEASED");
        Future<?> threadB = executor.submit(() -> {
            handleAnimalEventUseCase.handle(cmd);
            return null;
        });

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "handling the DECEASED event should still be blocked on the plan row lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);
        threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        PlanStatus finalStatus = QuarkusTransaction.requiringNew()
                .call(() -> em.find(FeedingPlanEntity.class, plan.getId()).getStatus());
        assertEquals(PlanStatus.ENDED, finalStatus);
        assertTrue(deceasedAnimalRepository.existsByAnimalId(animalId));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS)) {
                fail("timed out waiting to be released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting to be released");
        }
    }
}
