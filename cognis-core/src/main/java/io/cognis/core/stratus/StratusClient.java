package io.cognis.core.stratus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for StratusOS kernel APIs.
 *
 * <p>Covers three surfaces used by Cognis integration:
 * <ul>
 *   <li>{@code POST /syscall}  — policy-enforced action execution (shell, web fetch)</li>
 *   <li>{@code POST /vfs/write} — write a fact/memory into StratusOS VFS</li>
 *   <li>{@code POST /vfs/search} — semantic search over VFS contents</li>
 * </ul>
 *
 * <p>Configure via environment variables (read once at startup):
 * <pre>
 *   STRATUS_GATEWAY_URL=http://localhost:7070
 *   STRATUS_AUTH_TOKEN=stratus-dev-token-change-me
 *   STRATUS_SESSION_ID=<vertical-name>         (optional — set by SubprocessRunner)
 * </pre>
 */
public final class StratusClient {

    private final String gatewayUrl;
    private final String authToken;
    private final String sessionId;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public StratusClient(String gatewayUrl, String authToken, String sessionId) {
        this.gatewayUrl = gatewayUrl.replaceAll("/+$", "");
        this.authToken  = authToken;
        this.sessionId  = sessionId != null ? sessionId : "cognis";
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Factory that reads configuration from environment variables.
     * Returns {@code null} if {@code STRATUS_GATEWAY_URL} is not set.
     */
    public static StratusClient fromEnv() {
        String url   = System.getenv("STRATUS_GATEWAY_URL");
        if (url == null || url.isBlank()) {
            return null;
        }
        String token  = System.getenv().getOrDefault("STRATUS_AUTH_TOKEN", "stratus-dev-token-change-me");
        String sessId = System.getenv("STRATUS_SESSION_ID");
        return new StratusClient(url, token, sessId);
    }

    // -------------------------------------------------------------------------
    // /syscall  — policy-enforced execution
    // -------------------------------------------------------------------------

    /**
     * Send an intent to StratusOS /syscall and return the result text.
     *
     * @param intent  natural-language or structured intent string (e.g. "execute: ls -la")
     * @return result text from the kernel, or an error string prefixed with "Error:"
     */
    public String syscall(String intent) {
        ObjectNode body = mapper.createObjectNode();
        body.put("intent",     intent);
        body.put("session_id", sessionId);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/syscall"))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = mapper.readTree(resp.body());
                // StratusOS returns {"result":"...","status":"ok",...}
                if (json.has("result")) {
                    return json.get("result").asText();
                }
                // Fallback: return raw body
                return resp.body();
            } else if (resp.statusCode() == 403) {
                JsonNode json = tryParseJson(resp.body());
                String reason = json != null && json.has("message")
                    ? json.get("message").asText()
                    : resp.body();
                return "Error: StratusOS denied — " + reason;
            } else {
                return "Error: StratusOS /syscall returned HTTP " + resp.statusCode() + ": " + resp.body();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: StratusOS unreachable — " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // /vfs/write  — persist a memory into StratusOS VFS
    // -------------------------------------------------------------------------

    /**
     * Write {@code content} to the VFS at {@code path}.
     *
     * @param path    VFS path, e.g. {@code /verticals/humanitarian/memory/facts.md}
     * @param content text content to store
     * @throws IOException if the write fails or StratusOS returns an error
     */
    public void vfsWrite(String path, String content) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("path",    path);
        body.put("content", content);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/vfs/write"))
            .header("Content-Type",  "application/json")
            .header("Authorization", "Bearer " + authToken)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .timeout(Duration.ofSeconds(15))
            .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201 && resp.statusCode() != 204) {
                throw new IOException("VFS write failed — HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("VFS write interrupted", e);
        }
    }

    /**
     * Read text content from a VFS path.
     *
     * @param path VFS path to read
     * @return file content, or empty string if not found
     * @throws IOException if the request fails
     */
    public String vfsRead(String path) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(gatewayUrl + "/vfs/read?path=" + URI.create(path).getRawPath()))
            .header("Authorization", "Bearer " + authToken)
            .GET()
            .timeout(Duration.ofSeconds(15))
            .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                return "";
            }
            if (resp.statusCode() != 200) {
                throw new IOException("VFS read failed — HTTP " + resp.statusCode());
            }
            JsonNode json = tryParseJson(resp.body());
            if (json != null && json.has("content")) {
                return json.get("content").asText();
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("VFS read interrupted", e);
        }
    }

    /**
     * Semantic search over VFS content.
     *
     * @param query      natural-language query
     * @param maxResults maximum number of matching chunks to return
     * @return JSON array string of matching results from StratusOS, or empty array on failure
     */
    public String vfsSearch(String query, int maxResults) {
        ObjectNode body = mapper.createObjectNode();
        body.put("query",      query);
        body.put("max_results", maxResults);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(gatewayUrl + "/vfs/search"))
                .header("Content-Type",  "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(20))
                .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            return "[]";
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[]";
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String gatewayUrl()  { return gatewayUrl; }
    public String sessionId()   { return sessionId;  }

    /**
     * Return a new client with a different session ID (for per-conversation tagging).
     */
    public StratusClient withSession(String newSessionId) {
        return new StratusClient(gatewayUrl, authToken, newSessionId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private JsonNode tryParseJson(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
