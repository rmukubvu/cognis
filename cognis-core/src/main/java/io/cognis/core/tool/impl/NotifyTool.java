package io.cognis.core.tool.impl;

import io.cognis.core.bus.MessageBus;
import io.cognis.core.cron.CronService;
import io.cognis.core.cron.NaturalTimeParser;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

public final class NotifyTool implements Tool {
    private final NaturalTimeParser timeParser = new NaturalTimeParser();

    @Override
    public String name() {
        return "notify";
    }

    @Override
    public String description() {
        return "Send immediate or scheduled notification messages";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String message = String.valueOf(input.getOrDefault("message", "")).trim();
        if (message.isBlank()) {
            return "Error: message is required";
        }

        MessageBus messageBus = context.service("messageBus", MessageBus.class);
        CronService cronService = context.service("cronService", CronService.class);
        String label = String.valueOf(input.getOrDefault("label", truncate(message, 40)));
        String at = String.valueOf(input.getOrDefault("at", "")).trim();
        if (!at.isBlank()) {
            if (cronService == null) {
                return "Error: cron service is not configured";
            }
            try {
                long runAt = timeParser.parseToEpochMs(at, Clock.systemUTC(), ZoneId.systemDefault());
                var job = cronService.addAt(label, runAt, message);
                return "Notification scheduled at " + at + " (id: " + job.id() + ")";
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }

        int inSeconds = toInt(input.get("inSeconds"), 0);
        if (inSeconds <= 0) {
            if (messageBus == null) {
                return "Error: message bus is not configured";
            }
            messageBus.publish(io.cognis.core.model.ChatMessage.assistant("[notify] " + message));
            return "Notification delivered immediately";
        }

        if (cronService == null) {
            return "Error: cron service is not configured";
        }

        try {
            var job = cronService.addIn(label, inSeconds, message);
            return "Notification scheduled in " + inSeconds + "s (id: " + job.id() + ")";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "...";
    }
}
