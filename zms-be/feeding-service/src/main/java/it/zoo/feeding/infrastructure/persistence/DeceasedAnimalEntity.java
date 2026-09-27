package it.zoo.feeding.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deceased_animals")
public class DeceasedAnimalEntity {

    @Id
    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public DeceasedAnimalEntity() {}

    public UUID getAnimalId() { return animalId; }
    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }

    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
