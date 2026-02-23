package io.cognis.core.memory;

import java.io.IOException;
import java.util.List;

public interface MemoryStore {
    MemoryEntry remember(String content, String source, List<String> tags) throws IOException;

    boolean forget(String id) throws IOException;

    List<MemoryEntry> recall(String query, int maxResults) throws IOException;

    List<MemoryEntry> list() throws IOException;

    int count() throws IOException;

    String formatContext(int maxEntries) throws IOException;
}
