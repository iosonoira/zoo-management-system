package it.zoo.notification.domain.model;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;

import java.time.Instant;
import java.util.UUID;

public class Notification {

    private UUID id;
    private UUID eventId;
    private UUID animalId;
    private AnimalEventType eventType;
    private Severity severity;
    private String message;
    private Instant occurredAt;
    private Instant createdAt;

    public Notification() {}

    public Notification(UUID id, UUID eventId, UUID animalId, AnimalEventType eventType,
                       Severity severity, String message, Instant occurredAt, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.animalId = animalId;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getAnimalId() { return animalId; }
    public AnimalEventType getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setEventType(AnimalEventType eventType) { this.eventType = eventType; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setMessage(String message) { this.message = message; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
