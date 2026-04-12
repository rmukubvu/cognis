package io.cognis.core.stratus;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.Map;

/**
 * Drop-in replacement for {@link io.cognis.core.tool.impl.WebTool} that routes
 * every HTTP fetch and search through StratusOS {@code POST /syscall}.
 *
 * <p>Benefits over the local WebTool:
 * <ul>
 *   <li>Network egress policy — StratusOS can restrict which hosts are reachable</li>
 *   <li>SSRF protection at kernel level — not just application-layer pattern matching</li>
 *   <li>Audit trail — every outbound URL is recorded in the immutable ledger</li>
 *   <li>Per-agent rate limits — StratusOS rate limits apply before the request leaves the box</li>
 * </ul>
 *
 * <p>Intent formats sent to /syscall:
 * <ul>
 *   <li>{@code fetch https://example.com} — for action="fetch"</li>
 *   <li>{@code search query text} — for action="search"</li>
 * </ul>
 *
 * <p>StratusOS resolves fetch/search as {@code ACTION_NETWORK_OUT}. The agent session
 * must hold {@code CAP_NETWORK_OUT} or the call is denied.
 */
public final class StratusPolicyWebTool implements Tool {

    private final StratusClient stratus;

    public StratusPolicyWebTool(StratusClient stratus) {
        this.stratus = stratus;
    }

    @Override
    public String name() {
        return "web";
    }

    @Override
    public String description() {
        return "Web fetch and search via StratusOS policy enforcement (audited, rate-limited)";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of("type", "string", "enum", new String[]{"fetch", "search"},
                    "description", "fetch: retrieve a URL; search: web search query"),
                "url",   Map.of("type", "string", "description", "URL to fetch (action=fetch)"),
                "query", Map.of("type", "string", "description", "Search query (action=search)"),
                "count", Map.of("type", "integer", "description", "Max results for search (default 5)")
            ),
            "required", new String[]{"action"}
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = String.valueOf(input.getOrDefault("action", "")).trim();

        return switch (action) {
            case "fetch" -> {
                String url = String.valueOf(input.getOrDefault("url", "")).trim();
                if (url.isBlank()) yield "Error: url is required for action=fetch";
                yield stratus.syscall("fetch " + url);
            }
            case "search" -> {
                String query = String.valueOf(input.getOrDefault("query", "")).trim();
                if (query.isBlank()) yield "Error: query is required for action=search";
                int count = Integer.parseInt(String.valueOf(input.getOrDefault("count", "5")));
                yield stratus.syscall("search \"" + query + "\" count=" + count);
            }
            default -> "Error: unsupported action: " + action + " (use fetch or search)";
        };
    }
}
