package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.AnimalStatus;
import it.zoo.animal.domain.enums.Habitat;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animals")
public class AnimalEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String species;

    @Column(nullable = false)
    private boolean dangerous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Habitat habitat;

    @Column(name = "enclosure_id", nullable = false)
    private UUID enclosureId;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnimalStatus status;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public AnimalEntity() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public boolean isDangerous() { return dangerous; }
    public Habitat getHabitat() { return habitat; }
    public UUID getEnclosureId() { return enclosureId; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public AnimalStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSpecies(String species) { this.species = species; }
    public void setDangerous(boolean dangerous) { this.dangerous = dangerous; }
    public void setHabitat(Habitat habitat) { this.habitat = habitat; }
    public void setEnclosureId(UUID enclosureId) { this.enclosureId = enclosureId; }
    public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
    public void setStatus(AnimalStatus status) { this.status = status; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
