package io.cognis.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSessionSummaryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecordAndTrimSummary() throws Exception {
        FileSessionSummaryManager manager = new FileSessionSummaryManager(tempDir.resolve("summary.txt"), 120);

        manager.recordTurn("This is the first long user prompt that should be compressed", "Assistant first long answer");
        manager.recordTurn("Second prompt", "Second response");

        String summary = manager.currentSummary();
        assertThat(summary).isNotBlank();
        assertThat(summary.length()).isLessThanOrEqualTo(120);
        assertThat(summary).contains("Assistant");
    }
}
