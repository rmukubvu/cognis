package io.cognis.core.agent;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Persistence interface for {@link DynamicAgent} definitions.
 *
 * <p>Used by {@link io.cognis.core.tool.impl.AgentTool} to save and load
 * persistent named agents created via {@code action=create}.
 */
public interface AgentStore {

    /** Persist a new or updated agent definition. */
    void save(DynamicAgent agent) throws IOException;

    /** Find a named agent by exact name match. */
    Optional<DynamicAgent> find(String name) throws IOException;

    /** Return all stored agents. */
    List<DynamicAgent> list() throws IOException;

    /** Remove an agent by name. Returns true if it existed and was removed. */
    boolean delete(String name) throws IOException;
}
