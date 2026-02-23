package io.cognis.core.config;

import java.nio.file.Path;

public final class ConfigPaths {

    private ConfigPaths() {
    }

    public static Path defaultConfigPath() {
        return Path.of(System.getProperty("user.home"), ".cognis", "config.json");
    }

    public static Path resolveWorkspace(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of(System.getProperty("user.home"), ".cognis", "workspace");
        }
        if (rawPath.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(rawPath.substring(2));
        }
        return Path.of(rawPath);
    }
}
