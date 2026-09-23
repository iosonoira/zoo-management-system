package it.zoo.notification.application;

import it.zoo.notification.domain.exception.InvalidAnimalEventException;
import it.zoo.notification.domain.model.Notification;
import it.zoo.notification.domain.model.NotificationRule;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;
import it.zoo.notification.domain.port.in.HandleAnimalEventUseCase;
import it.zoo.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class HandleAnimalEventService implements HandleAnimalEventUseCase {

    private static final Logger LOG = Logger.getLogger(HandleAnimalEventService.class);

    private final NotificationRepository repository;

    public HandleAnimalEventService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void handle(HandleAnimalEventCommand command) {
        if (command.eventId() == null) {
            throw new InvalidAnimalEventException("Event ID must not be null");
        }
        if (command.eventType() == null) {
            throw new InvalidAnimalEventException("Event type must not be null");
        }
        if (command.animalId() == null) {
            throw new InvalidAnimalEventException("Animal ID must not be null");
        }
        if (command.occurredAt() == null) {
            throw new InvalidAnimalEventException("Occurred at must not be null");
        }

        if (repository.existsByEventId(command.eventId())) {
            return;
        }

        Notification notification = new Notification(
            UUID.randomUUID(),
            command.eventId(),
            command.animalId(),
            command.eventType(),
            NotificationRule.severityOf(command),
            NotificationRule.messageOf(command),
            command.occurredAt(),
            Instant.now()
        );

        repository.save(notification);
        LOG.info(notification.getSeverity() + ": " + notification.getMessage());
    }
}
