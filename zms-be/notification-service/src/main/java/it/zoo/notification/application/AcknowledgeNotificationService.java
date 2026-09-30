package it.zoo.notification.application;

import it.zoo.notification.domain.exception.NotificationNotFoundException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.port.in.AcknowledgeNotificationUseCase;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class AcknowledgeNotificationService implements AcknowledgeNotificationUseCase {

    private static final Logger LOG = Logger.getLogger(AcknowledgeNotificationService.class);

    private final NotificationRepository repository;

    public AcknowledgeNotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Notification acknowledge(UUID id, String actor) {
        // A conditional update, not read-then-write: two concurrent acknowledgements cannot both
        // pass a check on acknowledgedAt, because the second UPDATE waits on the row lock and then
        // no longer matches "acknowledgedAt IS NULL".
        if (repository.acknowledge(id, actor, Instant.now())) {
            LOG.info("Notification " + id + " acknowledged by " + actor);
        }
        return repository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }
}
