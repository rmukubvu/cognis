package io.cognis.core.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class FileProfileStore implements ProfileStore {
    private final Path path;
    private final ObjectMapper mapper;

    public FileProfileStore(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized UserProfile get() throws IOException {
        if (!Files.exists(path)) {
            return UserProfile.empty();
        }
        try {
            return mapper.readValue(Files.readString(path), UserProfile.class);
        } catch (Exception e) {
            return UserProfile.empty();
        }
    }

    @Override
    public synchronized void setField(String field, String value) throws IOException {
        UserProfile current = get();
        UserProfile updated = switch (field) {
            case "name" -> new UserProfile(value, current.timezone(), current.preferences(), current.goals(), current.relationships(), current.notes(), Instant.now());
            case "timezone" -> new UserProfile(current.name(), value, current.preferences(), current.goals(), current.relationships(), current.notes(), Instant.now());
            case "notes" -> new UserProfile(current.name(), current.timezone(), current.preferences(), current.goals(), current.relationships(), value, Instant.now());
            default -> current;
        };
        save(updated);
    }

    @Override
    public synchronized void setPreference(String key, String value) throws IOException {
        UserProfile current = get();
        Map<String, String> prefs = new LinkedHashMap<>(current.preferences());
        prefs.put(key, value);
        save(new UserProfile(current.name(), current.timezone(), Map.copyOf(prefs), current.goals(), current.relationships(), current.notes(), Instant.now()));
    }

    @Override
    public synchronized void addGoal(String goal) throws IOException {
        UserProfile current = get();
        LinkedHashSet<String> set = new LinkedHashSet<>(current.goals());
        set.add(goal);
        save(new UserProfile(current.name(), current.timezone(), current.preferences(), new ArrayList<>(set), current.relationships(), current.notes(), Instant.now()));
    }

    @Override
    public synchronized void removeGoal(String goal) throws IOException {
        UserProfile current = get();
        ArrayList<String> goals = new ArrayList<>(current.goals());
        goals.removeIf(g -> g.equals(goal));
        save(new UserProfile(current.name(), current.timezone(), current.preferences(), goals, current.relationships(), current.notes(), Instant.now()));
    }

    @Override
    public synchronized void addRelationship(String name, String notes) throws IOException {
        UserProfile current = get();
        Map<String, String> people = new LinkedHashMap<>(current.relationships());
        people.put(name, notes == null ? "" : notes);
        save(new UserProfile(current.name(), current.timezone(), current.preferences(), current.goals(), Map.copyOf(people), current.notes(), Instant.now()));
    }

    @Override
    public synchronized String formatForPrompt() throws IOException {
        UserProfile p = get();
        if (p.name().isBlank() && p.preferences().isEmpty() && p.goals().isEmpty() && p.relationships().isEmpty() && p.notes().isBlank()) {
            return "";
        }

        StringBuilder out = new StringBuilder("## User Profile\n\n");
        if (!p.name().isBlank()) {
            out.append("**Name:** ").append(p.name()).append("\n");
        }
        if (!p.timezone().isBlank()) {
            out.append("**Timezone:** ").append(p.timezone()).append("\n");
        }
        if (!p.notes().isBlank()) {
            out.append("**Notes:** ").append(p.notes()).append("\n");
        }
        if (!p.preferences().isEmpty()) {
            out.append("\n**Preferences:**\n");
            p.preferences().forEach((k, v) -> out.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        if (!p.goals().isEmpty()) {
            out.append("\n**Goals:**\n");
            p.goals().forEach(g -> out.append("- ").append(g).append("\n"));
        }
        if (!p.relationships().isEmpty()) {
            out.append("\n**People:**\n");
            p.relationships().forEach((k, v) -> out.append("- ").append(k).append(v.isBlank() ? "" : ": " + v).append("\n"));
        }
        return out.toString();
    }

    private void save(UserProfile profile) throws IOException {
        Files.createDirectories(path.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json + System.lineSeparator());
        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
}
