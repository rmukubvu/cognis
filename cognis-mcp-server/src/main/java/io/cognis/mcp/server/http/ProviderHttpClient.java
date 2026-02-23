package io.cognis.mcp.server.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class ProviderHttpClient {
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public ProviderHttpClient(OkHttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public Map<String, Object> send(
        String method,
        String baseUrl,
        String path,
        Map<String, String> query,
        Map<String, String> headers,
        Map<String, Object> body
    ) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
        }

        RequestBody requestBody = requiresBody(method)
            ? RequestBody.create(mapper.writeValueAsString(body), JSON)
            : null;

        Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        Request request = requestBuilder.method(method, requestBody).build();

        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "{}" : response.body().string();
            Map<String, Object> payload = parseJsonBody(raw);
            payload.put("http_status", response.code());
            payload.put("ok", response.isSuccessful());
            return payload;
        }
    }

    public Map<String, Object> sendForm(
        String method,
        String baseUrl,
        String path,
        Map<String, String> query,
        Map<String, String> headers,
        Map<String, String> formFields
    ) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
        }

        FormBody.Builder formBody = new FormBody.Builder();
        for (Map.Entry<String, String> field : formFields.entrySet()) {
            formBody.add(field.getKey(), field.getValue());
        }

        Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        Request request = requestBuilder.method(method, formBody.build()).build();

        try (Response response = client.newCall(request).execute()) {
            String raw = response.body() == null ? "{}" : response.body().string();
            Map<String, Object> payload = parseJsonBody(raw);
            payload.put("http_status", response.code());
            payload.put("ok", response.isSuccessful());
            return payload;
        }
    }

    private boolean requiresBody(String method) {
        return "POST".equalsIgnoreCase(method)
            || "PUT".equalsIgnoreCase(method)
            || "PATCH".equalsIgnoreCase(method);
    }

    private Map<String, Object> parseJsonBody(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of("raw", raw);
        }
    }
}
