package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class LyftProvider extends AbstractHttpIntegrationProvider {
    public LyftProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("lyft.estimate_ride", "Get Lyft ride estimate", "GET", "/cost", false),
                new ProviderOperation("lyft.request_ride", "Request Lyft ride", "POST", "/rides", true)
            )
        );
    }

    @Override
    public String name() {
        return "lyft";
    }
}
