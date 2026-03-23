package io.cognis.vertical.livestock.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.store.AnimalStore;
import io.cognis.vertical.livestock.store.FileAnimalStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HerdLocationToolTest {

    @TempDir Path tempDir;

    private ToolContext contextWithStore(AnimalStore store) {
        return new ToolContext(tempDir, Map.of("animalStore", store));
    }

    private ToolContext emptyContext() {
        return new ToolContext(tempDir, Map.of());
    }

    @Test
    void nameIsHerdLocation() {
        assertThat(new HerdLocationTool().name()).isEqualTo("herd_location");
    }

    @Test
    void emptyStoreReturnsNoAnimalsMessage() {
        FileAnimalStore store = new FileAnimalStore(tempDir.resolve("animals.json"));
        String result = new HerdLocationTool().execute(Map.of(), contextWithStore(store));
        assertThat(result).contains("No animals registered");
    }

    @Test
    void nullStoreReturnsNoStoreMessage() {
        String result = new HerdLocationTool().execute(Map.of(), emptyContext());
        assertThat(result).contains("No animal store");
    }

    @Test
    void populatedStoreReturnsJsonWithAllAnimals() throws Exception {
        FileAnimalStore store = new FileAnimalStore(tempDir.resolve("animals.json"));
        store.upsert(Animal.create("EUI-001", "cattle", -25.74, 28.18));
        store.upsert(Animal.create("EUI-002", "sheep",  -25.75, 28.19));

        String result = new HerdLocationTool().execute(Map.of(), contextWithStore(store));

        assertThat(result).contains("\"totalAnimals\":2");
        assertThat(result).contains("EUI-001");
        assertThat(result).contains("EUI-002");
        assertThat(result).contains("cattle");
        assertThat(result).contains("sheep");
    }

    @Test
    void resultContainsExpectedFields() throws Exception {
        FileAnimalStore store = new FileAnimalStore(tempDir.resolve("animals.json"));
        store.upsert(Animal.create("EUI-003", "goat", -26.0, 27.5));

        String result = new HerdLocationTool().execute(Map.of(), contextWithStore(store));

        assertThat(result).contains("\"id\"");
        assertThat(result).contains("\"species\"");
        assertThat(result).contains("\"lat\"");
        assertThat(result).contains("\"lng\"");
        assertThat(result).contains("\"lastSeen\"");
        assertThat(result).contains("\"insideGeofence\"");
        assertThat(result).contains("\"activityLevel\"");
    }
}
