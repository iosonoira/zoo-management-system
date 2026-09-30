package it.zoo.notification.infrastructure.persistence;

import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationQuery;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationJpaRepository implements NotificationRepository {

    private final EntityManager em;

    public NotificationJpaRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = NotificationEntityMapper.toEntity(notification);
        em.persist(entity);
        em.flush();
        return NotificationEntityMapper.toDomain(entity);
    }

    @Override
    public boolean existsByEventId(UUID eventId) {
        Long count = em.createQuery(
                        "SELECT COUNT(n) FROM NotificationEntity n WHERE n.eventId = :eventId", Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(em.find(NotificationEntity.class, id))
                .map(NotificationEntityMapper::toDomain);
    }

    @Override
    public boolean acknowledge(UUID id, String actor, Instant at) {
        // Bulk update: it bypasses the persistence context, so no NotificationEntity for this id
        // may be loaded in the same transaction before it (findById afterwards reads the new row).
        int updated = em.createQuery(
                        "UPDATE NotificationEntity n SET n.acknowledgedBy = :actor, n.acknowledgedAt = :at "
                                + "WHERE n.id = :id AND n.acknowledgedAt IS NULL")
                .setParameter("actor", actor)
                .setParameter("at", at)
                .setParameter("id", id)
                .executeUpdate();
        return updated == 1;
    }

    @Override
    public List<Notification> findPage(NotificationQuery query, int page, int size) {
        // setFirstResult takes an int: an offset past it cannot hold rows, and page * size would overflow.
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) {
            return List.of();
        }
        TypedQuery<NotificationEntity> jpql = em.createQuery(
                "SELECT n FROM NotificationEntity n" + whereOf(query) + " ORDER BY n.occurredAt DESC, n.id",
                NotificationEntity.class);
        bind(jpql, query);
        return jpql.setFirstResult((int) offset)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(NotificationEntityMapper::toDomain)
                .toList();
    }

    @Override
    public long count(NotificationQuery query) {
        TypedQuery<Long> jpql = em.createQuery(
                "SELECT COUNT(n) FROM NotificationEntity n" + whereOf(query), Long.class);
        bind(jpql, query);
        return jpql.getSingleResult();
    }

    private static String whereOf(NotificationQuery query) {
        List<String> conditions = new ArrayList<>();
        if (query.animalId() != null) {
            conditions.add("n.animalId = :animalId");
        }
        if (!query.severities().isEmpty()) {
            conditions.add("n.severity IN :severities");
        }
        if (query.openOnly()) {
            conditions.add("n.acknowledgedAt IS NULL");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void bind(TypedQuery<?> jpql, NotificationQuery query) {
        if (query.animalId() != null) {
            jpql.setParameter("animalId", query.animalId());
        }
        if (!query.severities().isEmpty()) {
            jpql.setParameter("severities", query.severities());
        }
    }
}
