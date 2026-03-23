package io.cognis.core.usage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Immutable record of a single LLM inference invocation, capturing token counts,
 * duration, estimated cost, and the context in which the call was made.
 */
public record UsageRecord(
    Instant timestamp,
    String vertical,
    @JsonProperty("client_id") String clientId,
    String channel,
    String provider,
    String model,
    @JsonProperty("prompt_tokens")     int promptTokens,
    @JsonProperty("completion_tokens") int completionTokens,
    @JsonProperty("duration_ms")       long durationMs,
    @JsonProperty("cost_usd")          double costUsd,
    @JsonProperty("tools_used")        List<String> toolsUsed
) {
    /** Returns the sum of prompt and completion tokens. */
    public int totalTokens() { return promptTokens + completionTokens; }
}
