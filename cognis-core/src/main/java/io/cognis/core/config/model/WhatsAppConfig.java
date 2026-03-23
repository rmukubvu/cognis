package io.cognis.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuration for the outbound WhatsApp / SMS reply channel.
 *
 * <h2>Twilio example</h2>
 * <pre>{@code
 * "whatsapp": {
 *   "provider":    "twilio",
 *   "accountSid":  "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
 *   "authToken":   "your_auth_token",
 *   "fromNumber":  "+14155238886"
 * }
 * }</pre>
 *
 * <h2>Meta Cloud API example</h2>
 * <pre>{@code
 * "whatsapp": {
 *   "provider":       "meta",
 *   "accessToken":    "EAAxxxxxxxxx...",
 *   "phoneNumberId":  "123456789012345"
 * }
 * }</pre>
 *
 * <p>Omit the block or set {@code "provider": "noop"} to disable outbound replies
 * (the agent will still process inbound messages and log the response).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppConfig(
    String provider,
    String accountSid,
    String authToken,
    String fromNumber,
    String accessToken,
    String phoneNumberId
) {

    public static WhatsAppConfig defaults() {
        return new WhatsAppConfig("noop", "", "", "", "", "");
    }

    /** Returns true if provider is "twilio" (case-insensitive). */
    public boolean isTwilio() {
        return "twilio".equalsIgnoreCase(provider);
    }

    /** Returns true if provider is "meta" (case-insensitive). */
    public boolean isMeta() {
        return "meta".equalsIgnoreCase(provider);
    }

    /** Returns true if this config has sufficient credentials to send real messages. */
    public boolean configured() {
        if (isTwilio()) {
            return notBlank(accountSid) && notBlank(authToken) && notBlank(fromNumber);
        }
        if (isMeta()) {
            return notBlank(accessToken) && notBlank(phoneNumberId);
        }
        return false;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
