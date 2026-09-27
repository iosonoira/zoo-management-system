package it.zoo.feeding.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedings")
public class FeedingEntity {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "fed_at", nullable = false)
    private Instant fedAt;

    @Column(name = "quantity_grams", nullable = false)
    private int quantityGrams;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    public FeedingEntity() {}

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public Instant getFedAt() { return fedAt; }
    public int getQuantityGrams() { return quantityGrams; }
    public String getNotes() { return notes; }
    public String getRecordedBy() { return recordedBy; }

    public void setId(UUID id) { this.id = id; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public void setFedAt(Instant fedAt) { this.fedAt = fedAt; }
    public void setQuantityGrams(int quantityGrams) { this.quantityGrams = quantityGrams; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setRecordedBy(String recordedBy) { this.recordedBy = recordedBy; }
}
