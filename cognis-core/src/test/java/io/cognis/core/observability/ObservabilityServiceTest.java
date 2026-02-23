package io.cognis.core.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservabilityServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldComputeDashboardSummary() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-02-21T12:00:00Z"), ZoneOffset.UTC);
        ObservabilityService service = new ObservabilityService(
            new FileAuditStore(tempDir.resolve("audit.json")),
            clock
        );

        service.record("user_activity", Map.of("client_id", "alice"));
        service.record("task_started", Map.of("task_id", "t1", "client_id", "alice"));
        service.record("task_succeeded", Map.of("task_id", "t1", "client_id", "alice", "duration_ms", 420, "cost_usd", 0.04));
        service.record("task_started", Map.of("task_id", "t2", "client_id", "alice"));
        service.record("task_failed", Map.of("task_id", "t2", "client_id", "alice", "duration_ms", 180));
        service.record("payment_request", Map.of("transaction_id", "p1"));
        service.record("payment_denied", Map.of("transaction_id", "p1"));

        DashboardSummary summary = service.summary();

        assertThat(summary.tasksStarted()).isEqualTo(2);
        assertThat(summary.tasksSucceeded()).isEqualTo(1);
        assertThat(summary.tasksFailed()).isEqualTo(1);
        assertThat(summary.taskSuccessRate()).isEqualTo(50.0);
        assertThat(summary.p50LatencyMs()).isEqualTo(420.0);
        assertThat(summary.p95LatencyMs()).isEqualTo(420.0);
        assertThat(summary.averageCostPerTaskUsd()).isEqualTo(0.04);
        assertThat(summary.safetyIncidentRate()).isEqualTo(100.0);
        assertThat(summary.activeUsers7d()).isEqualTo(1);
    }
}
