package io.cognis.mcp.server.provider;

import io.cognis.mcp.server.config.ProviderConfig;
import io.cognis.mcp.server.http.ProviderHttpClient;
import java.util.List;

public final class TwilioProvider extends AbstractHttpIntegrationProvider {
    public TwilioProvider(ProviderConfig config, ProviderHttpClient httpClient) {
        super(
            config,
            httpClient,
            List.of(
                new ProviderOperation("twilio.send_sms", "Send SMS via Twilio", "POST", "/Accounts/%ACCOUNT_ID%/Messages.json", true),
                new ProviderOperation("twilio.make_call", "Create voice call via Twilio", "POST", "/Accounts/%ACCOUNT_ID%/Calls.json", true)
            )
        );
    }

    @Override
    public String name() {
        return "twilio";
    }
}
