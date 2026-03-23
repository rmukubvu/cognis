package io.cognis.vertical.livestock.store;

import io.cognis.vertical.livestock.model.Animal;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistent store for livestock animals, keyed by tag/EUI id.
 */
public interface AnimalStore {

    /** Inserts or updates the given animal. */
    void upsert(Animal animal) throws IOException;

    /** Returns all animals in the store. */
    List<Animal> findAll() throws IOException;

    /** Returns the animal with the given id, or empty if not found. */
    Optional<Animal> findById(String id) throws IOException;

    /** Returns all animals whose {@code insideGeofence} flag is {@code false}. */
    List<Animal> findOutsideGeofence() throws IOException;

    /**
     * Returns all animals whose {@code lastSeen} timestamp is before {@code threshold}.
     * Useful for detecting sensor failures or animals in trouble.
     */
    List<Animal> findInactiveSince(Instant threshold) throws IOException;

    /**
     * Returns all animals whose {@code lastWaterVisit} is null or before {@code threshold}.
     * Used to detect dehydration risk or broken water troughs.
     */
    List<Animal> findWithoutWaterVisitSince(Instant threshold) throws IOException;
}
