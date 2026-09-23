package it.zoo.notification.infrastructure.persistence;

import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@ApplicationScoped
public class NotificationPanacheRepository implements NotificationRepository {

    private final EntityManager em;

    public NotificationPanacheRepository(EntityManager em) {
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
}
