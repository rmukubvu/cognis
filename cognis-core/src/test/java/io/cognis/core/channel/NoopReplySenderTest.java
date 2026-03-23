package io.cognis.core.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class NoopReplySenderTest {

    private final NoopReplySender sender = new NoopReplySender();

    @Test
    void supportsWhatsApp() {
        assertThat(sender.supports("whatsapp")).isTrue();
    }

    @Test
    void supportsSms() {
        assertThat(sender.supports("sms")).isTrue();
    }

    @Test
    void supportsAnyChannel() {
        assertThat(sender.supports("signal")).isTrue();
        assertThat(sender.supports("telegram")).isTrue();
    }

    @Test
    void sendDoesNotThrow() {
        assertThatCode(() -> sender.send("+27821234567", "Hello farmer!", "whatsapp"))
            .doesNotThrowAnyException();
    }

    @Test
    void sendWithEmptyMessageDoesNotThrow() {
        assertThatCode(() -> sender.send("+27821234567", "", "sms"))
            .doesNotThrowAnyException();
    }

    @Test
    void sendWithLongMessageDoesNotThrow() {
        String longMessage = "A".repeat(2_000);
        assertThatCode(() -> sender.send("+27821234567", longMessage, "whatsapp"))
            .doesNotThrowAnyException();
    }
}
