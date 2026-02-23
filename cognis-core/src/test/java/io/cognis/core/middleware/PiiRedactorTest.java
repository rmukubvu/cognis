package io.cognis.core.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    @Test
    void shouldRedactEmailPhoneAndIp() {
        PiiRedactor redactor = new PiiRedactor();
        String input = "email me at a@b.com or call 555-123-4567 from 192.168.1.9";

        String output = redactor.redact(input);

        assertThat(output).contains("[REDACTED_EMAIL]");
        assertThat(output).contains("[REDACTED_PHONE]");
        assertThat(output).contains("[REDACTED_IP]");
    }
}
