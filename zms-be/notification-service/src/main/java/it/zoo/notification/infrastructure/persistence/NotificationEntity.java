package it.zoo.notification.infrastructure.persistence;

import it.zoo.notification.domain.enums.AnimalEventType;
import it.zoo.notification.domain.enums.Severity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AnimalEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationEntity() {}

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
