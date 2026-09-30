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

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "animal_name", length = 100)
    private String name;

    @Column(name = "species", length = 100)
    private String species;

    @Column(name = "dangerous")
    private Boolean dangerous;

    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    @Column(name = "new_status", length = 30)
    private String newStatus;

    @Column(name = "from_enclosure_id")
    private UUID fromEnclosureId;

    @Column(name = "to_enclosure_id")
    private UUID toEnclosureId;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    public NotificationEntity() {}

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getAnimalId() { return animalId; }
    public AnimalEventType getEventType() { return eventType; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPerformedBy() { return performedBy; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public Boolean getDangerous() { return dangerous; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public UUID getFromEnclosureId() { return fromEnclosureId; }
    public UUID getToEnclosureId() { return toEnclosureId; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setEventType(AnimalEventType eventType) { this.eventType = eventType; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setMessage(String message) { this.message = message; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public void setName(String name) { this.name = name; }
    public void setSpecies(String species) { this.species = species; }
    public void setDangerous(Boolean dangerous) { this.dangerous = dangerous; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public void setFromEnclosureId(UUID fromEnclosureId) { this.fromEnclosureId = fromEnclosureId; }
    public void setToEnclosureId(UUID toEnclosureId) { this.toEnclosureId = toEnclosureId; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
