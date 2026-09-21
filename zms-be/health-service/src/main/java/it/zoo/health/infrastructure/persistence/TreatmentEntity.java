package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.enums.TreatmentStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "treatments")
public class TreatmentEntity {

    @Id
    private UUID id;

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TreatmentStatus status;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public TreatmentEntity() {}

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
}
