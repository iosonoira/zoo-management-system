package it.zoo.health.infrastructure.persistence;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_records")
public class MedicalRecordEntity {

    @Id
    private UUID id;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(nullable = false, length = 1000)
    private String diagnosis;

    @Column(name = "examined_on", nullable = false)
    private LocalDate examinedOn;

    @Column(nullable = false, length = 100)
    private String veterinarian;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    public MedicalRecordEntity() {}

    public UUID getId() { return id; }
    public UUID getAnimalId() { return animalId; }
    public String getReason() { return reason; }
    public String getDiagnosis() { return diagnosis; }
    public LocalDate getExaminedOn() { return examinedOn; }
    public String getVeterinarian() { return veterinarian; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public void setExaminedOn(LocalDate examinedOn) { this.examinedOn = examinedOn; }
    public void setVeterinarian(String veterinarian) { this.veterinarian = veterinarian; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }
}
