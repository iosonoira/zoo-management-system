package it.zoo.animal.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.zoo.animal.domain.event.AnimalEvent;
import it.zoo.animal.domain.port.out.AnimalEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OutboxAnimalEventPublisher implements AnimalEventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxAnimalEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void publish(AnimalEvent event) {
        AnimalEventMessage message = AnimalEventMessageMapper.toMessage(event);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize animal event " + event.eventId(), e);
        }

        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setId(event.eventId());
        entity.setAggregateId(event.animalId());
        entity.setEventType(message.eventType());
        entity.setPayload(payload);
        entity.setOccurredAt(event.occurredAt());
        entity.setPublishedAt(null);
        entity.setAttempts(0);

        repository.persist(entity);
    }
}
