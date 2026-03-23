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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodic herd health anomaly check — runs every 4 hours.
 *
 * <p>Flags animals with low activity or no recent water visits, then
 * calls the LLM to interpret the anomalies. Silent if no anomalies are found.
 */
public final class HealthAnomalyJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(HealthAnomalyJob.class);

    @Override
    public String name() {
        return "livestock.health-anomaly-check";
    }

    @Override
    public String cronExpression() {
        return "0 */4 * * *";
    }

    @Override
    public void run(ToolContext context) {
        AnimalStore animalStore = context.service("animalStore", AnimalStore.class);
        if (animalStore == null) {
            LOG.debug("HealthAnomalyJob: no animal store configured, skipping.");
            return;
        }

        List<Animal> all;
        try {
            all = animalStore.findAll();
        } catch (Exception e) {
            LOG.warn("HealthAnomalyJob: failed to read animal store", e);
            return;
        }

        if (all.isEmpty()) {
            return;
        }

        Instant now            = Instant.now();
        Instant twentyFourAgo  = now.minus(24, ChronoUnit.HOURS);

        List<String> anomalies = new ArrayList<>();
        for (Animal a : all) {
            if (a.activityLevel() < 0.2) {
                anomalies.add("Animal " + a.id() + " (" + a.species() + ", " + a.section() +
                    "): very low activity (" + String.format("%.2f", a.activityLevel()) + ")");
            }
            if (a.lastWaterVisit() == null || a.lastWaterVisit().isBefore(twentyFourAgo)) {
                anomalies.add("Animal " + a.id() + " (" + a.species() + ", " + a.section() +
                    "): no water visit in 24h (last: " +
                    (a.lastWaterVisit() != null ? a.lastWaterVisit() : "never") + ")");
            }
        }

        if (anomalies.isEmpty()) {
            // Silent — no noise when herd is healthy
            return;
        }

        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings         = context.service("agentSettings",     AgentSettings.class);

        if (orchestrator == null || settings == null) {
            LOG.warn("[HEALTH ALERT] {} anomalies detected but orchestrator not available.", anomalies.size());
            return;
        }

        String anomalyList = String.join("\n", anomalies.stream().map(s -> "  - " + s).toList());
        String prompt = """
            Livestock health anomalies detected during routine 4-hour check:
            %s

            Interpret these anomalies for the farm manager. Explain likely causes and
            recommended immediate actions. Keep response under 100 words.
            """.formatted(anomalyList);

        try {
            var result = orchestrator.run(prompt, settings, context.workspace(),
                Map.of("task_id", "livestock-health-alert"));
            String alert = "[HEALTH ALERT]\n" + result.content();
            LOG.info(alert);
            publishToMessageBus(context, alert);
        } catch (Exception e) {
            LOG.warn("HealthAnomalyJob: agent run failed", e);
        }
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish health alert to message bus", e);
            }
        }
    }
}
