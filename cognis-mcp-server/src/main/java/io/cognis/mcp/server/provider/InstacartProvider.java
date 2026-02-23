package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class InstacartProvider extends AbstractHttpIntegrationProvider {
    public InstacartProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("instacart.search_store", "Search Instacart stores", "GET", "/stores/search", false),
                new ProviderOperation("instacart.create_order", "Create Instacart order", "POST", "/orders", true)
            )
        );
    }

    @Override
    public String name() {
        return "instacart";
    }
}
