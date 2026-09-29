package it.zoo.health.infrastructure.persistence;

import it.zoo.health.domain.port.out.AnimalLock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@ApplicationScoped
public class PostgresAnimalLock implements AnimalLock {

    private final EntityManager em;

    public PostgresAnimalLock(EntityManager em) {
        this.em = em;
    }

    @Override
    public void acquire(UUID animalId) {
        // Two ids folding to the same key only wait on each other; they never run unserialized.
        long key = animalId.getMostSignificantBits() ^ animalId.getLeastSignificantBits();
        em.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(:key) AS text)")
                .setParameter("key", key)
                .getSingleResult();
    }
}
