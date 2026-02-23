package io.cognis.core.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class NaturalTimeParserTest {

    private final NaturalTimeParser parser = new NaturalTimeParser();

    @Test
    void shouldParseRelativeInExpression() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-20T10:00:00Z"), ZoneId.of("UTC"));

        long parsed = parser.parseToEpochMs("in 15m", clock, ZoneId.of("UTC"));

        assertThat(parsed).isEqualTo(Instant.parse("2026-02-20T10:15:00Z").toEpochMilli());
    }

    @Test
    void shouldParseTomorrowAtExpression() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-20T10:00:00Z"), ZoneId.of("UTC"));

        long parsed = parser.parseToEpochMs("tomorrow at 9am", clock, ZoneId.of("UTC"));

        assertThat(parsed).isEqualTo(Instant.parse("2026-02-21T09:00:00Z").toEpochMilli());
    }
}
