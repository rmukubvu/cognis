package io.cognis.core.profile;

import java.io.IOException;

public interface ProfileStore {
    UserProfile get() throws IOException;

    void setField(String field, String value) throws IOException;

    void setPreference(String key, String value) throws IOException;

    void addGoal(String goal) throws IOException;

    void removeGoal(String goal) throws IOException;

    void addRelationship(String name, String notes) throws IOException;

    String formatForPrompt() throws IOException;
}
