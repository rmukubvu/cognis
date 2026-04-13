package io.cognis.core.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.config.model.WhatsAppConfig;
import org.junit.jupiter.api.Test;

class WhatsAppConfigTest {

    @Test
    void defaultsAreNoop() {
        WhatsAppConfig cfg = WhatsAppConfig.defaults();
        assertThat(cfg.provider()).isEqualTo("noop");
        assertThat(cfg.isTwilio()).isFalse();
        assertThat(cfg.isMeta()).isFalse();
        assertThat(cfg.configured()).isFalse();
    }

    @Test
    void twilioConfiguredWhenAllFieldsPresent() {
        WhatsAppConfig cfg = new WhatsAppConfig("twilio", "ACabc123", "auth_tok", "+14155238886", "", "", "", "");
        assertThat(cfg.isTwilio()).isTrue();
        assertThat(cfg.configured()).isTrue();
    }

    @Test
    void twilioNotConfiguredWhenAccountSidMissing() {
        WhatsAppConfig cfg = new WhatsAppConfig("twilio", "", "auth_tok", "+14155238886", "", "", "", "");
        assertThat(cfg.isTwilio()).isTrue();
        assertThat(cfg.configured()).isFalse();
    }

    @Test
    void twilioNotConfiguredWhenAuthTokenMissing() {
        WhatsAppConfig cfg = new WhatsAppConfig("twilio", "ACabc123", "", "+14155238886", "", "", "", "");
        assertThat(cfg.isTwilio()).isTrue();
        assertThat(cfg.configured()).isFalse();
    }

    @Test
    void metaConfiguredWhenTokenAndPhoneNumberIdPresent() {
        WhatsAppConfig cfg = new WhatsAppConfig("meta", "", "", "", "EAAtoken", "12345678901234", "", "");
        assertThat(cfg.isMeta()).isTrue();
        assertThat(cfg.configured()).isTrue();
    }

    @Test
    void metaNotConfiguredWhenAccessTokenMissing() {
        WhatsAppConfig cfg = new WhatsAppConfig("meta", "", "", "", "", "12345678901234", "", "");
        assertThat(cfg.isMeta()).isTrue();
        assertThat(cfg.configured()).isFalse();
    }

    @Test
    void metaNotConfiguredWhenPhoneNumberIdMissing() {
        WhatsAppConfig cfg = new WhatsAppConfig("meta", "", "", "", "EAAtoken", "", "", "");
        assertThat(cfg.isMeta()).isTrue();
        assertThat(cfg.configured()).isFalse();
    }

    @Test
    void providerCheckIsCaseInsensitive() {
        assertThat(new WhatsAppConfig("TWILIO", "", "", "", "", "", "", "").isTwilio()).isTrue();
        assertThat(new WhatsAppConfig("Twilio", "", "", "", "", "", "", "").isTwilio()).isTrue();
        assertThat(new WhatsAppConfig("META", "", "", "", "", "", "", "").isMeta()).isTrue();
        assertThat(new WhatsAppConfig("Meta", "", "", "", "", "", "", "").isMeta()).isTrue();
    }

    @Test
    void noopProviderIsNeitherTwilioNorMeta() {
        WhatsAppConfig cfg = new WhatsAppConfig("noop", "", "", "", "", "", "", "");
        assertThat(cfg.isTwilio()).isFalse();
        assertThat(cfg.isMeta()).isFalse();
        assertThat(cfg.configured()).isFalse();
    }
}
