package it.zoo.notification.infrastructure.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationQuery;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class NotificationJpaRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-09-20T10:00:00Z");

    private static final UUID ANIMAL_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ANIMAL_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static final UUID ID_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ID_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ID_4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ID_5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private static final NotificationQuery ALL = new NotificationQuery(null, Set.of(), false);

    @Inject
    EntityManager em;

    @Inject
    NotificationRepository repository;

    /*
     * Seed (occurredAt desc, then id):
     *   ID_5  T0+4h  animal B  CRITICAL  open
     *   ID_3  T0+2h  animal A  WARNING   open
     *   ID_4  T0+2h  animal B  INFO      acknowledged
     *   ID_2  T0+1h  animal A  WARNING   acknowledged
     *   ID_1  T0     animal A  INFO      open
     * ID_3 and ID_4 share occurredAt on purpose: they are ordered by id.
     */
    @BeforeEach
    void seed() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM NotificationEntity").executeUpdate();
            em.persist(row(ID_1, ANIMAL_A, Severity.INFO, T0, false));
            em.persist(row(ID_2, ANIMAL_A, Severity.WARNING, T0.plusSeconds(3600), true));
            em.persist(row(ID_3, ANIMAL_A, Severity.WARNING, T0.plusSeconds(7200), false));
            em.persist(row(ID_4, ANIMAL_B, Severity.INFO, T0.plusSeconds(7200), true));
            em.persist(row(ID_5, ANIMAL_B, Severity.CRITICAL, T0.plusSeconds(14400), false));
        });
    }

    private NotificationEntity row(UUID id, UUID animalId, Severity severity, Instant occurredAt, boolean acknowledged) {
        NotificationEntity e = new NotificationEntity();
        e.setId(id);
        e.setEventId(UUID.randomUUID());
        e.setAnimalId(animalId);
        e.setEventType(AnimalEventType.ANIMAL_STATUS_CHANGED);
        e.setSeverity(severity);
        e.setMessage("message " + id);
        e.setOccurredAt(occurredAt);
        e.setCreatedAt(occurredAt);
        if (acknowledged) {
            e.setAcknowledgedBy("zoo-vet");
            e.setAcknowledgedAt(occurredAt.plusSeconds(60));
        }
        return e;
    }

    private List<UUID> idsOf(List<Notification> notifications) {
        return notifications.stream().map(Notification::getId).toList();
    }

    @Test
    void shouldOrderByOccurredAtDescendingThenById() {
        List<Notification> page = repository.findPage(ALL, 0, 10);

        assertEquals(List.of(ID_5, ID_3, ID_4, ID_2, ID_1), idsOf(page));
    }

    @Test
    void shouldFilterByAnimalId() {
        NotificationQuery query = new NotificationQuery(ANIMAL_A, Set.of(), false);

        assertEquals(List.of(ID_3, ID_2, ID_1), idsOf(repository.findPage(query, 0, 10)));
        assertEquals(3L, repository.count(query));
    }

    @Test
    void shouldFilterBySeveritiesWithTwoValues() {
        NotificationQuery query = new NotificationQuery(null, Set.of(Severity.WARNING, Severity.CRITICAL), false);

        assertEquals(List.of(ID_5, ID_3, ID_2), idsOf(repository.findPage(query, 0, 10)));
        assertEquals(3L, repository.count(query));
    }

    @Test
    void shouldFilterBySingleSeverity() {
        NotificationQuery query = new NotificationQuery(null, Set.of(Severity.INFO), false);

        assertEquals(List.of(ID_4, ID_1), idsOf(repository.findPage(query, 0, 10)));
        assertEquals(2L, repository.count(query));
    }

    @Test
    void shouldReturnOnlyOpenNotificationsWhenOpenOnly() {
        NotificationQuery query = new NotificationQuery(null, Set.of(), true);

        assertEquals(List.of(ID_5, ID_3, ID_1), idsOf(repository.findPage(query, 0, 10)));
        assertEquals(3L, repository.count(query));
    }

    @Test
    void shouldCombineAllThreeFilters() {
        NotificationQuery query = new NotificationQuery(ANIMAL_A, Set.of(Severity.WARNING, Severity.CRITICAL), true);

        assertEquals(List.of(ID_3), idsOf(repository.findPage(query, 0, 10)));
        assertEquals(1L, repository.count(query));
    }

    @Test
    void shouldCountEveryRowWithoutFilters() {
        assertEquals(5L, repository.count(ALL));
    }

    @Test
    void shouldReturnTheSecondPage() {
        List<Notification> secondPage = repository.findPage(ALL, 1, 2);

        assertEquals(List.of(ID_4, ID_2), idsOf(secondPage));
        assertEquals(5L, repository.count(ALL));
    }

    @Test
    void shouldReturnAShortLastPage() {
        assertEquals(List.of(ID_1), idsOf(repository.findPage(ALL, 2, 2)));
    }

    @Test
    void shouldReturnEmptyPageBeyondTheLastRow() {
        assertTrue(repository.findPage(ALL, 3, 2).isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenTheOffsetOverflowsAnInt() {
        assertTrue(repository.findPage(ALL, Integer.MAX_VALUE, 100).isEmpty());
    }

    @Test
    void shouldFindById() {
        Optional<Notification> found = repository.findById(ID_2);

        assertTrue(found.isPresent());
        assertEquals(ID_2, found.get().getId());
        assertEquals(ANIMAL_A, found.get().getAnimalId());
        assertEquals(Severity.WARNING, found.get().getSeverity());
        assertEquals("zoo-vet", found.get().getAcknowledgedBy());
    }

    @Test
    void shouldReturnEmptyWhenIdIsUnknown() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
