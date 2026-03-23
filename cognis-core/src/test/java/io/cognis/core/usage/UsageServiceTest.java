package io.cognis.core.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageServiceTest {

    // ── estimateCost ──────────────────────────────────────────────────────────

    @Test
    void estimateCostForClaudeSonnet() {
        // 3.00 USD / 1M prompt + 15.00 USD / 1M completion
        double cost = UsageService.estimateCost("claude-sonnet-3-5", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(18.0, within(0.0001));
    }

    @Test
    void estimateCostForClaudeOpus() {
        // 15.00 USD / 1M prompt + 75.00 USD / 1M completion
        double cost = UsageService.estimateCost("claude-opus-4", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(90.0, within(0.0001));
    }

    @Test
    void estimateCostForClaudeHaiku() {
        // 0.25 USD / 1M prompt + 1.25 USD / 1M completion
        double cost = UsageService.estimateCost("claude-haiku-3", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(1.5, within(0.0001));
    }

    @Test
    void estimateCostForGpt4oMini() {
        // 0.15 USD / 1M prompt + 0.60 USD / 1M completion
        double cost = UsageService.estimateCost("gpt-4o-mini", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(0.75, within(0.0001));
    }

    @Test
    void estimateCostForGpt4o() {
        // 2.50 USD / 1M prompt + 10.00 USD / 1M completion
        double cost = UsageService.estimateCost("gpt-4o", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(12.5, within(0.0001));
    }

    @Test
    void estimateCostForLlama() {
        // 0.10 USD / 1M prompt + 0.10 USD / 1M completion
        double cost = UsageService.estimateCost("llama-3.1-70b", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(0.2, within(0.0001));
    }

    @Test
    void estimateCostFallsBackToDefaultForUnknownModel() {
        // default: 3.00 / 1M prompt + 15.00 / 1M completion
        double cost = UsageService.estimateCost("unknown-model-xyz", 1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(18.0, within(0.0001));
    }

    @Test
    void estimateCostHandlesNullModel() {
        // null model should fall back to default without NPE
        double cost = UsageService.estimateCost(null, 500, 200);
        assertThat(cost).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void estimateCostForZeroTokens() {
        double cost = UsageService.estimateCost("claude-sonnet", 0, 0);
        assertThat(cost).isEqualTo(0.0);
    }

    // ── summary() ────────────────────────────────────────────────────────────

    @Test
    void summaryAggregatesRecordsCorrectly() throws IOException {
        StubUsageStore stub = new StubUsageStore();
        UsageService service = new UsageService(stub);

        Instant now = Instant.now();
        stub.records.add(new UsageRecord(now, "sa-agriculture", "+27821111111", "whatsapp",
            "anthropic", "claude-sonnet-3-5", 100, 50, 500L, 0.001, List.of()));
        stub.records.add(new UsageRecord(now, "sa-agriculture", "+27822222222", "sms",
            "anthropic", "claude-sonnet-3-5", 200, 80, 700L, 0.002, List.of()));
        stub.records.add(new UsageRecord(now, "humanitarian", "+25471111111", "whatsapp",
            "openrouter", "claude-haiku-3", 50, 30, 300L, 0.0005, List.of("supply_tracking")));

        Map<String, Object> result = service.summary(30);

        assertThat(result.get("total_messages")).isEqualTo(3L);
        assertThat(result.get("unique_users")).isEqualTo(3L);
        assertThat(result.get("total_tokens")).isEqualTo(510L);
        assertThat(result.get("prompt_tokens")).isEqualTo(350L);
        assertThat(result.get("completion_tokens")).isEqualTo(160L);

        @SuppressWarnings("unchecked")
        Map<String, Long> byVertical = (Map<String, Long>) result.get("messages_by_vertical");
        assertThat(byVertical.get("sa-agriculture")).isEqualTo(2L);
        assertThat(byVertical.get("humanitarian")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Long> byChannel = (Map<String, Long>) result.get("messages_by_channel");
        assertThat(byChannel.get("whatsapp")).isEqualTo(2L);
        assertThat(byChannel.get("sms")).isEqualTo(1L);
    }

    @Test
    void summaryWithEmptyStoreReturnsZeroes() throws IOException {
        UsageService service = new UsageService(new StubUsageStore());

        Map<String, Object> result = service.summary(7);

        assertThat(result.get("total_messages")).isEqualTo(0L);
        assertThat(result.get("unique_users")).isEqualTo(0L);
        assertThat(result.get("estimated_cost_usd")).isEqualTo(0.0);
    }

    @Test
    void summaryReportsPeriodDays() {
        UsageService service = new UsageService(new StubUsageStore());

        Map<String, Object> result = service.summary(14);

        assertThat(result.get("period_days")).isEqualTo(14);
    }

    @Test
    void summaryCountsUniqueUsersNotMessages() throws IOException {
        StubUsageStore stub = new StubUsageStore();
        UsageService service = new UsageService(stub);

        Instant now = Instant.now();
        // Same client sends two messages
        stub.records.add(new UsageRecord(now, "sa-agriculture", "+27821111111", "whatsapp",
            "anthropic", "claude-sonnet", 100, 50, 500L, 0.001, List.of()));
        stub.records.add(new UsageRecord(now, "sa-agriculture", "+27821111111", "sms",
            "anthropic", "claude-sonnet", 80, 40, 400L, 0.0008, List.of()));

        Map<String, Object> result = service.summary(30);

        assertThat(result.get("total_messages")).isEqualTo(2L);
        assertThat(result.get("unique_users")).isEqualTo(1L);
    }

    // ── Stub store for unit tests ─────────────────────────────────────────────

    /** Simple in-memory store that returns all records without time filtering. */
    private static final class StubUsageStore implements UsageStore {
        final List<UsageRecord> records = new ArrayList<>();

        @Override
        public void append(UsageRecord record) {
            records.add(record);
        }

        @Override
        public List<UsageRecord> findAll() {
            return List.copyOf(records);
        }

        @Override
        public List<UsageRecord> findSince(Instant since) {
            return records.stream()
                .filter(r -> !r.timestamp().isBefore(since))
                .toList();
        }
    }
}
