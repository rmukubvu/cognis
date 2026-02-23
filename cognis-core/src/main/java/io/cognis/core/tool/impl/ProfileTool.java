package io.cognis.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.core.profile.ProfileStore;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.Map;

public final class ProfileTool implements Tool {
    private final ObjectMapper mapper;

    public ProfileTool() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public String description() {
        return "Read and update user profile: get, set_name, set_timezone, set_preference, set_notes, add_goal, remove_goal, add_person";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        ProfileStore store = context.service("profileStore", ProfileStore.class);
        if (store == null) {
            return "Error: profile store is not configured";
        }

        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        try {
            return switch (action) {
                case "get" -> mapper.writerWithDefaultPrettyPrinter().writeValueAsString(store.get());
                case "set_name" -> setField(store, "name", input.get("value"));
                case "set_timezone" -> setField(store, "timezone", input.get("value"));
                case "set_notes" -> setField(store, "notes", input.get("value"));
                case "set_preference" -> setPreference(store, input);
                case "add_goal" -> addGoal(store, input.get("value"));
                case "remove_goal" -> removeGoal(store, input.get("value"));
                case "add_person" -> addPerson(store, input);
                default -> "Error: unknown action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String setField(ProfileStore store, String field, Object value) throws Exception {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (text.isBlank()) {
            return "Error: value is required";
        }
        store.setField(field, text);
        return "Profile updated";
    }

    private String setPreference(ProfileStore store, Map<String, Object> input) throws Exception {
        String key = String.valueOf(input.getOrDefault("key", "")).trim();
        String value = String.valueOf(input.getOrDefault("value", "")).trim();
        if (key.isBlank()) {
            return "Error: key is required";
        }
        store.setPreference(key, value);
        return "Preference updated";
    }

    private String addGoal(ProfileStore store, Object value) throws Exception {
        String goal = String.valueOf(value == null ? "" : value).trim();
        if (goal.isBlank()) {
            return "Error: goal is required";
        }
        store.addGoal(goal);
        return "Goal added";
    }

    private String removeGoal(ProfileStore store, Object value) throws Exception {
        String goal = String.valueOf(value == null ? "" : value).trim();
        if (goal.isBlank()) {
            return "Error: goal is required";
        }
        store.removeGoal(goal);
        return "Goal removed";
    }

    private String addPerson(ProfileStore store, Map<String, Object> input) throws Exception {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String notes = String.valueOf(input.getOrDefault("notes", "")).trim();
        if (name.isBlank()) {
            return "Error: name is required";
        }
        store.addRelationship(name, notes);
        return "Person saved";
    }
}
