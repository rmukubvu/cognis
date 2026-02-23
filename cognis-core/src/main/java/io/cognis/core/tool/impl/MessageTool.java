package io.cognis.core.tool.impl;

import io.cognis.core.bus.MessageBus;
import io.cognis.core.middleware.PiiRedactor;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.Map;

public final class MessageTool implements Tool {
    private final PiiRedactor piiRedactor = new PiiRedactor();

    @Override
    public String name() {
        return "message";
    }

    @Override
    public String description() {
        return "Publish an outbound message into the internal bus";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        MessageBus messageBus = context.service("messageBus", MessageBus.class);
        if (messageBus == null) {
            return "Error: message bus is not configured";
        }

        String channel = String.valueOf(input.getOrDefault("channel", "")).trim();
        String content = String.valueOf(input.getOrDefault("content", "")).trim();
        if (channel.isBlank() || content.isBlank()) {
            return "Error: channel and content are required";
        }

        String redacted = piiRedactor.redact(content);
        messageBus.publish(ChatMessage.assistant("[" + channel + "] " + redacted));
        return "Queued message for channel: " + channel;
    }
}
