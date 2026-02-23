package io.cognis.core.payment;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record PaymentPolicy(
    String currency,
    long maxPerTxCents,
    long maxDailyCents,
    long maxMonthlyCents,
    long requireConfirmationOverCents,
    List<String> allowedMerchants,
    List<String> allowedCategories,
    String timezone,
    Integer quietHoursStart,
    Integer quietHoursEnd
) {

    public PaymentPolicy {
        currency = normalizeCurrency(currency);
        maxPerTxCents = Math.max(0, maxPerTxCents);
        maxDailyCents = Math.max(0, maxDailyCents);
        maxMonthlyCents = Math.max(0, maxMonthlyCents);
        requireConfirmationOverCents = Math.max(0, requireConfirmationOverCents);
        allowedMerchants = normalizeList(allowedMerchants);
        allowedCategories = normalizeList(allowedCategories);
        timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        quietHoursStart = sanitizeHour(quietHoursStart);
        quietHoursEnd = sanitizeHour(quietHoursEnd);
    }

    public static PaymentPolicy defaults() {
        return new PaymentPolicy(
            "USD",
            10_000,
            20_000,
            100_000,
            2_000,
            List.of(),
            List.of(),
            "UTC",
            null,
            null
        );
    }

    public boolean allowsMerchant(String merchant) {
        if (allowedMerchants.isEmpty()) {
            return true;
        }
        return allowedMerchants.contains(normalizeKey(merchant));
    }

    public boolean allowsCategory(String category) {
        if (allowedCategories.isEmpty()) {
            return true;
        }
        return allowedCategories.contains(normalizeKey(category));
    }

    public boolean inQuietHours(Instant at) {
        if (quietHoursStart == null || quietHoursEnd == null || quietHoursStart.equals(quietHoursEnd)) {
            return false;
        }
        int hour = at.atZone(zoneId()).getHour();
        if (quietHoursStart < quietHoursEnd) {
            return hour >= quietHoursStart && hour < quietHoursEnd;
        }
        return hour >= quietHoursStart || hour < quietHoursEnd;
    }

    public ZoneId zoneId() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    private static String normalizeCurrency(String value) {
        String base = value == null || value.isBlank() ? "USD" : value.trim();
        return base.toUpperCase(Locale.ROOT);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeKey(value);
            if (!normalized.isBlank()) {
                set.add(normalized);
            }
        }
        return List.copyOf(set);
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Integer sanitizeHour(Integer value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > 23) {
            return null;
        }
        return value;
    }
}
