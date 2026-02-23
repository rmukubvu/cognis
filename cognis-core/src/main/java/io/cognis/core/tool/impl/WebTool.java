package io.cognis.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;

public final class WebTool implements Tool {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String braveApiKey;
    private final int defaultMaxResults;

    public WebTool(String braveApiKey, int defaultMaxResults) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
        this.braveApiKey = braveApiKey == null ? "" : braveApiKey;
        this.defaultMaxResults = Math.max(1, defaultMaxResults);
    }

    @Override
    public String name() {
        return "web";
    }

    @Override
    public String description() {
        return "Web fetch and search with SSRF protections";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        try {
            return switch (action) {
                case "fetch" -> fetch(String.valueOf(input.getOrDefault("url", "")),
                    Integer.parseInt(String.valueOf(input.getOrDefault("maxChars", "15000"))));
                case "search" -> search(
                    String.valueOf(input.getOrDefault("query", "")),
                    Integer.parseInt(String.valueOf(input.getOrDefault("count", String.valueOf(defaultMaxResults))))
                );
                default -> "Error: unsupported action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String fetch(String url, int maxChars) throws Exception {
        if (url.isBlank()) {
            return "Error: url is required";
        }

        URI uri = URI.create(url);
        validateUri(uri);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "cognis/0.1")
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String contentType = response.headers().firstValue("content-type").orElse("");

        String body = response.body() == null ? "" : response.body();
        String text = contentType.contains("text/html") ? Jsoup.parse(body).text() : body;
        text = text.length() > maxChars ? text.substring(0, maxChars) : text;

        return "URL: " + uri + "\nStatus: " + response.statusCode() + "\n\n" + text;
    }

    private String search(String query, int count) throws Exception {
        if (query.isBlank()) {
            return "Error: query is required";
        }
        if (braveApiKey.isBlank()) {
            return "Error: BRAVE_API_KEY not configured";
        }

        int capped = Math.min(Math.max(count, 1), 10);
        URI uri = URI.create("https://api.search.brave.com/res/v1/web/search?q=" + encode(query) + "&count=" + capped);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("X-Subscription-Token", braveApiKey)
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return "Error: web search HTTP " + response.statusCode();
        }

        return formatSearchResults(response.body(), query);
    }

    private String formatSearchResults(String body, String query) throws IOException {
        JsonNode root = mapper.readTree(body);
        JsonNode results = root.path("web").path("results");
        if (!results.isArray() || results.isEmpty()) {
            return "No results for: " + query;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Results for: " + query);
        int index = 1;
        for (JsonNode result : results) {
            lines.add(index + ". " + result.path("title").asText(""));
            lines.add("   " + result.path("url").asText(""));
            String description = result.path("description").asText("");
            if (!description.isBlank()) {
                lines.add("   " + description);
            }
            index++;
        }
        return String.join("\n", lines);
    }

    private void validateUri(URI uri) throws Exception {
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL host is required");
        }

        InetAddress address = InetAddress.getByName(host);
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Private or loopback addresses are blocked");
        }

        String ip = address.getHostAddress();
        if (ip.startsWith("169.254.")) {
            throw new IllegalArgumentException("Link-local addresses are blocked");
        }
    }

    private String encode(String value) {
        return value.replace(" ", "%20");
    }
}
