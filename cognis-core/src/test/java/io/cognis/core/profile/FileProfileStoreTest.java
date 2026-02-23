package io.cognis.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProfileStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistProfileFieldsAndFormatPrompt() throws Exception {
        FileProfileStore store = new FileProfileStore(tempDir.resolve("profile.json"));

        store.setField("name", "Robson");
        store.setPreference("tone", "direct");
        store.addGoal("Ship cognis");
        store.addRelationship("Alice", "teammate");

        UserProfile profile = store.get();
        assertThat(profile.name()).isEqualTo("Robson");
        assertThat(profile.preferences()).containsEntry("tone", "direct");
        assertThat(profile.goals()).contains("Ship cognis");

        String prompt = store.formatForPrompt();
        assertThat(prompt).contains("User Profile").contains("Robson").contains("Ship cognis");
    }
}
