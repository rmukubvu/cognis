package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class AmazonProvider extends AbstractHttpIntegrationProvider {
    public AmazonProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("amazon.search_items", "Search Amazon items", "GET", "/items/search", false),
                new ProviderOperation("amazon.create_order", "Create Amazon order", "POST", "/orders", true)
            )
        );
    }

    @Override
    public String name() {
        return "amazon";
    }
}
