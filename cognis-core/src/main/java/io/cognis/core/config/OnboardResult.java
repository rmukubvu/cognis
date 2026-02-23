package io.cognis.core.config;

import java.nio.file.Path;

public record OnboardResult(Path configPath, Path workspacePath, boolean createdConfig, boolean overwrittenConfig) {
}
