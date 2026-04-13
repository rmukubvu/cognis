package io.cognis.core.heartbeat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;

/**
 * Minimal 5-field POSIX cron expression evaluator (UTC).
 *
 * <p>Fields: {@code minute hour day-of-month month day-of-week}
 * <p>Supported syntax per field:
 * <ul>
 *   <li>{@code *}       — every value in range</li>
 *   <li>{@code n}       — specific value</li>
 *   <li>{@code a-b/n}  — range with step (e.g. {@code 0-59/30} = every 30 min)</li>
 *   <li>{@code a-b}     — inclusive range</li>
 *   <li>{@code a-b\/n}  — range with step</li>
 * </ul>
 * Day-of-week: 0 = Sunday … 6 = Saturday (ISO: Mon=1…Sun=7 remapped internally).
 */
public final class CronExpression {

    private static final int MAX_SCAN_MINUTES = 60 * 24 * 366; // 1 year

    private final BitSet minutes;   // 0-59
    private final BitSet hours;     // 0-23
    private final BitSet doms;      // 1-31
    private final BitSet months;    // 1-12
    private final BitSet dows;      // 0-6 (Sun-Sat)

    private CronExpression(BitSet minutes, BitSet hours, BitSet doms, BitSet months, BitSet dows) {
        this.minutes = minutes;
        this.hours   = hours;
        this.doms    = doms;
        this.months  = months;
        this.dows    = dows;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Parse a 5-field cron expression.
     *
     * @param expression e.g. {@code "0 6 * * *"}
     * @throws IllegalArgumentException if the expression cannot be parsed
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }
        String[] fields = expression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                "Cron expression must have exactly 5 fields: '" + expression + "'"
            );
        }
        return new CronExpression(
            parseField(fields[0], 0, 59),
            parseField(fields[1], 0, 23),
            parseField(fields[2], 1, 31),
            parseField(fields[3], 1, 12),
            parseField(fields[4], 0, 6)
        );
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Returns {@code true} if the expression fires at the given minute boundary. */
    public boolean matches(ZonedDateTime dt) {
        int dow = dt.getDayOfWeek().getValue() % 7; // ISO Mon=1..Sun=7 → Sun=0..Sat=6
        return minutes.get(dt.getMinute())
            && hours.get(dt.getHour())
            && doms.get(dt.getDayOfMonth())
            && months.get(dt.getMonthValue())
            && dows.get(dow);
    }

    /**
     * Compute the next execution time strictly after {@code from}.
     * Scans minute-by-minute up to one calendar year ahead.
     *
     * @throws IllegalStateException if no match is found within one year
     */
    public ZonedDateTime nextExecution(ZonedDateTime from) {
        ZonedDateTime candidate = from.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        for (int i = 0; i < MAX_SCAN_MINUTES; i++) {
            if (matches(candidate)) {
                return candidate;
            }
            candidate = candidate.plusMinutes(1);
        }
        throw new IllegalStateException("No execution found within one year for expression");
    }

    // ── Parser internals ─────────────────────────────────────────────────────

    private static BitSet parseField(String field, int min, int max) {
        BitSet bits = new BitSet(max + 1);
        for (String part : field.split(",")) {
            applyPart(bits, part.trim(), min, max);
        }
        return bits;
    }

    private static void applyPart(BitSet bits, String part, int min, int max) {
        if ("*".equals(part)) {
            bits.set(min, max + 1);
            return;
        }

        // step with wildcard: */n
        if (part.startsWith("*/")) {
            int step = parseInt(part.substring(2), part);
            for (int v = min; v <= max; v += step) {
                bits.set(v);
            }
            return;
        }

        // step with start value: n/step (Quartz-compatible, e.g. "0/30" = every 30 from 0)
        int slashOnly = part.indexOf('/');
        if (slashOnly >= 0 && part.indexOf('-') < 0) {
            int start = clamp(parseInt(part.substring(0, slashOnly), part), min, max);
            int step  = parseInt(part.substring(slashOnly + 1), part);
            for (int v = start; v <= max; v += step) {
                bits.set(v);
            }
            return;
        }

        int dashIdx = part.indexOf('-');
        if (dashIdx < 0) {
            // single value
            bits.set(clamp(parseInt(part, part), min, max));
            return;
        }

        // range: a-b or a-b/n
        int slashIdx = part.indexOf('/');
        int step = 1;
        String rangePart = part;
        if (slashIdx > dashIdx) {
            step = parseInt(part.substring(slashIdx + 1), part);
            rangePart = part.substring(0, slashIdx);
        }
        String[] bounds = rangePart.split("-", 2);
        int rangeMin = clamp(parseInt(bounds[0], part), min, max);
        int rangeMax = clamp(parseInt(bounds[1], part), min, max);
        for (int v = rangeMin; v <= rangeMax; v += step) {
            bits.set(v);
        }
    }

    private static int parseInt(String s, String context) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid cron field value '" + s + "' in '" + context + "'");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
