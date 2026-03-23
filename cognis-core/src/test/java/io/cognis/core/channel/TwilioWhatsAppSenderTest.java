package io.cognis.core.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TwilioWhatsAppSenderTest {

    private TwilioWhatsAppSender sender() {
        return new TwilioWhatsAppSender("AC123", "token123", "+14155238886");
    }

    @Test
    void supportsWhatsApp() {
        assertThat(sender().supports("whatsapp")).isTrue();
    }

    @Test
    void supportsSms() {
        assertThat(sender().supports("sms")).isTrue();
    }

    @Test
    void doesNotSupportUnknownChannel() {
        assertThat(sender().supports("signal")).isFalse();
        assertThat(sender().supports(null)).isFalse();
    }

    @Test
    void supportsIsCaseInsensitive() {
        assertThat(sender().supports("WhatsApp")).isTrue();
        assertThat(sender().supports("SMS")).isTrue();
    }

    @Test
    void sendThrowsOnBlankPhone() {
        assertThatThrownBy(() -> sender().send("", "Hello", "whatsapp"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("toPhone");
    }

    @Test
    void sendThrowsOnNullPhone() {
        assertThatThrownBy(() -> sender().send(null, "Hello", "whatsapp"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("toPhone");
    }

    @Test
    void sendWithEmptyMessageSkipsWithoutThrowing() throws Exception {
        // Empty message should be skipped (logged), not throw — even with invalid credentials
        // because we check length before hitting the network
        TwilioWhatsAppSender s = new TwilioWhatsAppSender("AC_INVALID", "bad_token", "+10000000000");
        // Should log and return without network call
        s.send("+27821234567", "", "whatsapp");
    }
}
