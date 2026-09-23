package it.zoo.notification.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import it.zoo.notification.domain.exception.InvalidAnimalEventException;
import it.zoo.notification.domain.port.in.HandleAnimalEventCommand;
import it.zoo.notification.domain.port.in.HandleAnimalEventUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumes {@code zoo.animal.events}. The handler ends up calling into
 * {@link HandleAnimalEventUseCase#handle}, which is {@code @Transactional} and does blocking
 * JDBC work through Hibernate ORM — SmallRye Reactive Messaging otherwise runs {@code @Incoming}
 * methods on the Vert.x event loop, so {@code @Blocking} is required here to move execution onto
 * a worker thread. Any exception (malformed JSON, unknown eventType, validation failure) is left
 * to propagate: the channel is configured with {@code failure-strategy=dead-letter-queue}, so
 * SmallRye routes the failing record to {@code zoo.animal.events.dlq} and keeps consuming.
 */
@ApplicationScoped
public class AnimalEventConsumer {

    private static final Logger LOG = Logger.getLogger(AnimalEventConsumer.class);

    private final HandleAnimalEventUseCase useCase;
    private final ObjectMapper objectMapper;

    public AnimalEventConsumer(HandleAnimalEventUseCase useCase, ObjectMapper objectMapper) {
        this.useCase = useCase;
        this.objectMapper = objectMapper;
    }

    @Incoming("animal-events-in")
    @Blocking
    public void consume(String payload) {
        AnimalEventMessage message;
        try {
            message = objectMapper.readValue(payload, AnimalEventMessage.class);
        } catch (JsonProcessingException e) {
            throw new InvalidAnimalEventException("Malformed animal event JSON", e);
        }

        HandleAnimalEventCommand command = AnimalEventMessageMapper.toCommand(message);
        useCase.handle(command);
        LOG.infof("Processed animal event %s (%s) for animal %s",
                command.eventId(), command.eventType(), command.animalId());
    }
}
