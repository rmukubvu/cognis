package io.cognis.core.profile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserProfile(
    String name,
    String timezone,
    Map<String, String> preferences,
    List<String> goals,
    Map<String, String> relationships,
    String notes,
    Instant updatedAt
) {
    public static UserProfile empty() {
        return new UserProfile("", "", Map.of(), List.of(), Map.of(), "", Instant.now());
    }
}
