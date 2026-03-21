package io.cognis.core.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EmbeddingProvider} that calls any OpenAI-compatible {@code /embeddings} endpoint.
 *
 * <p>Works with OpenRouter ({@code openai/text-embedding-3-small}), OpenAI, and any other
 * provider that follows the {@code POST /v1/embeddings} spec.
 *
 * <p>On failure (network error, bad response) it falls back to {@link HashEmbeddingProvider}
 * so recall continues to work without crashing the agent loop.
 *
 * <h2>OpenRouter example</h2>
 * <pre>{@code
 * new OpenAiCompatEmbeddingProvider(
 *     "https://openrouter.ai/api/v1/embeddings",
 *     apiKey,
 *     "openai/text-embedding-3-small"
 * )
 * }</pre>
 */
public final class OpenAiCompatEmbeddingProvider implements EmbeddingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatEmbeddingProvider.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final HashEmbeddingProvider fallback;

    public OpenAiCompatEmbeddingProvider(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint;
        this.apiKey   = apiKey;
        this.model    = model;
        this.http     = new OkHttpClient();
        this.mapper   = new ObjectMapper();
        this.fallback = new HashEmbeddingProvider();
    }

    @Override
    public List<Double> embed(String text) throws IOException {
        String body = mapper.writeValueAsString(Map.of("model", model, "input", text));
        Request request = new Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body, JSON))
            .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOG.warn("Embedding API returned {}, falling back to hash embedding", response.code());
                return fallback.embed(text);
            }
            String responseBody = response.body() == null ? "" : response.body().string();
            return parseEmbedding(responseBody, text);
        } catch (Exception e) {
            LOG.warn("Embedding API call failed ({}), falling back to hash embedding", e.getMessage());
            return fallback.embed(text);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Double> parseEmbedding(String responseBody, String originalText) {
        try {
            Map<String, Object> parsed = mapper.readValue(responseBody, MAP_TYPE);
            List<?> data = (List<?>) parsed.get("data");
            if (data == null || data.isEmpty()) {
                return fallback.embed(originalText);
            }
            Map<?, ?> first = (Map<?, ?>) data.get(0);
            List<?> rawVector = (List<?>) first.get("embedding");
            if (rawVector == null || rawVector.isEmpty()) {
                return fallback.embed(originalText);
            }
            return rawVector.stream()
                .map(v -> v instanceof Number n ? n.doubleValue() : 0.0)
                .toList();
        } catch (Exception e) {
            LOG.warn("Failed to parse embedding response, falling back to hash embedding");
            return fallback.embed(originalText);
        }
    }
}
