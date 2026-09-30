package it.zoo.notification.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.exception.NotificationNotFoundException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.port.in.AcknowledgeNotificationUseCase;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The first acknowledgement wins, also when two arrive at the same time. Same technique as
 * {@code HealthConcurrencyIT}: thread A updates the row and parks with its transaction open;
 * thread B is started only once A signals, and its blocking on A's row lock is proved with a
 * bounded {@code Future#get} timeout, never with {@code Thread.sleep}.
 */
@QuarkusTest
class NotificationAcknowledgementIT {

    private static final long BLOCK_CHECK_TIMEOUT_MS = 500;
    private static final long COMPLETION_TIMEOUT_S = 15;
    private static final long SIGNAL_TIMEOUT_S = 15;

    @Inject
    AcknowledgeNotificationUseCase acknowledgeNotification;

    @Inject
    NotificationRepository repository;

    @Inject
    EntityManager em;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        QuarkusTransaction.requiringNew().run(() ->
                em.createQuery("DELETE FROM NotificationEntity").executeUpdate());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private UUID saveOpenNotification() {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            NotificationEntity e = new NotificationEntity();
            e.setId(id);
            e.setEventId(UUID.randomUUID());
            e.setAnimalId(UUID.randomUUID());
            e.setEventType(AnimalEventType.ANIMAL_STATUS_CHANGED);
            e.setSeverity(Severity.CRITICAL);
            e.setMessage("Leo (Lion) status changed from HEALTHY to DECEASED");
            e.setOccurredAt(Instant.parse("2026-09-30T08:00:00Z"));
            e.setCreatedAt(Instant.parse("2026-09-30T08:00:01Z"));
            em.persist(e);
        });
        return id;
    }

    private NotificationEntity rowOf(UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(NotificationEntity.class, id));
    }

    @Test
    void shouldStoreTheActorAndTimeOfTheAcknowledgement() {
        UUID id = saveOpenNotification();

        Notification result = acknowledgeNotification.acknowledge(id, "vet.bianchi");

        assertEquals("vet.bianchi", result.getAcknowledgedBy());
        NotificationEntity row = rowOf(id);
        assertEquals("vet.bianchi", row.getAcknowledgedBy());
        assertEquals(result.getAcknowledgedAt(), row.getAcknowledgedAt());
    }

    @Test
    void shouldKeepTheFirstAcknowledgementWhenAcknowledgedAgain() {
        UUID id = saveOpenNotification();
        Notification first = acknowledgeNotification.acknowledge(id, "vet.bianchi");

        Notification second = acknowledgeNotification.acknowledge(id, "keeper.conti");

        assertEquals("vet.bianchi", second.getAcknowledgedBy());
        assertEquals(first.getAcknowledgedAt(), second.getAcknowledgedAt());
        assertEquals("vet.bianchi", rowOf(id).getAcknowledgedBy());
    }

    @Test
    void shouldThrowWhenAcknowledgingAnUnknownNotification() {
        assertThrows(NotificationNotFoundException.class,
                () -> acknowledgeNotification.acknowledge(UUID.randomUUID(), "vet.bianchi"));
    }

    // A holds the row lock of its uncommitted acknowledgement; B's acknowledgement must wait for A,
    // then find the row already acknowledged and return A's acknowledgement unchanged.
    @Test
    void shouldLetTheFirstOfTwoConcurrentAcknowledgementsWin() throws Exception {
        UUID id = saveOpenNotification();
        Instant atOfA = Instant.parse("2026-09-30T09:00:00Z");
        CountDownLatch rowLockedByA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);

        Future<?> threadA = executor.submit(() ->
                QuarkusTransaction.requiringNew().run(() -> {
                    assertTrue(repository.acknowledge(id, "vet.bianchi", atOfA));
                    rowLockedByA.countDown();
                    awaitLatch(releaseA);
                }));

        assertTrue(rowLockedByA.await(SIGNAL_TIMEOUT_S, TimeUnit.SECONDS),
                "thread A never signalled it acknowledged the row");

        Future<Notification> threadB = executor.submit(() ->
                acknowledgeNotification.acknowledge(id, "keeper.conti"));

        assertThrows(TimeoutException.class, () -> threadB.get(BLOCK_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                "the second acknowledgement should still be blocked on the row lock held by thread A");

        releaseA.countDown();
        threadA.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);

        Notification resultOfB = threadB.get(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS);
        assertEquals("vet.bianchi", resultOfB.getAcknowledgedBy());
        assertEquals(atOfA, resultOfB.getAcknowledgedAt());

        NotificationEntity row = rowOf(id);
        assertEquals("vet.bianchi", row.getAcknowledgedBy());
        assertEquals(atOfA, row.getAcknowledgedAt());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(COMPLETION_TIMEOUT_S, TimeUnit.SECONDS)) {
                fail("latch was never released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the latch");
        }
    }
}
