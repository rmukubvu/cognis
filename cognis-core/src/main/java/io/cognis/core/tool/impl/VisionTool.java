package io.cognis.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class VisionTool implements Tool {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final WorkspaceGuard guard = new WorkspaceGuard();
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public VisionTool(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl == null ? "" : apiUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? "gpt-4o" : model;
        this.client = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(60)).build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "view_image";
    }

    @Override
    public String description() {
        return "Analyze an image/document file via OpenAI-compatible vision API";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        if (apiUrl.isBlank() || apiKey.isBlank()) {
            return "Error: vision is not configured";
        }

        String pathArg = String.valueOf(input.getOrDefault("path", "")).trim();
        if (pathArg.isBlank()) {
            return "Error: path is required";
        }
        String question = String.valueOf(input.getOrDefault("question", "Describe this image in detail.")).trim();

        try {
            Path path = guard.resolve(context, pathArg);
            byte[] bytes = Files.readAllBytes(path);
            String mime = detectMime(path, bytes);
            String b64 = Base64.getEncoder().encodeToString(bytes);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:" + mime + ";base64," + b64)),
                    Map.of("type", "text", "text", question)
                )
            )));
            payload.put("max_tokens", 1024);

            Request request = new Request.Builder()
                .url(apiUrl)
                .post(RequestBody.create(mapper.writeValueAsBytes(payload), JSON))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .build();

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    return "Error: vision API status " + response.code() + ": " + body;
                }

                JsonNode root = mapper.readTree(body);
                return root.path("choices").path(0).path("message").path("content").asText("(empty response)");
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String detectMime(Path path, byte[] bytes) {
        String ext = path.getFileName().toString().toLowerCase();
        if (ext.endsWith(".jpg") || ext.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (ext.endsWith(".png")) {
            return "image/png";
        }
        if (ext.endsWith(".gif")) {
            return "image/gif";
        }
        if (ext.endsWith(".webp")) {
            return "image/webp";
        }
        if (ext.endsWith(".pdf")) {
            return "application/pdf";
        }
        return bytes.length > 0 ? "application/octet-stream" : "application/octet-stream";
    }
}
