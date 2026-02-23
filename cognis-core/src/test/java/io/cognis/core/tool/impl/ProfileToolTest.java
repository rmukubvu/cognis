package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.profile.FileProfileStore;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUpdateAndGetProfile() {
        ProfileTool tool = new ProfileTool();
        FileProfileStore store = new FileProfileStore(tempDir.resolve("profile.json"));
        ToolContext context = new ToolContext(tempDir, Map.of("profileStore", store));

        String set = tool.execute(Map.of("action", "set_name", "value", "Robson"), context);
        String get = tool.execute(Map.of("action", "get"), context);

        assertThat(set).contains("Profile updated");
        assertThat(get).contains("Robson");
    }
}
