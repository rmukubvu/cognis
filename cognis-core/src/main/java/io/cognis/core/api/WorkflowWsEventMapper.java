package io.cognis.core.api;

public final class WorkflowWsEventMapper {
    private WorkflowWsEventMapper() {
    }

    public static Event map(String raw) {
        String content = raw == null ? "" : raw;
        if (content.startsWith("[workflow:daily_brief]")) {
            return new Event("daily_brief", content.replaceFirst("^\\[workflow:daily_brief\\]\\s*", "").trim());
        }
        if (content.startsWith("[workflow:goal_checkin]")) {
            return new Event("goal_checkin", content.replaceFirst("^\\[workflow:goal_checkin\\]\\s*", "").trim());
        }
        if (content.startsWith("[workflow:workflow_result]")) {
            return new Event("workflow_result", content.replaceFirst("^\\[workflow:workflow_result\\]\\s*", "").trim());
        }
        return new Event("notification", content);
    }

    public record Event(String type, String content) {
    }
}
