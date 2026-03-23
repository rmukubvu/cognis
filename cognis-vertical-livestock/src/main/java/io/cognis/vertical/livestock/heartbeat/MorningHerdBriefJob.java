package io.cognis.vertical.livestock.heartbeat;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.store.AnimalStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily 06:00 herd status briefing for the farm manager.
 *
 * <p>Aggregates raw herd data (size, geofence status, health flags) and runs it
 * through the LLM to produce a concise morning briefing, published to the message bus.
 */
public final class MorningHerdBriefJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(MorningHerdBriefJob.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Africa/Johannesburg"));

    @Override
    public String name() {
        return "livestock.morning-herd-brief";
    }

    @Override
    public String cronExpression() {
        return "0 4 * * *";
    }

    @Override
    public void run(ToolContext context) {
        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings         = context.service("agentSettings",     AgentSettings.class);
        AnimalStore animalStore        = context.service("animalStore",        AnimalStore.class);

        if (orchestrator == null || settings == null) {
            LOG.info("[LIVESTOCK MORNING BRIEF] Orchestrator not available, skipping LLM run.");
            return;
        }

        String today = DATE_FMT.format(Instant.now());
        String rawData = buildRawData(animalStore);

        String prompt = """
            Generate a concise morning herd briefing for the farm manager.
            Date: %s
            Raw herd data:
            %s
            Flag any urgent issues. Keep it under 120 words.
            """.formatted(today, rawData);

        try {
            var result = orchestrator.run(prompt, settings, context.workspace(),
                Map.of("task_id", "livestock-morning-brief"));
            String brief = "[MORNING HERD BRIEF — %s]\n%s".formatted(today, result.content());
            LOG.info(brief);
            publishToMessageBus(context, brief);
        } catch (Exception e) {
            LOG.warn("MorningHerdBriefJob: agent run failed", e);
        }
    }

    private String buildRawData(AnimalStore animalStore) {
        if (animalStore == null) {
            return "Animal store not configured.";
        }
        try {
            List<Animal> all      = animalStore.findAll();
            int total             = all.size();
            long inside           = all.stream().filter(Animal::insideGeofence).count();
            long outside          = total - inside;
            long lowActivity      = all.stream().filter(a -> a.activityLevel() < 0.2).count();
            Instant threshold24h  = Instant.now().minus(24, ChronoUnit.HOURS);
            long noWater          = all.stream()
                .filter(a -> a.lastWaterVisit() == null || a.lastWaterVisit().isBefore(threshold24h))
                .count();
            return """
                Total animals: %d
                Inside geofence: %d
                Outside geofence: %d
                Low activity (<0.2): %d
                No water visit in 24h: %d
                """.formatted(total, inside, outside, lowActivity, noWater).trim();
        } catch (Exception e) {
            LOG.debug("Could not build raw herd data for brief", e);
            return "Animal data unavailable.";
        }
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish livestock morning brief to message bus", e);
            }
        }
    }
}
