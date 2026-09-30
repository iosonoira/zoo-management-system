package it.zoo.health.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.health.domain.enums.TreatmentStatus;
import it.zoo.health.domain.exception.AnimalDeceasedException;
import it.zoo.health.domain.exception.InvalidTreatmentStatusTransitionException;
import it.zoo.health.domain.model.DeceasedAnimal;
import it.zoo.health.domain.model.MedicalRecord;
import it.zoo.health.domain.model.Treatment;
import it.zoo.health.domain.port.in.HandleAnimalEventCommand;
import it.zoo.health.domain.port.in.HandleAnimalEventUseCase;
import it.zoo.health.domain.port.in.PrescribeTreatmentCommand;
import it.zoo.health.domain.port.in.PrescribeTreatmentUseCase;
import it.zoo.health.domain.port.in.UpdateTreatmentStatusUseCase;
import it.zoo.health.domain.port.out.AnimalLock;
import it.zoo.health.domain.port.out.DeceasedAnimalRepository;
import it.zoo.health.domain.port.out.MedicalRecordRepository;
import it.zoo.health.domain.port.out.TreatmentRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
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
 * Exercises the per-animal advisory lock ({@link AnimalLock}) that the DECEASED event handler,
 * prescribing and every treatment status change take before reading treatments.
 *
 * Same technique as {@code FeedingConcurrencyIT}: thread A takes the lock, writes and parks;
 * thread B is started only once A signals it holds the lock. Blocking is proved with a bounded
 * {@code Future#get} timeout expected to throw {@link TimeoutException}, never with
 * {@code Thread.sleep} as the synchronization primitive.
 */
@QuarkusTest
class HealthConcurrencyIT {

    private static final long BLOCK_CHECK_TIMEOUT_MS = 500;
    private static final long COMPLETION_TIMEOUT_S = 15;
    private static final long SIGNAL_TIMEOUT_S = 15;

    @Inject
    PrescribeTreatmentUseCase prescribeTreatmentUseCase;

    @Inject
    UpdateTreatmentStatusUseCase updateTreatmentStatusUseCase;

    @Inject
    HandleAnimalEventUseCase handleAnimalEventUseCase;

    @Inject
    AnimalLock animalLock;

    @Inject
    DeceasedAnimalRepository deceasedAnimalRepository;

    @Inject
    MedicalRecordRepository medicalRecordRepository;

    @Inject
    TreatmentRepository treatmentRepository;

    @Inject
    EntityManager em;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM TreatmentEntity").executeUpdate();
            em.createQuery("DELETE FROM MedicalRecordEntity").executeUpdate();
            em.createQuery("DELETE FROM DeceasedAnimalEntity").executeUpdate();
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private MedicalRecord saveRecord(UUID animalId) {
        MedicalRecord record = new MedicalRecord(UUID.randomUUID(), animalId, "Limping", "Sprained paw",
                LocalDate.of(2026, 9, 1), "Dr Rossi");
        record.setCreatedBy("vet");
        record.setUpdatedBy("vet");
        return QuarkusTransaction.requiringNew().call(() -> medicalRecordRepository.save(record));
    }

    private Treatment saveTreatment(UUID recordId, TreatmentStatus status) {
        Treatment treatment = new Treatment(UUID.randomUUID(), recordId, "Antibiotics", status);
        if (status == TreatmentStatus.ACTIVE) {
            treatment.setStartedOn(LocalDate.of(2026, 9, 1));
        }
        treatment.setCreatedBy("vet");
        treatment.setUpdatedBy("vet");
        return QuarkusTransaction.requiringNew().call(() -> treatmentRepository.save(treatment));
    }

    private TreatmentStatus statusOf(UUID treatmentId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> em.find(TreatmentEntity.class, treatmentId).getStatus());
    }

    private HandleAnimalEventCommand deceasedEvent(UUID animalId) {
        return new HandleAnimalEventCommand(
                UUID.randomUUID(), "ANIMAL_STATUS_CHANGED", animalId, Instant.now(), "zoo-vet", "DECEASED");
    }

    // (a) A holds the animal lock while recording the death; B's prescription on a record of the
    // same animal must wait for A, then see the animal as deceased.
    @Test
    void shouldBlockPrescribingWhileConcurrentDeceasedEventHoldsTheAnimalLock() throws Exception {
        UUID animalId = UUID.randomUUID();
        MedicalRecord record = saveRecord(animalId);
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

        PrescribeTreatmentCommand cmd = new PrescribeTreatmentCommand(record.getId(), "Antibiotics", "vet");
        Future<Treatment> threadB = executor.submit(() -> prescribeTreatmentUseCase.prescribe(cmd));

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "prescribing should still be blocked on the animal lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS));
        assertInstanceOf(AnimalDeceasedException.class, ex.getCause());
    }

    // (b) A holds the animal lock while cancelling a PRESCRIBED treatment, as the DECEASED handler
    // does; B's move of the same treatment to ACTIVE must wait for A, then see it CANCELLED and
    // fail on the transition, not on a stale @Version.
    @Test
    void shouldRejectStartingATreatmentCancelledByAConcurrentDeceasedEvent() throws Exception {
        UUID animalId = UUID.randomUUID();
        Treatment treatment = saveTreatment(saveRecord(animalId).getId(), TreatmentStatus.PRESCRIBED);
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                animalLock.acquire(animalId);
                Treatment locked = treatmentRepository.findById(treatment.getId()).orElseThrow();
                locked.setStatus(TreatmentStatus.CANCELLED);
                locked.setEndedOn(LocalDate.now());
                locked.setUpdatedBy("zoo-vet");
                treatmentRepository.save(locked);
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        Future<Treatment> threadB = executor.submit(
                () -> updateTreatmentStatusUseCase.updateStatus(treatment.getId(), TreatmentStatus.ACTIVE, "vet"));

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "starting the treatment should still be blocked on the animal lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS));
        assertInstanceOf(InvalidTreatmentStatusTransitionException.class, ex.getCause());
        assertEquals(TreatmentStatus.CANCELLED, statusOf(treatment.getId()));
    }

    // (c) A holds the animal lock while completing an ACTIVE treatment, as a vet's status change
    // does; B's DECEASED event for the same animal must wait for A, then commit without an
    // optimistic-lock failure, record the death and leave the COMPLETED treatment as it is.
    @Test
    void shouldHandleDeceasedEventAfterConcurrentStatusChangeReleasesTheAnimalLock() throws Exception {
        UUID animalId = UUID.randomUUID();
        UUID recordId = saveRecord(animalId).getId();
        Treatment completedByVet = saveTreatment(recordId, TreatmentStatus.ACTIVE);
        Treatment stillOpen = saveTreatment(recordId, TreatmentStatus.PRESCRIBED);
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                animalLock.acquire(animalId);
                Treatment locked = treatmentRepository.findById(completedByVet.getId()).orElseThrow();
                locked.setStatus(TreatmentStatus.COMPLETED);
                locked.setEndedOn(LocalDate.now());
                locked.setUpdatedBy("vet");
                treatmentRepository.save(locked);
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        HandleAnimalEventCommand cmd = deceasedEvent(animalId);
        Future<?> threadB = executor.submit(() -> {
            handleAnimalEventUseCase.handle(cmd);
            return null;
        });

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "handling the DECEASED event should still be blocked on the animal lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);
        threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        assertTrue(deceasedAnimalRepository.existsByAnimalId(animalId));
        assertEquals(TreatmentStatus.COMPLETED, statusOf(completedByVet.getId()));
        assertEquals(TreatmentStatus.CANCELLED, statusOf(stillOpen.getId()));
    }

    // (d) A holds the animal lock while prescribing (before the death is known); B's DECEASED
    // event must wait for A's commit, then cancel the new treatment too.
    @Test
    void shouldCancelATreatmentPrescribedJustBeforeTheDeceasedEvent() throws Exception {
        UUID animalId = UUID.randomUUID();
        UUID recordId = saveRecord(animalId).getId();
        UUID prescribedId = UUID.randomUUID();
        CountDownLatch lockHeldByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() -> {
            QuarkusTransaction.requiringNew().run(() -> {
                animalLock.acquire(animalId);
                Treatment treatment = new Treatment(prescribedId, recordId, "Antibiotics", TreatmentStatus.PRESCRIBED);
                treatment.setCreatedBy("vet");
                treatment.setUpdatedBy("vet");
                treatmentRepository.save(treatment);
                lockHeldByA.countDown();
                awaitLatch(releaseA);
            });
        });

        assertTrue(lockHeldByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS), "thread A never signalled it holds the lock");

        HandleAnimalEventCommand cmd = deceasedEvent(animalId);
        Future<?> threadB = executor.submit(() -> {
            handleAnimalEventUseCase.handle(cmd);
            return null;
        });

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "handling the DECEASED event should still be blocked on the animal lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);
        threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        assertEquals(TreatmentStatus.CANCELLED, statusOf(prescribedId));
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
