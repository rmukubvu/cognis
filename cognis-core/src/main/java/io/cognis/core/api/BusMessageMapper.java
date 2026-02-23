package io.cognis.core.api;

import io.cognis.core.model.ChatMessage;
import java.util.List;

final class BusMessageMapper {
    private static final String NOTIFICATION = "notification";
    private static final List<WorkflowPrefix> WORKFLOW_PREFIXES = List.of(
        new WorkflowPrefix("[workflow:daily_brief]", "daily_brief"),
        new WorkflowPrefix("[workflow:goal_checkin]", "goal_checkin"),
        new WorkflowPrefix("[workflow:workflow_result]", "workflow_result")
    );

    OutboundFrame map(ChatMessage message) {
        String raw = message.content() == null ? "" : message.content();
        for (WorkflowPrefix prefix : WORKFLOW_PREFIXES) {
            if (raw.startsWith(prefix.marker())) {
                String stripped = raw.substring(prefix.marker().length()).stripLeading();
                return new OutboundFrame(prefix.type(), stripped);
            }
        }
        return new OutboundFrame(NOTIFICATION, raw);
    }

    record OutboundFrame(String type, String content) {
    }

    private record WorkflowPrefix(String marker, String type) {
    }
}
