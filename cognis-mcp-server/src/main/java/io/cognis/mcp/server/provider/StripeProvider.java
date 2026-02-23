package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class StripeProvider extends AbstractHttpIntegrationProvider {
    public StripeProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("stripe.create_payment_intent", "Create a Stripe payment intent", "POST", "/payment_intents", true),
                new ProviderOperation("stripe.refund_payment", "Create a Stripe refund", "POST", "/refunds", true)
            )
        );
    }

    @Override
    public String name() {
        return "stripe";
    }
}
