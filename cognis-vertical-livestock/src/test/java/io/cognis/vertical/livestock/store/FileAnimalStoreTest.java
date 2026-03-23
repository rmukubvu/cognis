package io.cognis.vertical.livestock.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.vertical.livestock.model.Animal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAnimalStoreTest {

    @TempDir Path tempDir;

    private FileAnimalStore store() {
        return new FileAnimalStore(tempDir.resolve("animals.json"));
    }

    @Test
    void upsertAndFindAll() throws Exception {
        FileAnimalStore s = store();
        Animal cow = Animal.create("EUI-001", "cattle", -25.74, 28.18);
        s.upsert(cow);

        List<Animal> all = s.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).id()).isEqualTo("EUI-001");
        assertThat(all.get(0).species()).isEqualTo("cattle");
    }

    @Test
    void upsertOverwritesExistingById() throws Exception {
        FileAnimalStore s = store();
        Animal original = Animal.create("EUI-002", "sheep", -25.74, 28.18);
        s.upsert(original);

        Animal updated = original.withActivity(0.5);
        s.upsert(updated);

        List<Animal> all = s.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).activityLevel()).isEqualTo(0.5);
    }

    @Test
    void findByIdReturnsPresentForKnownAnimal() throws Exception {
        FileAnimalStore s = store();
        s.upsert(Animal.create("EUI-003", "goat", -26.0, 27.5));

        Optional<Animal> found = s.findById("EUI-003");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("EUI-003");
    }

    @Test
    void findByIdReturnsEmptyForUnknownAnimal() throws Exception {
        Optional<Animal> found = store().findById("DOES-NOT-EXIST");
        assertThat(found).isEmpty();
    }

    @Test
    void findOutsideGeofenceFiltersCorrectly() throws Exception {
        FileAnimalStore s = store();
        Animal inside  = Animal.create("EUI-010", "cattle", -25.0, 28.0).withGeofenceStatus(true);
        Animal outside = Animal.create("EUI-011", "cattle", -36.0, 14.0).withGeofenceStatus(false);
        s.upsert(inside);
        s.upsert(outside);

        List<Animal> breaches = s.findOutsideGeofence();
        assertThat(breaches).hasSize(1);
        assertThat(breaches.get(0).id()).isEqualTo("EUI-011");
    }

    @Test
    void findInactiveSinceReturnsAnimalsNotSeenAfterThreshold() throws Exception {
        FileAnimalStore s = store();
        // Use Jackson's Instant serialization — create an animal with an old lastSeen via withLocation trick
        // We need to inject an old timestamp; we'll use the JsonCreator constructor via Animal record directly
        Instant oldTime = Instant.now().minus(8, ChronoUnit.HOURS);
        Animal stale = new Animal("EUI-020", "", "cattle", "A", -25.0, 28.0, oldTime, 1.0, null, true);
        Animal fresh = Animal.create("EUI-021", "cattle", -25.0, 28.0);
        s.upsert(stale);
        s.upsert(fresh);

        Instant threshold = Instant.now().minus(6, ChronoUnit.HOURS);
        List<Animal> inactive = s.findInactiveSince(threshold);
        assertThat(inactive).hasSize(1);
        assertThat(inactive.get(0).id()).isEqualTo("EUI-020");
    }

    @Test
    void findWithoutWaterVisitSinceReturnsAnimalsWithNullOrOldVisit() throws Exception {
        FileAnimalStore s = store();
        Instant oldVisit = Instant.now().minus(30, ChronoUnit.HOURS);
        Instant recentVisit = Instant.now().minus(1, ChronoUnit.HOURS);

        Animal noWater   = new Animal("EUI-030", "", "cattle", "B", -25.0, 28.0, Instant.now(), 1.0, null, true);
        Animal oldWater  = new Animal("EUI-031", "", "sheep",  "B", -25.0, 28.0, Instant.now(), 1.0, oldVisit, true);
        Animal freshWater = new Animal("EUI-032", "", "goat",  "C", -25.0, 28.0, Instant.now(), 1.0, recentVisit, true);
        s.upsert(noWater);
        s.upsert(oldWater);
        s.upsert(freshWater);

        Instant threshold = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Animal> noVisit = s.findWithoutWaterVisitSince(threshold);
        assertThat(noVisit).hasSize(2);
        assertThat(noVisit.stream().map(Animal::id).toList())
            .containsExactlyInAnyOrder("EUI-030", "EUI-031");
    }

    @Test
    void findAllOnEmptyStoreReturnsEmptyList() throws Exception {
        assertThat(store().findAll()).isEmpty();
    }

    @Test
    void multipleAnimalsPersistedCorrectly() throws Exception {
        FileAnimalStore s = store();
        s.upsert(Animal.create("EUI-100", "cattle", -25.0, 28.0));
        s.upsert(Animal.create("EUI-101", "sheep",  -25.1, 28.1));
        s.upsert(Animal.create("EUI-102", "goat",   -25.2, 28.2));
        assertThat(s.findAll()).hasSize(3);
    }
}
