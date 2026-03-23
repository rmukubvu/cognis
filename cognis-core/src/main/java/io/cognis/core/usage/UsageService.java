package io.cognis.core.usage;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application-level service for recording LLM usage and producing aggregated summaries.
 *
 * <p>Cost estimation is performed using a built-in model price table keyed on
 * model name substrings (USD per 1 million tokens). An approximate ZAR conversion
 * is also included in the summary output.
 */
public final class UsageService {

    private static final Logger LOG = LoggerFactory.getLogger(UsageService.class);

    /** Price per 1M tokens (USD): [promptPrice, completionPrice]. Key = model substring. */
    private static final Map<String, double[]> PRICE_TABLE = new LinkedHashMap<>();
    static {
        PRICE_TABLE.put("claude-opus",   new double[]{15.00, 75.00});
        PRICE_TABLE.put("claude-sonnet", new double[]{ 3.00, 15.00});
        PRICE_TABLE.put("claude-haiku",  new double[]{ 0.25,  1.25});
        PRICE_TABLE.put("gpt-4o-mini",   new double[]{ 0.15,  0.60});
        PRICE_TABLE.put("gpt-4o",        new double[]{ 2.50, 10.00});
        PRICE_TABLE.put("gpt-4",         new double[]{ 2.50, 10.00});
        PRICE_TABLE.put("llama",         new double[]{ 0.10,  0.10});
        PRICE_TABLE.put("mistral",       new double[]{ 0.20,  0.60});
        PRICE_TABLE.put("gemini-pro",    new double[]{ 1.25,  5.00});
        // default fallback (empty key must be last)
        PRICE_TABLE.put("",              new double[]{ 3.00, 15.00});
    }

    private final UsageStore store;

    /** Creates a service backed by the given {@link UsageStore}. */
    public UsageService(UsageStore store) {
        this.store = store;
    }

    /**
     * Records a completed LLM invocation.
     *
     * @param vertical         the vertical name (e.g. "sa-agriculture", "humanitarian")
     * @param clientId         the caller's client identifier (e.g. phone number)
     * @param channel          the inbound channel (e.g. "whatsapp", "sms")
     * @param provider         the LLM provider name (e.g. "anthropic", "openrouter")
     * @param model            the model identifier used for the inference
     * @param promptTokens     number of input / prompt tokens consumed
     * @param completionTokens number of output / completion tokens generated
     * @param durationMs       wall-clock duration of the inference in milliseconds
     * @param toolsUsed        list of tool names invoked during the agent run (may be null)
     */
    public void record(
        String vertical,
        String clientId,
        String channel,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        long durationMs,
        List<String> toolsUsed
    ) {
        double cost = estimateCost(model, promptTokens, completionTokens);
        UsageRecord record = new UsageRecord(
            Instant.now(), vertical, clientId, channel,
            provider, model, promptTokens, completionTokens,
            durationMs, cost, toolsUsed == null ? List.of() : List.copyOf(toolsUsed)
        );
        try {
            store.append(record);
        } catch (IOException e) {
            LOG.warn("Failed to persist usage record: {}", e.getMessage());
        }
    }

    /**
     * Produces an aggregated usage summary for the past {@code days} days.
     *
     * @param days number of calendar days to include (counting back from now)
     * @return a map of summary fields suitable for JSON serialisation
     */
    public Map<String, Object> summary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<UsageRecord> records;
        try {
            records = store.findSince(since);
        } catch (IOException e) {
            LOG.warn("Failed to load usage records: {}", e.getMessage());
            records = List.of();
        }

        long totalMessages   = records.size();
        long totalPrompt     = records.stream().mapToLong(UsageRecord::promptTokens).sum();
        long totalCompletion = records.stream().mapToLong(UsageRecord::completionTokens).sum();
        double totalCostUsd  = records.stream().mapToDouble(UsageRecord::costUsd).sum();
        double totalCostZar  = totalCostUsd * 18.5; // approximate ZAR conversion rate

        // Per-vertical breakdown
        Map<String, Long>   byVertical     = new LinkedHashMap<>();
        Map<String, Double> costByVertical = new LinkedHashMap<>();
        for (UsageRecord r : records) {
            byVertical.merge(r.vertical(), 1L, Long::sum);
            costByVertical.merge(r.vertical(), r.costUsd(), Double::sum);
        }

        // Per-channel breakdown
        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (UsageRecord r : records) {
            byChannel.merge(r.channel(), 1L, Long::sum);
        }

        long uniqueUsers = records.stream().map(UsageRecord::clientId).distinct().count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period_days",          days);
        result.put("total_messages",       totalMessages);
        result.put("unique_users",         uniqueUsers);
        result.put("total_tokens",         totalPrompt + totalCompletion);
        result.put("prompt_tokens",        totalPrompt);
        result.put("completion_tokens",    totalCompletion);
        result.put("estimated_cost_usd",   round2(totalCostUsd));
        result.put("estimated_cost_zar",   round2(totalCostZar));
        result.put("messages_by_vertical", byVertical);
        result.put("cost_usd_by_vertical", roundMap(costByVertical));
        result.put("messages_by_channel",  byChannel);
        return result;
    }

    /**
     * Returns all records from the past {@code days} days.
     *
     * @param days number of calendar days to look back
     * @return list of matching {@link UsageRecord} instances
     */
    public List<UsageRecord> recentRecords(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        try {
            return store.findSince(since);
        } catch (IOException e) {
            LOG.warn("Failed to load recent usage records: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Estimates the USD cost of an inference given the model name and token counts.
     * Matches by model name substring (case-insensitive) against the built-in price table.
     */
    static double estimateCost(String model, int promptTokens, int completionTokens) {
        if (model == null) model = "";
        String lc = model.toLowerCase();
        double[] prices = PRICE_TABLE.get(""); // default fallback
        for (Map.Entry<String, double[]> entry : PRICE_TABLE.entrySet()) {
            if (!entry.getKey().isEmpty() && lc.contains(entry.getKey())) {
                prices = entry.getValue();
                break;
            }
        }
        return (promptTokens * prices[0] + completionTokens * prices[1]) / 1_000_000.0;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private static Map<String, Double> roundMap(Map<String, Double> map) {
        Map<String, Double> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(k, round2(v)));
        return result;
    }
}
