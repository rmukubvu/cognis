package io.cognis.core.tool;

import java.nio.file.Path;
import java.util.Map;

public record ToolContext(Path workspace, Map<String, Object> services) {

    public ToolContext(Path workspace) {
        this(workspace, Map.of());
    }

    public <T> T service(String key, Class<T> type) {
        Object service = services.get(key);
        if (service == null) {
            return null;
        }
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException("Service '" + key + "' is not of type " + type.getSimpleName());
        }
        return type.cast(service);
    }
}
