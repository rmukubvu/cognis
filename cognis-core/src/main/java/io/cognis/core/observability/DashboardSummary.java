package io.cognis.core.observability;

public record DashboardSummary(
    int tasksStarted,
    int tasksSucceeded,
    int tasksFailed,
    double taskSuccessRate,
    double p50LatencyMs,
    double p95LatencyMs,
    double averageCostPerTaskUsd,
    double failureRecoveryRate,
    double safetyIncidentRate,
    int weeklyCompletedTasks,
    int activeUsers7d,
    double retention7d,
    int auditEvents
) {
}
