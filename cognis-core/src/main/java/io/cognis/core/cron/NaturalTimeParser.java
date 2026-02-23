package io.cognis.core.cron;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NaturalTimeParser {
    private static final Pattern IN_PATTERN = Pattern.compile("^in\\s+(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)$");
    private static final Pattern DAY_AT_PATTERN = Pattern.compile("^(today|tomorrow)(?:\\s+at\\s+(.+))?$");
    private static final DateTimeFormatter DATE_TIME_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public long parseToEpochMs(String expression, Clock clock, ZoneId zoneId) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("time expression is required");
        }

        String normalized = expression.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();

        Matcher inMatcher = IN_PATTERN.matcher(normalized);
        if (inMatcher.matches()) {
            long value = Long.parseLong(inMatcher.group(1));
            String unit = inMatcher.group(2);
            long seconds = switch (unit) {
                case "s", "sec", "secs", "second", "seconds" -> value;
                case "m", "min", "mins", "minute", "minutes" -> value * 60;
                case "h", "hr", "hrs", "hour", "hours" -> value * 3600;
                case "d", "day", "days" -> value * 86400;
                default -> throw new IllegalArgumentException("unsupported time unit: " + unit);
            };
            return now.plusSeconds(seconds).toEpochMilli();
        }

        Matcher dayMatcher = DAY_AT_PATTERN.matcher(normalized);
        if (dayMatcher.matches()) {
            LocalDate baseDate = LocalDateTime.ofInstant(now, zoneId).toLocalDate();
            if ("tomorrow".equals(dayMatcher.group(1))) {
                baseDate = baseDate.plusDays(1);
            }
            LocalTime time = parseTime(dayMatcher.group(2));
            return LocalDateTime.of(baseDate, time).atZone(zoneId).toInstant().toEpochMilli();
        }

        try {
            return Instant.parse(expression.trim()).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(expression.trim(), DATE_TIME_SPACE).atZone(zoneId).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(expression.trim()).atZone(zoneId).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("unable to parse time expression: " + expression);
    }

    private LocalTime parseTime(String token) {
        if (token == null || token.isBlank()) {
            return LocalTime.of(9, 0);
        }

        String value = token.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher meridiem = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$").matcher(value);
        if (meridiem.matches()) {
            int hour = Integer.parseInt(meridiem.group(1));
            int minute = meridiem.group(2) == null ? 0 : Integer.parseInt(meridiem.group(2));
            String ap = meridiem.group(3);
            hour = hour % 12;
            if ("pm".equals(ap)) {
                hour += 12;
            }
            return LocalTime.of(hour, minute);
        }

        Matcher twentyFour = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?$").matcher(value);
        if (twentyFour.matches()) {
            int hour = Integer.parseInt(twentyFour.group(1));
            int minute = twentyFour.group(2) == null ? 0 : Integer.parseInt(twentyFour.group(2));
            return LocalTime.of(hour, minute);
        }

        throw new IllegalArgumentException("invalid time format: " + token);
    }
}
