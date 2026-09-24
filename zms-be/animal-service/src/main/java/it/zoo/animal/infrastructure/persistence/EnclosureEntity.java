package it.zoo.animal.infrastructure.persistence;

import it.zoo.animal.domain.enums.Habitat;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "enclosures")
public class EnclosureEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Habitat habitat;

    public EnclosureEntity() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Habitat getHabitat() { return habitat; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setHabitat(Habitat habitat) { this.habitat = habitat; }
}
