package io.cognis.core.heartbeat;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronExpressionTest {

    // ── matches ──────────────────────────────────────────────────────────────

    @Test
    void wildcardMatchesEveryMinute() {
        CronExpression expr = CronExpression.parse("* * * * *");
        ZonedDateTime dt = ZonedDateTime.of(2026, 3, 20, 9, 37, 0, 0, ZoneOffset.UTC);
        assertThat(expr.matches(dt)).isTrue();
    }

    @Test
    void dailyAt6amMatchesExactly() {
        CronExpression expr = CronExpression.parse("0 6 * * *");
        ZonedDateTime yes = ZonedDateTime.of(2026, 3, 20, 6, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime no1 = ZonedDateTime.of(2026, 3, 20, 6, 1, 0, 0, ZoneOffset.UTC);
        ZonedDateTime no2 = ZonedDateTime.of(2026, 3, 20, 7, 0, 0, 0, ZoneOffset.UTC);
        assertThat(expr.matches(yes)).isTrue();
        assertThat(expr.matches(no1)).isFalse();
        assertThat(expr.matches(no2)).isFalse();
    }

    @Test
    void every15MinutesMatchesQuarters() {
        CronExpression expr = CronExpression.parse("*/15 * * * *");
        for (int min : new int[]{0, 15, 30, 45}) {
            ZonedDateTime dt = ZonedDateTime.of(2026, 3, 20, 10, min, 0, 0, ZoneOffset.UTC);
            assertThat(expr.matches(dt)).as("should match minute=" + min).isTrue();
        }
        for (int min : new int[]{1, 14, 16, 29, 31, 44, 46}) {
            ZonedDateTime dt = ZonedDateTime.of(2026, 3, 20, 10, min, 0, 0, ZoneOffset.UTC);
            assertThat(expr.matches(dt)).as("should NOT match minute=" + min).isFalse();
        }
    }

    @Test
    void topOfEveryHour() {
        CronExpression expr = CronExpression.parse("0 * * * *");
        ZonedDateTime yes = ZonedDateTime.of(2026, 3, 20, 14, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime no  = ZonedDateTime.of(2026, 3, 20, 14, 1, 0, 0, ZoneOffset.UTC);
        assertThat(expr.matches(yes)).isTrue();
        assertThat(expr.matches(no)).isFalse();
    }

    @Test
    void weekdayOnlyExpression() {
        CronExpression expr = CronExpression.parse("0 9 * * 1-5"); // Mon-Fri at 09:00
        // 2026-03-20 is a Friday (dow=5)
        ZonedDateTime friday = ZonedDateTime.of(2026, 3, 20, 9, 0, 0, 0, ZoneOffset.UTC);
        // 2026-03-21 is a Saturday (dow=6)
        ZonedDateTime saturday = ZonedDateTime.of(2026, 3, 21, 9, 0, 0, 0, ZoneOffset.UTC);
        assertThat(expr.matches(friday)).isTrue();
        assertThat(expr.matches(saturday)).isFalse();
    }

    @Test
    void rangeWithStep() {
        CronExpression expr = CronExpression.parse("0-30/10 * * * *"); // 0, 10, 20, 30
        for (int min : new int[]{0, 10, 20, 30}) {
            ZonedDateTime dt = ZonedDateTime.of(2026, 3, 20, 8, min, 0, 0, ZoneOffset.UTC);
            assertThat(expr.matches(dt)).as("minute=" + min).isTrue();
        }
        ZonedDateTime bad = ZonedDateTime.of(2026, 3, 20, 8, 5, 0, 0, ZoneOffset.UTC);
        assertThat(expr.matches(bad)).isFalse();
    }

    // ── nextExecution ────────────────────────────────────────────────────────

    @Test
    void nextExecutionForEveryMinuteIsOneMinuteLater() {
        CronExpression expr = CronExpression.parse("* * * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 3, 20, 10, 5, 0, 0, ZoneOffset.UTC);
        ZonedDateTime next = expr.nextExecution(from);
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 3, 20, 10, 6, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void nextExecutionForDailyBriefing() {
        CronExpression expr = CronExpression.parse("0 6 * * *");
        // Currently 07:00 — next should be 06:00 next day
        ZonedDateTime from = ZonedDateTime.of(2026, 3, 20, 7, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime next = expr.nextExecution(from);
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 3, 21, 6, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void nextExecutionCrossesHour() {
        CronExpression expr = CronExpression.parse("0 * * * *"); // top of every hour
        ZonedDateTime from = ZonedDateTime.of(2026, 3, 20, 10, 45, 0, 0, ZoneOffset.UTC);
        ZonedDateTime next = expr.nextExecution(from);
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 3, 20, 11, 0, 0, 0, ZoneOffset.UTC));
    }

    // ── parse errors ─────────────────────────────────────────────────────────

    @Test
    void blankExpressionThrows() {
        assertThatThrownBy(() -> CronExpression.parse(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void wrongFieldCountThrows() {
        assertThatThrownBy(() -> CronExpression.parse("* * * *"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("5 fields");
    }

    @Test
    void invalidValueThrows() {
        assertThatThrownBy(() -> CronExpression.parse("abc * * * *"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
