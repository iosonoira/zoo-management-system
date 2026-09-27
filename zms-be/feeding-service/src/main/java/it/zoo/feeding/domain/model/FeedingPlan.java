package it.zoo.feeding.domain.model;

import it.zoo.feeding.domain.enums.PlanStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class FeedingPlan {

    private UUID id;
    private UUID animalId;
    private String food;
    private int quantityGrams;
    private List<LocalTime> feedingTimes;
    private String notes;
    private PlanStatus status;
    private LocalDate startedOn;
    private LocalDate endedOn;
    private String createdBy;
    private String updatedBy;
    private Long version;

    public FeedingPlan() {}

    public FeedingPlan(UUID id, UUID animalId, String food, int quantityGrams, List<LocalTime> feedingTimes, String notes, PlanStatus status, LocalDate startedOn) {
        this.id = id;
        this.animalId = animalId;
        this.food = food;
        this.quantityGrams = quantityGrams;
        this.feedingTimes = feedingTimes;
        this.notes = notes;
        this.status = status;
        this.startedOn = startedOn;
    }

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

    public boolean canTransitionTo(PlanStatus target) {
        if (target == null || target == this.status) {
            return false;
        }
        return switch (this.status) {
            case ACTIVE -> target == PlanStatus.SUSPENDED || target == PlanStatus.ENDED;
            case SUSPENDED -> target == PlanStatus.ACTIVE || target == PlanStatus.ENDED;
            case ENDED -> false;
        };
    }
}
