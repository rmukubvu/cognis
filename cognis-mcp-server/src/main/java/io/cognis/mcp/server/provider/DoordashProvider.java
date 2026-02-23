package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class DoordashProvider extends AbstractHttpIntegrationProvider {
    public DoordashProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("doordash.search_store", "Search DoorDash stores", "GET", "/stores/search", false),
                new ProviderOperation("doordash.create_order", "Create DoorDash order", "POST", "/orders", true)
            )
        );
    }

    @Override
    public String name() {
        return "doordash";
    }
}
