package io.cognis.core.integration.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class McpToolClient implements McpInvoker {
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public McpToolClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder().callTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
    public Map<String, Object> listTools() throws Exception {
        Request request = new Request.Builder()
            .url(baseUrl + "/mcp/tools")
            .get()
            .build();
        return execute(request);
    }

    @Override
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws Exception {
        Map<String, Object> payload = Map.of(
            "name", toolName,
            "arguments", arguments == null ? Map.of() : arguments
        );

        Request request = new Request.Builder()
            .url(baseUrl + "/mcp/call")
            .post(RequestBody.create(mapper.writeValueAsString(payload), JSON))
            .build();

        return execute(request);
    }

    private String normalizeBaseUrl(String value) {
        String raw = (value == null || value.isBlank()) ? "http://127.0.0.1:8791" : value.trim();
        if (raw.endsWith("/")) {
            return raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private Map<String, Object> execute(Request request) throws Exception {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "{}" : response.body().string();
            Map<String, Object> parsed = mapper.readValue(body, new TypeReference<>() {});
            parsed.put("http_status", response.code());
            parsed.put("http_ok", response.isSuccessful());
            return parsed;
        }
    }
}
