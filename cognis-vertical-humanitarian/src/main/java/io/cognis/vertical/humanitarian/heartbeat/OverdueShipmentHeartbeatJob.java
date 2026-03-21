package io.cognis.vertical.humanitarian.heartbeat;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.humanitarian.supply.Consignment;
import io.cognis.vertical.humanitarian.supply.ConsignmentStatus;
import io.cognis.vertical.humanitarian.supply.SupplyStore;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hourly heartbeat that scans for overdue consignments and fires a real LLM agent run
 * to draft coordinator alerts when any are found.
 *
 * <p>Fires at the top of every hour ({@code "0 * * * *"}).
 * When overdue shipments are detected, builds a structured prompt and calls
 * {@link AgentOrchestrator} so the agent can use the {@code supply_tracking} and
 * {@code notify} tools to draft and dispatch actual SMS alerts — not just log warnings.
 */
public final class OverdueShipmentHeartbeatJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(OverdueShipmentHeartbeatJob.class);
    private static final long OVERDUE_HOURS = 48;

    @Override
    public String name() {
        return "humanitarian.overdue-shipment-check";
    }

    @Override
    public String cronExpression() {
        return "0 * * * *";
    }

    @Override
    public void run(ToolContext context) {
        SupplyStore store = context.service("supplyStore", SupplyStore.class);
        if (store == null) {
            LOG.debug("OverdueShipmentHeartbeatJob: supplyStore not in context, skipping");
            return;
        }

        Instant cutoff = Instant.now().minus(OVERDUE_HOURS, ChronoUnit.HOURS);
        List<Consignment> overdue = store.findByStatus(ConsignmentStatus.DISPATCHED).stream()
            .filter(c -> c.createdAt() != null && c.createdAt().isBefore(cutoff))
            .toList();

        if (overdue.isEmpty()) {
            LOG.debug("OverdueShipmentHeartbeatJob: no overdue shipments");
            return;
        }

        LOG.warn("OverdueShipmentHeartbeatJob: {} overdue shipment(s) — triggering agent run", overdue.size());

        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings = context.service("agentSettings", AgentSettings.class);
        Path workspace = context.workspace();

        if (orchestrator == null || settings == null) {
            // Fallback: log only (dev mode without orchestrator injected)
            overdue.forEach(c -> LOG.warn(
                "OVERDUE consignment {} at {} for {} — {}h+ with no delivery confirmation",
                c.id(), c.location(), c.recipientPhone(), OVERDUE_HOURS
            ));
            return;
        }

        String summary = overdue.stream()
            .map(c -> "- Consignment %s at %s for %s (dispatched %dh ago)".formatted(
                c.id(), c.location(), c.recipientPhone(),
                ChronoUnit.HOURS.between(c.createdAt(), Instant.now())
            ))
            .collect(Collectors.joining("\n"));

        String prompt = """
            AUTOMATED ALERT: The following consignments are overdue by more than %dh with no delivery confirmation.
            %s

            For each overdue consignment:
            1. Log a discrepancy via the supply_tracking tool (action=log_discrepancy)
            2. Compose a brief coordinator alert noting the consignment ID, location, and hours overdue

            Be concise. This is an automated check — do not ask for confirmation.
            """.formatted(OVERDUE_HOURS, summary);

        try {
            var result = orchestrator.run(prompt, settings, workspace, Map.of("task_id", "overdue-check"));
            LOG.info("OverdueShipmentHeartbeatJob agent run complete: {}", result.content());
            publishToMessageBus(context, "[OVERDUE ALERT]\n" + result.content());
        } catch (Exception e) {
            LOG.warn("OverdueShipmentHeartbeatJob: agent run failed", e);
        }
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish overdue alert to message bus", e);
            }
        }
    }
}
