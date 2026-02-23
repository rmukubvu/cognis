package io.cognis.core.observability;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ObservabilityService {
    private static final int MAX_EVENTS = 20_000;
    private static final Duration RECOVERY_WINDOW = Duration.ofHours(1);

    private final AuditStore store;
    private final Clock clock;

    public ObservabilityService(AuditStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized AuditEvent record(String type, Map<String, Object> attributes) throws IOException {
        List<AuditEvent> all = new ArrayList<>(store.load());
        AuditEvent event = new AuditEvent(
            UUID.randomUUID().toString(),
            clock.instant(),
            type,
            attributes
        );
        all.add(event);
        if (all.size() > MAX_EVENTS) {
            all = new ArrayList<>(all.subList(all.size() - MAX_EVENTS, all.size()));
        }
        store.save(all);
        return event;
    }

    public synchronized List<AuditEvent> recent(int limit) throws IOException {
        int safe = Math.max(1, limit);
        return store.load().stream()
            .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
            .limit(safe)
            .toList();
    }

    public synchronized DashboardSummary summary() throws IOException {
        List<AuditEvent> all = store.load().stream()
            .sorted(Comparator.comparing(AuditEvent::timestamp))
            .toList();
        Instant now = clock.instant();
        Instant since7d = now.minus(Duration.ofDays(7));
        Instant since14d = now.minus(Duration.ofDays(14));

        List<AuditEvent> taskStarted = byType(all, "task_started");
        List<AuditEvent> taskSucceeded = byType(all, "task_succeeded");
        List<AuditEvent> taskFailed = byType(all, "task_failed");
        List<AuditEvent> paymentRequests = byType(all, "payment_request");
        List<AuditEvent> paymentDenied = byType(all, "payment_denied");

        int started = taskStarted.size();
        int succeeded = taskSucceeded.size();
        int failed = taskFailed.size();
        double successRate = started == 0 ? 0.0 : percentage(succeeded, started);

        List<Double> latencies = taskSucceeded.stream()
            .map(e -> toDouble(e.attributes().get("duration_ms")))
            .filter(v -> v != null && v >= 0)
            .map(Double::doubleValue)
            .sorted()
            .toList();
        double p50 = percentile(latencies, 50);
        double p95 = percentile(latencies, 95);

        List<Double> costs = taskSucceeded.stream()
            .map(e -> toDouble(e.attributes().get("cost_usd")))
            .filter(v -> v != null && v >= 0)
            .map(Double::doubleValue)
            .toList();
        double avgCost = costs.isEmpty() ? 0.0 : costs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double recoveryRate = computeRecoveryRate(taskFailed, taskSucceeded);
        double safetyRate = paymentRequests.isEmpty() ? 0.0 : percentage(paymentDenied.size(), paymentRequests.size());
        int weeklyCompleted = (int) taskSucceeded.stream().filter(e -> !e.timestamp().isBefore(since7d)).count();

        Set<String> usersLast7d = uniqueClients(all, since7d, now);
        Set<String> usersPrev7d = uniqueClients(all, since14d, since7d);
        int activeUsers = usersLast7d.size();
        double retention7d = usersPrev7d.isEmpty() ? 0.0 : percentage(
            (int) usersPrev7d.stream().filter(usersLast7d::contains).count(),
            usersPrev7d.size()
        );

        return new DashboardSummary(
            started,
            succeeded,
            failed,
            round2(successRate),
            round2(p50),
            round2(p95),
            round4(avgCost),
            round2(recoveryRate),
            round2(safetyRate),
            weeklyCompleted,
            activeUsers,
            round2(retention7d),
            all.size()
        );
    }

    private List<AuditEvent> byType(List<AuditEvent> events, String type) {
        return events.stream().filter(e -> type.equalsIgnoreCase(e.type())).toList();
    }

    private Set<String> uniqueClients(List<AuditEvent> events, Instant fromInclusive, Instant toExclusive) {
        return events.stream()
            .filter(e -> !e.timestamp().isBefore(fromInclusive) && !e.timestamp().isAfter(toExclusive))
            .map(e -> e.attributes().get("client_id"))
            .map(value -> value == null ? "" : String.valueOf(value).trim())
            .filter(value -> !value.isBlank())
            .collect(Collectors.toSet());
    }

    private double computeRecoveryRate(List<AuditEvent> failures, List<AuditEvent> successes) {
        if (failures.isEmpty()) {
            return 0.0;
        }
        int recovered = 0;
        for (AuditEvent failed : failures) {
            String client = str(failed.attributes().get("client_id"));
            if (client.isBlank()) {
                continue;
            }
            Instant max = failed.timestamp().plus(RECOVERY_WINDOW);
            boolean hasRecovered = successes.stream().anyMatch(success ->
                client.equals(str(success.attributes().get("client_id")))
                    && !success.timestamp().isBefore(failed.timestamp())
                    && !success.timestamp().isAfter(max)
            );
            if (hasRecovered) {
                recovered++;
            }
        }
        return percentage(recovered, failures.size());
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private double percentile(List<Double> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int safe = Math.max(0, Math.min(100, percentile));
        if (safe == 0) {
            return sorted.getFirst();
        }
        int index = (int) Math.ceil((safe / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (numerator * 100.0) / denominator;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
