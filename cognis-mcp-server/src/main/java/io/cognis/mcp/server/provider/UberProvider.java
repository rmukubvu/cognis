package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class UberProvider extends AbstractHttpIntegrationProvider {
    public UberProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("uber.estimate_ride", "Get Uber ride estimate", "GET", "/estimates/price", false),
                new ProviderOperation("uber.request_ride", "Request Uber ride", "POST", "/requests", true)
            )
        );
    }

    @Override
    public String name() {
        return "uber";
    }
}
