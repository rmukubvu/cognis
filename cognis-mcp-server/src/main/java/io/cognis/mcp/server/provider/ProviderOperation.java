package io.cognis.mcp.server.provider;

public record ProviderOperation(
    String toolName,
    String description,
    String method,
    String path,
    boolean mutating
) {
}
