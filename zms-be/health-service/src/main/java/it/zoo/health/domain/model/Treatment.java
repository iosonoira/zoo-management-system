package it.zoo.health.domain.model;

import it.zoo.health.domain.enums.TreatmentStatus;

import java.time.LocalDate;
import java.util.UUID;

public class Treatment {

    private UUID id;
    private UUID medicalRecordId;
    private String description;
    private TreatmentStatus status;
    private LocalDate startedOn;
    private LocalDate endedOn;
    private String createdBy;
    private String updatedBy;
    private Long version;

    public Treatment() {}

    public Treatment(UUID id, UUID medicalRecordId, String description, TreatmentStatus status) {
        this.id = id;
        this.medicalRecordId = medicalRecordId;
        this.description = description;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getMedicalRecordId() { return medicalRecordId; }
    public String getDescription() { return description; }
    public TreatmentStatus getStatus() { return status; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setMedicalRecordId(UUID medicalRecordId) { this.medicalRecordId = medicalRecordId; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(TreatmentStatus status) { this.status = status; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }

    public boolean canTransitionTo(TreatmentStatus target) {
        if (target == null || target == this.status) {
            return false;
        }
        return switch (this.status) {
            case PRESCRIBED -> target == TreatmentStatus.ACTIVE || target == TreatmentStatus.CANCELLED;
            case ACTIVE -> target == TreatmentStatus.COMPLETED || target == TreatmentStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
