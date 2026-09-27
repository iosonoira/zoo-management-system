package it.zoo.feeding.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Feeding {

    private UUID id;
    private UUID planId;
    private Instant fedAt;
    private int quantityGrams;
    private String notes;
    private String recordedBy;

    public Feeding() {}

    public Feeding(UUID id, UUID planId, Instant fedAt, int quantityGrams, String notes, String recordedBy) {
        this.id = id;
        this.planId = planId;
        this.fedAt = fedAt;
        this.quantityGrams = quantityGrams;
        this.notes = notes;
        this.recordedBy = recordedBy;
    }

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
