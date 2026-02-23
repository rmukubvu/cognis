package io.cognis.core.memory;

import java.util.List;

public interface MemoryExtractor {
    List<ExtractedMemory> extract(String userPrompt, String assistantResponse);
}
