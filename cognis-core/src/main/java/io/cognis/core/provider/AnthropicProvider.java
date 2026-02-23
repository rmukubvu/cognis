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

public final class AnthropicProvider implements LlmProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String name;
    private final String apiKey;
    private final HttpUrl apiBase;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final int maxAttempts;

    public AnthropicProvider(String name, String apiKey, String apiBase) {
        this(name, apiKey, apiBase, 3);
    }

    public AnthropicProvider(String name, String apiKey, String apiBase, int maxAttempts) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.apiBase = HttpUrl.get(Objects.requireNonNull(apiBase, "apiBase must not be null"));
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

                    if (response.body() == null) {
                        return new LlmResponse("", List.of(), Map.of());
                    }
                    return parseResponse(response.body().string());
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
        payload.put("max_tokens", 4096);
        payload.put("messages", toWireMessages(messages));

        String systemPrompt = extractSystemPrompt(messages);
        if (!systemPrompt.isBlank()) {
            payload.put("system", systemPrompt);
        }

        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", toAnthropicTools(tools));
        }

        RequestBody body = RequestBody.create(mapper.writeValueAsString(payload), JSON);
        return new Request.Builder()
            .url(messagesUrl())
            .post(body)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .build();
    }

    private HttpUrl messagesUrl() {
        return apiBase.newBuilder()
            .addPathSegment("messages")
            .build();
    }

    private List<Map<String, Object>> toWireMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> wire = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.role() == MessageRole.SYSTEM) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("role", message.role() == MessageRole.ASSISTANT ? "assistant" : "user");

            if (message.role() == MessageRole.TOOL) {
                row.put("content", List.of(Map.of(
                    "type", "tool_result",
                    "tool_use_id", message.toolCallId() == null ? "" : message.toolCallId(),
                    "content", message.content()
                )));
            } else if (message.role() == MessageRole.ASSISTANT && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                List<Map<String, Object>> content = new ArrayList<>();
                if (message.content() != null && !message.content().isBlank()) {
                    content.add(Map.of(
                        "type", "text",
                        "text", message.content()
                    ));
                }
                for (ToolCall call : message.toolCalls()) {
                    content.add(Map.of(
                        "type", "tool_use",
                        "id", call.id() == null ? "" : call.id(),
                        "name", call.name(),
                        "input", call.arguments() == null ? Map.of() : call.arguments()
                    ));
                }
                row.put("content", content);
            } else {
                row.put("content", message.content());
            }
            wire.add(row);
        }
        return wire;
    }

    private String extractSystemPrompt(List<ChatMessage> messages) {
        return messages.stream()
            .filter(m -> m.role() == MessageRole.SYSTEM)
            .map(ChatMessage::content)
            .reduce("", (a, b) -> a.isBlank() ? b : a + "\n\n" + b);
    }

    private List<Map<String, Object>> toAnthropicTools(List<Map<String, Object>> tools) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (!(fn instanceof Map<?, ?> fnMap)) {
                continue;
            }
            Object name = fnMap.get("name");
            if (!(name instanceof String s) || s.isBlank()) {
                continue;
            }
            Object description = fnMap.containsKey("description") ? fnMap.get("description") : "";
            Object parameters = fnMap.containsKey("parameters")
                ? fnMap.get("parameters")
                : Map.of("type", "object", "properties", Map.of());
            mapped.add(Map.of(
                "name", s,
                "description", String.valueOf(description),
                "input_schema", parameters
            ));
        }
        return mapped;
    }

    private LlmResponse parseResponse(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder content = new StringBuilder();

        for (JsonNode item : root.path("content")) {
            String type = item.path("type").asText("");
            if ("text".equals(type)) {
                content.append(item.path("text").asText(""));
            }
            if ("tool_use".equals(type)) {
                Map<String, Object> input = mapper.convertValue(item.path("input"), new TypeReference<Map<String, Object>>() {
                });
                toolCalls.add(new ToolCall(
                    item.path("id").asText(""),
                    item.path("name").asText(""),
                    input
                ));
            }
        }

        Map<String, Object> usage = mapper.convertValue(root.path("usage"), new TypeReference<Map<String, Object>>() {
        });
        return new LlmResponse(content.toString(), toolCalls, usage);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
