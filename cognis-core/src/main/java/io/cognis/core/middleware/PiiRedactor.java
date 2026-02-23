package io.cognis.core.middleware;

import java.util.regex.Pattern;

public final class PiiRedactor {
    private static final Pattern EMAIL = Pattern.compile("(?i)([a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,})");
    private static final Pattern PHONE = Pattern.compile("(\\+\\d{1,2}\\s)?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    public String redact(String input) {
        if (input == null || input.isBlank()) {
            return input == null ? "" : input;
        }
        String out = EMAIL.matcher(input).replaceAll("[REDACTED_EMAIL]");
        out = PHONE.matcher(out).replaceAll("[REDACTED_PHONE]");
        out = IPV4.matcher(out).replaceAll("[REDACTED_IP]");
        return out;
    }
}
