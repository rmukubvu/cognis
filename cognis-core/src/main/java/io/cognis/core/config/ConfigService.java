package io.cognis.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.core.config.model.CognisConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ConfigService {
    private final ObjectMapper mapper;

    public ConfigService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public CognisConfig load(Path configPath) throws IOException {
        Objects.requireNonNull(configPath, "configPath must not be null");
        if (!Files.exists(configPath)) {
            return CognisConfig.defaults();
        }

        JsonNode defaultsNode = mapper.valueToTree(CognisConfig.defaults());
        JsonNode existingNode = mapper.readTree(Files.readString(configPath));
        JsonNode merged = deepMerge(defaultsNode, existingNode);
        return mapper.treeToValue(merged, CognisConfig.class);
    }

    public void save(Path configPath, CognisConfig config) throws IOException {
        Objects.requireNonNull(configPath, "configPath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Files.createDirectories(configPath.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(configPath, json + System.lineSeparator());
    }

    public OnboardResult onboard(Path configPath, boolean overwrite) throws IOException {
        boolean created = !Files.exists(configPath);
        boolean overwritten = false;

        CognisConfig config;
        if (created || overwrite) {
            config = CognisConfig.defaults();
            overwritten = !created && overwrite;
        } else {
            config = load(configPath);
        }

        save(configPath, config);

        Path workspace = ConfigPaths.resolveWorkspace(config.agents().defaults().workspace());
        WorkspaceBootstrap.ensureWorkspaceTemplates(workspace);
        return new OnboardResult(configPath, workspace, created, overwritten);
    }

    public String toPrettyJson(CognisConfig config) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize config", e);
        }
    }

    private JsonNode deepMerge(JsonNode base, JsonNode override) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        if (!base.isObject() || !override.isObject()) {
            return override;
        }

        ObjectNode merged = ((ObjectNode) base).deepCopy();
        override.fields().forEachRemaining(entry -> {
            JsonNode existing = merged.get(entry.getKey());
            merged.set(entry.getKey(), deepMerge(existing, entry.getValue()));
        });
        return merged;
    }
}
