package io.cognis.core.tool.impl;

import io.cognis.core.cron.CronService;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.workflow.WorkflowService;
import java.util.Map;

public final class WorkflowTool implements Tool {
    @Override
    public String name() {
        return "workflow";
    }

    @Override
    public String description() {
        return "Execute executive workflows: daily_brief, goal_plan, relationship_nudge";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of("type", "string"),
                "goal", Map.of("type", "string"),
                "person", Map.of("type", "string"),
                "horizon_days", Map.of("type", "integer"),
                "schedule_daily", Map.of("type", "boolean")
            ),
            "required", new String[] {"action"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        WorkflowService workflows = context.service("workflowService", WorkflowService.class);
        if (workflows == null) {
            return "Error: workflow service is not configured";
        }

        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        try {
            return switch (action) {
                case "daily_brief" -> workflows.buildDailyExecutiveBrief();
                case "goal_plan" -> handleGoalPlan(input, context, workflows);
                case "relationship_nudge" -> workflows.buildRelationshipNudge(asString(input.get("person")));
                default -> "Error: unsupported action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String handleGoalPlan(Map<String, Object> input, ToolContext context, WorkflowService workflows) throws Exception {
        String goal = asString(input.get("goal"));
        int horizonDays = asInt(input.get("horizon_days"), 7);
        String plan = workflows.buildGoalExecutionPlan(goal, horizonDays);
        if (plan.startsWith("Error:")) {
            return plan;
        }

        boolean scheduleDaily = asBoolean(input.get("schedule_daily"), true);
        if (!scheduleDaily) {
            return plan;
        }

        CronService cronService = context.service("cronService", CronService.class);
        if (cronService == null) {
            return plan + "\n\nDaily check-in not scheduled (cron service unavailable).";
        }

        String normalizedGoal = goal == null ? "" : goal.trim();
        String label = normalizedGoal.isBlank() ? "goal-checkin" : "goal-checkin-" + slug(normalizedGoal);
        cronService.addEvery(label, 24 * 60 * 60, "workflow:goal_checkin:" + normalizedGoal);
        return plan + "\n\nDaily check-in scheduled.";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean asBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String slug(String raw) {
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "goal" : normalized;
    }
}
