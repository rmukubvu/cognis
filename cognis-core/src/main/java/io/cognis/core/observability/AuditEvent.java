package io.cognis.core.observability;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
    String id,
    Instant timestamp,
    String type,
    Map<String, Object> attributes
) {
    public AuditEvent {
        id = id == null ? "" : id.trim();
        timestamp = timestamp == null ? Instant.EPOCH : timestamp;
        type = type == null ? "" : type.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
