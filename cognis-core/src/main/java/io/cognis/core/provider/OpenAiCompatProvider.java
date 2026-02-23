package io.cognis.core.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.model.MessageRole;
import io.cognis.core.model.ToolCall;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public final class OpenAiCompatProvider implements LlmProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String name;
    private final String apiKey;
    private final HttpUrl apiBase;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final Map<String, String> extraHeaders;
    private final int maxAttempts;

    public OpenAiCompatProvider(
        String name,
        String apiKey,
        String apiBase,
        Map<String, String> extraHeaders
    ) {
        this(name, apiKey, apiBase, extraHeaders, 3);
    }

    public OpenAiCompatProvider(
        String name,
        String apiKey,
        String apiBase,
        Map<String, String> extraHeaders,
        int maxAttempts
    ) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = HttpUrl.get(Objects.requireNonNull(apiBase, "apiBase must not be null"));
        this.extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(90))
            .writeTimeout(Duration.ofSeconds(20))
            .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
        if (apiKey.isBlank()) {
            return new LlmResponse("Error calling LLM: missing API key for provider " + name, List.of(), Map.of());
        }

        long delayMs = 250;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Request request = buildRequest(model, messages, tools);
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() == null ? "" : response.body().string();
                        boolean retryable = response.code() == 429 || response.code() >= 500;
                        if (retryable && attempt < maxAttempts) {
                            sleep(delayMs);
                            delayMs = Math.min(delayMs * 2, 2000);
                            continue;
                        }
                        return new LlmResponse(
                            "Error calling LLM: HTTP " + response.code() + " " + errorBody,
                            List.of(),
                            Map.of("http_status", response.code())
                        );
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        return new LlmResponse("", List.of(), Map.of());
                    }

                    String contentType = response.header("Content-Type", "");
                    if (contentType.contains("text/event-stream")) {
                        return parseSse(body.source());
                    }
                    return parseJson(body.string());
                }
            } catch (IOException ioe) {
                if (attempt < maxAttempts) {
                    sleep(delayMs);
                    delayMs = Math.min(delayMs * 2, 2000);
                    continue;
                }
                return new LlmResponse("Error calling LLM: " + ioe.getMessage(), List.of(), Map.of());
            } catch (Exception e) {
                return new LlmResponse("Error calling LLM: " + e.getMessage(), List.of(), Map.of());
            }
        }
        return new LlmResponse("Error calling LLM: exhausted retries", List.of(), Map.of());
    }

    private Request buildRequest(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", toWireMessages(messages));
        payload.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        RequestBody body = RequestBody.create(mapper.writeValueAsString(payload), JSON);

        Request.Builder builder = new Request.Builder()
            .url(completionsUrl())
            .post(body)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream");

        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private HttpUrl completionsUrl() {
        return apiBase.newBuilder()
            .addPathSegment("chat")
            .addPathSegment("completions")
            .build();
    }

    private List<Map<String, Object>> toWireMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", toRoleValue(message.role()));
            row.put("content", message.content());
            if (message.role() == MessageRole.ASSISTANT && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                row.put("tool_calls", toWireToolCalls(message.toolCalls()));
            }
            if (message.role() == MessageRole.TOOL && message.toolCallId() != null && !message.toolCallId().isBlank()) {
                row.put("tool_call_id", message.toolCallId());
            }
            wire.add(row);
        }
        return wire;
    }

    private List<Map<String, Object>> toWireToolCalls(List<ToolCall> toolCalls) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall call = toolCalls.get(i);
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.name());
            function.put("arguments", toArgumentsJson(call.arguments()));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", call.id() == null || call.id().isBlank() ? "call_" + i : call.id());
            item.put("type", "function");
            item.put("function", function);
            wire.add(item);
        }
        return wire;
    }

    private String toArgumentsJson(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String toRoleValue(MessageRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    private LlmResponse parseJson(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText("");
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
        Map<String, Object> usage = usageAsMap(root.path("usage"));
        return new LlmResponse(content, toolCalls, usage);
    }

    private LlmResponse parseSse(BufferedSource source) throws IOException {
        StringBuilder content = new StringBuilder();
        Map<String, ToolCallBuffer> toolBuffers = new LinkedHashMap<>();
        Map<Integer, String> toolIdsByIndex = new LinkedHashMap<>();
        Map<String, Object> usage = Map.of();

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null || line.isBlank() || !line.startsWith("data:")) {
                continue;
            }

            String payload = line.substring(5).trim();
            if (payload.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(payload)) {
                break;
            }

            JsonNode event = mapper.readTree(payload);
            if (event.has("usage")) {
                usage = usageAsMap(event.path("usage"));
            }

            for (JsonNode choice : event.path("choices")) {
                JsonNode delta = choice.path("delta");
                if (delta.has("content") && !delta.path("content").isNull()) {
                    content.append(delta.path("content").asText(""));
                }
                collectToolCalls(delta.path("tool_calls"), toolBuffers, toolIdsByIndex);
            }
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (Map.Entry<String, ToolCallBuffer> entry : toolBuffers.entrySet()) {
            ToolCallBuffer buffer = entry.getValue();
            Map<String, Object> arguments = parseArguments(buffer.arguments.toString());
            toolCalls.add(new ToolCall(entry.getKey(), buffer.name, arguments));
        }

        return new LlmResponse(content.toString(), toolCalls, usage);
    }

    private List<ToolCall> parseToolCalls(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText("");
            JsonNode function = item.path("function");
            String name = function.path("name").asText("");
            Map<String, Object> args;
            JsonNode argsNode = function.path("arguments");
            if (argsNode.isTextual()) {
                args = parseArguments(argsNode.asText("{}"));
            } else {
                args = mapper.convertValue(argsNode, new TypeReference<Map<String, Object>>() {
                });
            }
            toolCalls.add(new ToolCall(id, name, args));
        }
        return toolCalls;
    }

    private void collectToolCalls(
        JsonNode toolCallsNode,
        Map<String, ToolCallBuffer> buffers,
        Map<Integer, String> toolIdsByIndex
    ) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return;
        }
        for (JsonNode toolCall : toolCallsNode) {
            int index = toolCall.path("index").asInt(-1);
            String id = toolCall.path("id").asText();
            if (id != null && !id.isBlank() && index >= 0) {
                toolIdsByIndex.put(index, id);
            }
            if ((id == null || id.isBlank()) && index >= 0 && toolIdsByIndex.containsKey(index)) {
                id = toolIdsByIndex.get(index);
            }
            if (id == null || id.isBlank()) {
                index = Math.max(index, 0);
                id = "call_" + index;
            }

            ToolCallBuffer buffer = buffers.computeIfAbsent(id, ignored -> new ToolCallBuffer());
            JsonNode function = toolCall.path("function");
            if (function.has("name")) {
                String name = function.path("name").asText("");
                if (!name.isBlank()) {
                    buffer.name = name;
                }
            }
            if (function.has("arguments")) {
                String argChunk = function.path("arguments").asText("");
                if (!argChunk.isEmpty()) {
                    buffer.arguments.append(argChunk);
                }
            }
        }
    }

    private Map<String, Object> usageAsMap(JsonNode usage) {
        if (usage == null || usage.isMissingNode() || usage.isNull()) {
            return Map.of();
        }
        return mapper.convertValue(usage, new TypeReference<Map<String, Object>>() {
        });
    }

    private Map<String, Object> parseArguments(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return Map.of();
            }
            return mapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static final class ToolCallBuffer {
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
