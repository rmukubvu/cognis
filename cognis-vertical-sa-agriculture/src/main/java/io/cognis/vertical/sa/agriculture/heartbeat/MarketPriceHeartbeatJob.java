package io.cognis.vertical.sa.agriculture.heartbeat;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.sa.agriculture.store.FarmerStore;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily 06:00 SAST (04:00 UTC) agricultural market brief.
 *
 * <p>Calls the LLM agent to produce a narrative briefing covering SAFEX commodity
 * price trends, fresh produce market highlights, and seasonal advisory notes.
 * The result is published to the message bus for all connected operators.
 */
public final class MarketPriceHeartbeatJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(MarketPriceHeartbeatJob.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Africa/Johannesburg"));

    @Override
    public String name() {
        return "sa-agriculture.morning-market-brief";
    }

    @Override
    public String cronExpression() {
        // 06:00 SAST = 04:00 UTC
        return "0 4 * * *";
    }

    @Override
    public void run(ToolContext context) {
        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings         = context.service("agentSettings", AgentSettings.class);
        FarmerStore farmerStore         = context.service("farmerStore", FarmerStore.class);
        Path workspace                  = context.workspace();

        String today = DATE_FMT.format(Instant.now());

        long farmerCount = 0;
        if (farmerStore != null) {
            try {
                farmerCount = farmerStore.findAll().size();
            } catch (Exception e) {
                LOG.debug("Could not count farmers for brief", e);
            }
        }

        String prompt = """
            Generate a concise morning agricultural market brief for South African emerging farmers.
            Date: %s SAST
            Registered farmers on platform: %d

            Cover these 4 points:
            1. SAFEX commodity price highlights — maize, wheat, sunflower (3-sentence max)
            2. Fresh produce market tip for today (Johannesburg, Durban, or Cape Town)
            3. Seasonal advisory — what should farmers in different regions be doing this week?
            4. One government support programme farmers should know about (CASP, Land Bank, Ilima)

            Tone: practical, direct, encouraging. Written for farmers who read this on WhatsApp.
            Do not use technical jargon. Keep total length under 200 words.
            """.formatted(today, farmerCount);

        if (orchestrator == null || settings == null) {
            LOG.info("[SA AGRICULTURE BRIEF - {}] Orchestrator not available, skipping LLM run.", today);
            return;
        }

        try {
            var result = orchestrator.run(prompt, settings, workspace, Map.of("task_id", "sa-agriculture-morning-brief"));
            String brief = "[SA AGRICULTURE BRIEF — %s]\n%s".formatted(today, result.content());
            LOG.info(brief);
            publishToMessageBus(context, brief);
        } catch (Exception e) {
            LOG.warn("MarketPriceHeartbeatJob: agent run failed", e);
        }
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish SA agriculture brief to message bus", e);
            }
        }
    }
}
