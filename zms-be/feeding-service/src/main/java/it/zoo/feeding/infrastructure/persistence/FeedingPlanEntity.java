package it.zoo.feeding.infrastructure.persistence;

import it.zoo.feeding.domain.enums.PlanStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "feeding_plans")
public class FeedingPlanEntity {

    @Id
    private UUID id;

    @Column(name = "animal_id", nullable = false)
    private UUID animalId;

    @Column(nullable = false, length = 100)
    private String food;

    @Column(name = "quantity_grams", nullable = false)
    private int quantityGrams;

    @Convert(converter = LocalTimeListConverter.class)
    @Column(name = "feeding_times", nullable = false, length = 40)
    private List<LocalTime> feedingTimes;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "started_on", nullable = false)
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

    public FeedingPlanEntity() {}

    public UUID getId() { return id; }
    public UUID getAnimalId() { return animalId; }
    public String getFood() { return food; }
    public int getQuantityGrams() { return quantityGrams; }
    public List<LocalTime> getFeedingTimes() { return feedingTimes; }
    public String getNotes() { return notes; }
    public PlanStatus getStatus() { return status; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndedOn() { return endedOn; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }

    public void setId(UUID id) { this.id = id; }
    public void setAnimalId(UUID animalId) { this.animalId = animalId; }
    public void setFood(String food) { this.food = food; }
    public void setQuantityGrams(int quantityGrams) { this.quantityGrams = quantityGrams; }
    public void setFeedingTimes(List<LocalTime> feedingTimes) { this.feedingTimes = feedingTimes; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public void setEndedOn(LocalDate endedOn) { this.endedOn = endedOn; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Long version) { this.version = version; }
}
