package io.cognis.core.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class MetaCloudApiSenderTest {

    private MetaCloudApiSender sender() {
        return new MetaCloudApiSender("123456789", "EAAtest_token");
    }

    @Test
    void supportsWhatsApp() {
        assertThat(sender().supports("whatsapp")).isTrue();
    }

    @Test
    void doesNotSupportSms() {
        // Meta Cloud API is WhatsApp only
        assertThat(sender().supports("sms")).isFalse();
    }

    @Test
    void supportsIsCaseInsensitive() {
        assertThat(sender().supports("WhatsApp")).isTrue();
        assertThat(sender().supports("WHATSAPP")).isTrue();
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
    void sendThrowsWhenChannelIsSms() {
        assertThatThrownBy(() -> sender().send("+27821234567", "Hello", "sms"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("whatsapp only");
    }

    @Test
    void sendWithEmptyMessageSkipsWithoutThrowing() throws Exception {
        // Empty message should skip before hitting the network
        sender().send("+27821234567", "", "whatsapp");
    }

    @Test
    void sendWithNullMessageSkipsWithoutThrowing() throws Exception {
        sender().send("+27821234567", null, "whatsapp");
    }
}
