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
    private String performedBy;
    private String name;
    private String species;
    private Boolean dangerous;
    private String previousStatus;
    private String newStatus;
    private UUID fromEnclosureId;
    private UUID toEnclosureId;
    private String acknowledgedBy;
    private Instant acknowledgedAt;

    public Notification() {}

    public Notification(UUID id, UUID eventId, UUID animalId, AnimalEventType eventType,
                       Severity severity, String message, Instant occurredAt, Instant createdAt,
                       String performedBy, String name, String species, Boolean dangerous,
                       String previousStatus, String newStatus,
                       UUID fromEnclosureId, UUID toEnclosureId,
                       String acknowledgedBy, Instant acknowledgedAt) {
        this.id = id;
        this.eventId = eventId;
        this.animalId = animalId;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.performedBy = performedBy;
        this.name = name;
        this.species = species;
        this.dangerous = dangerous;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.fromEnclosureId = fromEnclosureId;
        this.toEnclosureId = toEnclosureId;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
    }

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
