package io.cognis.core.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.model.ChatMessage;
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

public final class CodexResponsesProvider implements LlmProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String name;
    private final String accessToken;
    private final String accountId;
    private final HttpUrl endpoint;
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public CodexResponsesProvider(String name, String accessToken, String accountId, String endpoint) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.accessToken = accessToken == null ? "" : accessToken;
        this.accountId = accountId == null ? "" : accountId;
        this.endpoint = HttpUrl.get(Objects.requireNonNull(endpoint, "endpoint must not be null"));
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
        if (accessToken.isBlank()) {
            return new LlmResponse("Error calling LLM: missing access token for provider " + name, List.of(), Map.of());
        }

        try {
            Request request = buildRequest(model, messages, tools);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() == null ? "" : response.body().string();
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
        } catch (Exception e) {
            return new LlmResponse("Error calling LLM: " + e.getMessage(), List.of(), Map.of());
        }
    }

    private Request buildRequest(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) throws IOException {
        String normalizedModel = model == null ? "" : model.replace("openai-codex/", "").replace("openai_codex/", "");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", normalizedModel);
        payload.put("stream", true);
        payload.put("tool_choice", "auto");
        payload.put("parallel_tool_calls", true);
        payload.put("store", false);
        payload.put("input", toInput(messages));

        String systemPrompt = messages.stream()
            .filter(m -> m.role().name().equals("SYSTEM"))
            .map(ChatMessage::content)
            .reduce("", (a, b) -> a.isBlank() ? b : a + "\n\n" + b);
        if (!systemPrompt.isBlank()) {
            payload.put("instructions", systemPrompt);
        }

        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", toCodexTools(tools));
        }

        RequestBody body = RequestBody.create(mapper.writeValueAsString(payload), JSON);
        Request.Builder builder = new Request.Builder()
            .url(endpoint)
            .post(body)
            .header("Authorization", "Bearer " + accessToken)
            .header("OpenAI-Beta", "responses=experimental")
            .header("accept", "text/event-stream")
            .header("content-type", "application/json")
            .header("originator", "cognis");

        if (!accountId.isBlank()) {
            builder.header("chatgpt-account-id", accountId);
        }
        return builder.build();
    }

    private List<Map<String, Object>> toInput(List<ChatMessage> messages) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ChatMessage message : messages) {
            switch (message.role()) {
                case SYSTEM -> {
                    // system becomes instructions.
                }
                case USER -> items.add(Map.of(
                    "role", "user",
                    "content", List.of(Map.of("type", "input_text", "text", message.content()))
                ));
                case ASSISTANT -> items.add(Map.of(
                    "type", "message",
                    "role", "assistant",
                    "content", List.of(Map.of("type", "output_text", "text", message.content())),
                    "status", "completed"
                ));
                case TOOL -> items.add(Map.of(
                    "type", "function_call_output",
                    "call_id", message.toolCallId() == null ? "" : message.toolCallId(),
                    "output", message.content()
                ));
            }
        }
        return items;
    }

    private List<Map<String, Object>> toCodexTools(List<Map<String, Object>> tools) {
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
                "type", "function",
                "name", s,
                "description", String.valueOf(description),
                "parameters", parameters
            ));
        }
        return mapped;
    }

    private LlmResponse parseJson(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        String content = root.path("output_text").asText("");
        return new LlmResponse(content, List.of(), Map.of());
    }

    private LlmResponse parseSse(BufferedSource source) throws IOException {
        StringBuilder content = new StringBuilder();
        Map<String, ToolCallBuffer> toolCalls = new LinkedHashMap<>();
        Map<String, Object> usage = Map.of();

        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null || line.isBlank() || !line.startsWith("data:")) {
                continue;
            }
            String payload = line.substring(5).trim();
            if (payload.isBlank()) {
                continue;
            }
            if ("[DONE]".equals(payload)) {
                break;
            }

            JsonNode event = mapper.readTree(payload);
            String type = event.path("type").asText("");

            if (type.contains("output_text.delta")) {
                content.append(event.path("delta").asText(""));
            }

            if ("response.output_item.added".equals(type)) {
                JsonNode item = event.path("item");
                if ("function_call".equals(item.path("type").asText(""))) {
                    String callId = item.path("call_id").asText(item.path("id").asText(""));
                    ToolCallBuffer buffer = toolCalls.computeIfAbsent(callId, key -> new ToolCallBuffer());
                    buffer.name = item.path("name").asText(buffer.name);
                    String args = item.path("arguments").asText("");
                    if (!args.isBlank()) {
                        buffer.arguments.append(args);
                    }
                }
            }

            if (type.contains("function_call_arguments.delta")) {
                String callId = event.path("call_id").asText("");
                ToolCallBuffer buffer = toolCalls.computeIfAbsent(callId, key -> new ToolCallBuffer());
                buffer.arguments.append(event.path("delta").asText(""));
            }

            if (event.has("usage")) {
                usage = mapper.convertValue(event.path("usage"), new TypeReference<Map<String, Object>>() {
                });
            }
        }

        List<ToolCall> parsed = new ArrayList<>();
        for (Map.Entry<String, ToolCallBuffer> entry : toolCalls.entrySet()) {
            parsed.add(new ToolCall(entry.getKey(), entry.getValue().name, parseArguments(entry.getValue().arguments.toString())));
        }

        return new LlmResponse(content.toString(), parsed, usage);
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
}
