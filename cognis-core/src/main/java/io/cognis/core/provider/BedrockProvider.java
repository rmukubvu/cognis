package io.cognis.core.provider;

import io.cognis.core.model.ChatMessage;
import io.cognis.core.model.MessageRole;
import io.cognis.core.model.ToolCall;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

public final class BedrockProvider implements LlmProvider, AutoCloseable {
    private final String name;
    private final BedrockRuntimeClient client;
    private final boolean ownsClient;

    public BedrockProvider(
        String name,
        String region,
        String apiBase,
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        String profile
    ) {
        this(name, buildClient(region, apiBase, accessKeyId, secretAccessKey, sessionToken, profile), true);
    }

    BedrockProvider(String name, BedrockRuntimeClient client) {
        this(name, client, false);
    }

    private BedrockProvider(String name, BedrockRuntimeClient client, boolean ownsClient) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.ownsClient = ownsClient;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        if (model == null || model.isBlank()) {
            return new LlmResponse("Error calling LLM: missing model for provider " + name, List.of(), Map.of());
        }

        try {
            ConverseRequest.Builder request = ConverseRequest.builder()
                .modelId(normalizeModelId(model))
                .messages(toBedrockMessages(messages));

            List<SystemContentBlock> systemBlocks = toSystemBlocks(messages);
            if (!systemBlocks.isEmpty()) {
                request.system(systemBlocks);
            }

            ToolConfiguration toolConfiguration = toToolConfiguration(tools);
            if (toolConfiguration != null) {
                request.toolConfig(toolConfiguration);
            }

            ConverseResponse response = client.converse(request.build());
            return parseResponse(response);
        } catch (Exception e) {
            return new LlmResponse("Error calling LLM: " + e.getMessage(), List.of(), Map.of());
        }
    }

    @Override
    public void close() {
        if (ownsClient) {
            client.close();
        }
    }

    private static BedrockRuntimeClient buildClient(
        String region,
        String apiBase,
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        String profile
    ) {
        String effectiveRegion = firstNonBlank(region, System.getenv("AWS_REGION"), System.getenv("AWS_DEFAULT_REGION"));
        if (effectiveRegion == null) {
            throw new IllegalArgumentException("missing AWS region for Bedrock provider");
        }

        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider(accessKeyId, secretAccessKey, sessionToken, profile);
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
            .region(Region.of(effectiveRegion))
            .credentialsProvider(credentialsProvider);

        if (apiBase != null && !apiBase.isBlank()) {
            builder.endpointOverride(URI.create(apiBase));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider resolveCredentialsProvider(
        String accessKeyId,
        String secretAccessKey,
        String sessionToken,
        String profile
    ) {
        if (accessKeyId != null && !accessKeyId.isBlank() && secretAccessKey != null && !secretAccessKey.isBlank()) {
            if (sessionToken != null && !sessionToken.isBlank()) {
                return StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken)
                );
            }
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        if (profile != null && !profile.isBlank()) {
            return ProfileCredentialsProvider.create(profile);
        }
        return DefaultCredentialsProvider.create();
    }

    private String normalizeModelId(String model) {
        String normalized = model.trim();
        if (normalized.toLowerCase().startsWith("bedrock/")) {
            return normalized.substring("bedrock/".length());
        }
        return normalized;
    }

    private List<SystemContentBlock> toSystemBlocks(List<ChatMessage> messages) {
        List<SystemContentBlock> blocks = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.role() == MessageRole.SYSTEM && !message.content().isBlank()) {
                blocks.add(SystemContentBlock.builder().text(message.content()).build());
            }
        }
        return blocks;
    }

    private List<Message> toBedrockMessages(List<ChatMessage> messages) {
        List<Message> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.role() == MessageRole.SYSTEM) {
                continue;
            }

            List<ContentBlock> content = new ArrayList<>();
            if (message.role() == MessageRole.ASSISTANT && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                if (message.content() != null && !message.content().isBlank()) {
                    content.add(ContentBlock.builder().text(message.content()).build());
                }
                int index = 0;
                for (ToolCall toolCall : message.toolCalls()) {
                    String toolCallId = toolCall.id() == null || toolCall.id().isBlank()
                        ? "tool_" + index++
                        : toolCall.id();
                    content.add(ContentBlock.builder().toolUse(
                        ToolUseBlock.builder()
                            .toolUseId(toolCallId)
                            .name(toolCall.name())
                            .input(toDocument(toolCall.arguments()))
                            .build()
                    ).build());
                }
            } else if (message.role() == MessageRole.TOOL) {
                content.add(ContentBlock.builder().toolResult(
                    software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock.builder()
                        .toolUseId(message.toolCallId() == null ? "" : message.toolCallId())
                        .status(ToolResultStatus.SUCCESS)
                        .content(List.of(ToolResultContentBlock.builder().text(message.content()).build()))
                        .build()
                ).build());
            } else if (message.content() != null && !message.content().isBlank()) {
                content.add(ContentBlock.builder().text(message.content()).build());
            }

            if (content.isEmpty()) {
                continue;
            }

            wire.add(Message.builder()
                .role(message.role() == MessageRole.ASSISTANT ? ConversationRole.ASSISTANT : ConversationRole.USER)
                .content(content)
                .build());
        }
        return wire;
    }

    private ToolConfiguration toToolConfiguration(List<Map<String, Object>> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        List<Tool> mapped = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Object functionObj = tool.get("function");
            if (!(functionObj instanceof Map<?, ?> function)) {
                continue;
            }

            Object nameObj = function.get("name");
            if (!(nameObj instanceof String functionName) || functionName.isBlank()) {
                continue;
            }

            Object descriptionObj = function.containsKey("description") ? function.get("description") : "";
            Object parametersObj = function.containsKey("parameters")
                ? function.get("parameters")
                : Map.of("type", "object", "properties", Map.of());

            ToolInputSchema inputSchema = ToolInputSchema.builder()
                .json(toDocument(parametersObj))
                .build();
            ToolSpecification specification = ToolSpecification.builder()
                .name(functionName)
                .description(String.valueOf(descriptionObj))
                .inputSchema(inputSchema)
                .build();
            mapped.add(Tool.builder().toolSpec(specification).build());
        }

        if (mapped.isEmpty()) {
            return null;
        }
        return ToolConfiguration.builder().tools(mapped).build();
    }

    private LlmResponse parseResponse(ConverseResponse response) {
        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        if (response.output() != null && response.output().message() != null) {
            for (ContentBlock block : response.output().message().content()) {
                if (block.text() != null) {
                    content.append(block.text());
                }
                if (block.toolUse() != null) {
                    ToolUseBlock toolUse = block.toolUse();
                    Map<String, Object> arguments = toObjectMap(toolUse.input());
                    toolCalls.add(new ToolCall(toolUse.toolUseId(), toolUse.name(), arguments));
                }
            }
        }

        Map<String, Object> usage = new LinkedHashMap<>();
        if (response.usage() != null) {
            usage.put("input_tokens", response.usage().inputTokens());
            usage.put("output_tokens", response.usage().outputTokens());
            usage.put("total_tokens", response.usage().totalTokens());
        }
        return new LlmResponse(content.toString(), toolCalls, usage);
    }

    private Map<String, Object> toObjectMap(Document document) {
        Object value = fromDocument(document);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        return Map.of();
    }

    private Document toDocument(Object value) {
        if (value == null) {
            return Document.fromNull();
        }
        if (value instanceof String s) {
            return Document.fromString(s);
        }
        if (value instanceof Boolean b) {
            return Document.fromBoolean(b);
        }
        if (value instanceof Number n) {
            return Document.fromNumber(n.toString());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Document> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), toDocument(entry.getValue()));
            }
            return Document.fromMap(converted);
        }
        if (value instanceof List<?> list) {
            List<Document> converted = new ArrayList<>();
            for (Object item : list) {
                converted.add(toDocument(item));
            }
            return Document.fromList(converted);
        }
        return Document.fromString(String.valueOf(value));
    }

    private Object fromDocument(Document document) {
        if (document == null || document.isNull()) {
            return null;
        }
        if (document.isString()) {
            return document.asString();
        }
        if (document.isBoolean()) {
            return document.asBoolean();
        }
        if (document.isNumber()) {
            String numeric = document.asNumber().toString();
            try {
                if (numeric.contains(".") || numeric.contains("e") || numeric.contains("E")) {
                    return Double.parseDouble(numeric);
                }
                return Long.parseLong(numeric);
            } catch (NumberFormatException ignored) {
                return numeric;
            }
        }
        if (document.isList()) {
            List<Object> values = new ArrayList<>();
            for (Document item : document.asList()) {
                values.add(fromDocument(item));
            }
            return values;
        }
        if (document.isMap()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Document> entry : document.asMap().entrySet()) {
                values.put(entry.getKey(), fromDocument(entry.getValue()));
            }
            return values;
        }
        return document.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
