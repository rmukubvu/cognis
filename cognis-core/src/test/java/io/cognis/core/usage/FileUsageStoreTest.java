package io.cognis.core.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUsageStoreTest {

    @TempDir
    Path tempDir;

    private UsageRecord sampleRecord(String vertical, String clientId, Instant timestamp) {
        return new UsageRecord(
            timestamp,
            vertical,
            clientId,
            "whatsapp",
            "anthropic",
            "claude-sonnet-3-5",
            100,
            50,
            1234L,
            0.002,
            List.of("safex_price")
        );
    }

    @Test
    void findAllReturnsEmptyListWhenFileDoesNotExist() throws Exception {
        FileUsageStore store = new FileUsageStore(tempDir.resolve("usage.jsonl"));

        List<UsageRecord> records = store.findAll();

        assertThat(records).isEmpty();
    }

    @Test
    void appendAndFindAllRoundTrip() throws Exception {
        FileUsageStore store = new FileUsageStore(tempDir.resolve("usage.jsonl"));
        UsageRecord r1 = sampleRecord("sa-agriculture", "+27821234567", Instant.parse("2026-03-01T08:00:00Z"));
        UsageRecord r2 = sampleRecord("humanitarian",   "+25471234567", Instant.parse("2026-03-02T09:00:00Z"));

        store.append(r1);
        store.append(r2);

        List<UsageRecord> all = store.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).vertical()).isEqualTo("sa-agriculture");
        assertThat(all.get(0).clientId()).isEqualTo("+27821234567");
        assertThat(all.get(0).promptTokens()).isEqualTo(100);
        assertThat(all.get(0).completionTokens()).isEqualTo(50);
        assertThat(all.get(0).toolsUsed()).containsExactly("safex_price");
        assertThat(all.get(1).vertical()).isEqualTo("humanitarian");
    }

    @Test
    void appendCreatesParentDirectoriesAutomatically() throws Exception {
        Path nested = tempDir.resolve("deep/dir/usage.jsonl");
        FileUsageStore store = new FileUsageStore(nested);

        store.append(sampleRecord("sa-agriculture", "+27821111111", Instant.now()));

        assertThat(nested).exists();
        assertThat(store.findAll()).hasSize(1);
    }

    @Test
    void findSinceFiltersRecordsByTimestamp() throws Exception {
        FileUsageStore store = new FileUsageStore(tempDir.resolve("usage.jsonl"));
        Instant early  = Instant.parse("2026-01-01T00:00:00Z");
        Instant middle = Instant.parse("2026-02-01T00:00:00Z");
        Instant late   = Instant.parse("2026-03-01T00:00:00Z");

        store.append(sampleRecord("sa-agriculture", "a", early));
        store.append(sampleRecord("humanitarian",   "b", middle));
        store.append(sampleRecord("sa-agriculture", "c", late));

        List<UsageRecord> result = store.findSince(middle);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(UsageRecord::clientId)).containsExactlyInAnyOrder("b", "c");
    }

    @Test
    void findSinceIncludesRecordExactlyAtBoundary() throws Exception {
        FileUsageStore store = new FileUsageStore(tempDir.resolve("usage.jsonl"));
        Instant boundary = Instant.parse("2026-03-01T12:00:00Z");

        store.append(sampleRecord("sa-agriculture", "x", boundary));

        List<UsageRecord> result = store.findSince(boundary);

        assertThat(result).hasSize(1);
    }

    @Test
    void findSinceReturnsEmptyWhenNoRecordsMatchWindow() throws Exception {
        FileUsageStore store = new FileUsageStore(tempDir.resolve("usage.jsonl"));
        store.append(sampleRecord("sa-agriculture", "a", Instant.parse("2026-01-01T00:00:00Z")));

        List<UsageRecord> result = store.findSince(Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(result).isEmpty();
    }

    @Test
    void totalTokensHelperReturnsSum() {
        UsageRecord r = sampleRecord("sa-agriculture", "a", Instant.now());
        assertThat(r.totalTokens()).isEqualTo(150);
    }
}
