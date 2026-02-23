package io.cognis.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cognis.mcp.server.config.McpServerConfig;
import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import io.cognis.mcp.server.provider.AmazonProvider;
import io.cognis.mcp.server.provider.DoordashProvider;
import io.cognis.mcp.server.provider.InstacartProvider;
import io.cognis.mcp.server.provider.IntegrationProvider;
import io.cognis.mcp.server.provider.LyftProvider;
import io.cognis.mcp.server.provider.StripeProvider;
import io.cognis.mcp.server.provider.TwilioProvider;
import io.cognis.mcp.server.provider.UberProvider;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McpServerApplication {
    private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

    public static void main(String[] args) {
        McpServerConfig config = McpServerConfig.fromEnv();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OkHttpClient client = new OkHttpClient.Builder().callTimeout(config.timeout()).build();
        ProviderHttpClient httpClient = new ProviderHttpClient(client, mapper);

        List<IntegrationProvider> providers = List.of(
            new StripeProvider(config.provider("stripe"), httpClient),
            new AmazonProvider(config.provider("amazon"), httpClient),
            new UberProvider(config.provider("uber"), httpClient),
            new LyftProvider(config.provider("lyft"), httpClient),
            new InstacartProvider(config.provider("instacart"), httpClient),
            new DoordashProvider(config.provider("doordash"), httpClient),
            new TwilioProvider(config.provider("twilio"), httpClient)
        );

        logConfiguredProviders(providers, config);

        ToolRouter router = new ToolRouter(providers);
        McpHttpServer server = new McpHttpServer(config.port(), router, mapper);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        log.info("Cognis MCP server listening on port {}", config.port());
    }

    private static void logConfiguredProviders(List<IntegrationProvider> providers, McpServerConfig config) {
        for (IntegrationProvider provider : providers) {
            ProviderConfig providerConfig = config.provider(provider.name());
            log.info("Provider {} configured={} baseUrl={}", provider.name(), providerConfig.configured(), providerConfig.baseUrl());
        }
    }
}
