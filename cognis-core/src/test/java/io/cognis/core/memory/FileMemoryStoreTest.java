package io.cognis.core.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMemoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRememberRecallAndForget() throws Exception {
        FileMemoryStore store = new FileMemoryStore(tempDir.resolve("memories.json"));

        MemoryEntry entry = store.remember("User likes concise answers", "agent", List.of("preference"));
        assertThat(store.count()).isEqualTo(1);

        var recalled = store.recall("concise", 5);
        assertThat(recalled).hasSize(1);
        assertThat(recalled.getFirst().id()).isEqualTo(entry.id());

        assertThat(store.forget(entry.id())).isTrue();
        assertThat(store.count()).isZero();
    }
}
