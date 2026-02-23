package io.cognis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.config.model.CognisConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadDefaultsWhenConfigMissing() throws Exception {
        ConfigService service = new ConfigService();
        Path configPath = tempDir.resolve("config.json");

        CognisConfig config = service.load(configPath);

        assertThat(config.agents().defaults().model()).isEqualTo("anthropic/claude-opus-4-5");
        assertThat(config.providers().openrouter().configured()).isFalse();
    }

    @Test
    void shouldRefreshConfigByMergingDefaultsWithExistingValues() throws Exception {
        ConfigService service = new ConfigService();
        Path configPath = tempDir.resolve("config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
            {
              "agents": {
                "defaults": {
                  "model": "gpt-5"
                }
              },
              "providers": {
                "openrouter": {
                  "apiKey": "sk-test"
                }
              }
            }
            """);

        CognisConfig config = service.load(configPath);

        assertThat(config.agents().defaults().model()).isEqualTo("gpt-5");
        assertThat(config.agents().defaults().maxToolIterations()).isEqualTo(20);
        assertThat(config.providers().openrouter().apiKey()).isEqualTo("sk-test");
        assertThat(config.providers().openai().apiKey()).isEqualTo("");
    }

    @Test
    void onboardShouldCreateConfigAndWorkspaceTemplates() throws Exception {
        ConfigService service = new ConfigService();
        Path configPath = tempDir.resolve(".cognis/config.json");

        OnboardResult result = service.onboard(configPath, false);

        assertThat(result.createdConfig()).isTrue();
        assertThat(Files.exists(configPath)).isTrue();
        assertThat(Files.exists(result.workspacePath().resolve("AGENTS.md"))).isTrue();
        assertThat(Files.exists(result.workspacePath().resolve("memory/MEMORY.md"))).isTrue();
    }
}
