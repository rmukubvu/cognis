package io.cognis.core.memory;

import java.time.Instant;
import java.util.List;

public record MemoryEntry(
    String id,
    String content,
    List<String> tags,
    List<Double> embedding,
    String source,
    Instant createdAt,
    Instant updatedAt
) {
}
