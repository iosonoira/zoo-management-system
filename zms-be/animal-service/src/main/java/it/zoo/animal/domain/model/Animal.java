package it.zoo.animal.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public class Animal {

    private UUID id;
    private String name;
    private String species;
    private boolean dangerous;
    private Habitat habitat;
    private UUID enclosureId;
    private LocalDate arrivalDate;
    private AnimalStatus status;

    public Animal() {}

    public Animal(UUID id, String name, String species, boolean dangerous,
                  Habitat habitat, UUID enclosureId, LocalDate arrivalDate,
                  AnimalStatus status) {
        this.id = id;
        this.name = name;
        this.species = species;
        this.dangerous = dangerous;
        this.habitat = habitat;
        this.enclosureId = enclosureId;
        this.arrivalDate = arrivalDate;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSpecies() { return species; }
    public boolean isDangerous() { return dangerous; }
    public Habitat getHabitat() { return habitat; }
    public UUID getEnclosureId() { return enclosureId; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public AnimalStatus getStatus() { return status; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSpecies(String species) { this.species = species; }
    public void setDangerous(boolean dangerous) { this.dangerous = dangerous; }
    public void setHabitat(Habitat habitat) { this.habitat = habitat; }
    public void setEnclosureId(UUID enclosureId) { this.enclosureId = enclosureId; }
    public void setArrivalDate(LocalDate arrivalDate) { this.arrivalDate = arrivalDate; }
    public void setStatus(AnimalStatus status) { this.status = status; }

    public boolean canTransitionTo(AnimalStatus target) {
        if (this.status == AnimalStatus.DECEASED) {
            return false;
        }
        return this.status != target;
    }
}