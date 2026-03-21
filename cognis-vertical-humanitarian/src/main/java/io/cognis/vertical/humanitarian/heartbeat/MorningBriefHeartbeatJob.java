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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily 06:00 UTC briefing — triggers a real LLM agent run to generate a
 * narrative supply chain brief, then publishes it to the message bus so all
 * connected coordinators receive it automatically.
 */
public final class MorningBriefHeartbeatJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(MorningBriefHeartbeatJob.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    @Override
    public String name() {
        return "humanitarian.morning-brief";
    }

    @Override
    public String cronExpression() {
        return "0 6 * * *";
    }

    @Override
    public void run(ToolContext context) {
        SupplyStore store = context.service("supplyStore", SupplyStore.class);
        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings = context.service("agentSettings", AgentSettings.class);
        Path workspace = context.workspace();

        if (store == null) {
            LOG.debug("MorningBriefHeartbeatJob: supplyStore not in context, skipping");
            return;
        }

        List<Consignment> all = store.findAll();
        if (all.isEmpty()) {
            LOG.info("MorningBriefHeartbeatJob: no supply data, skipping brief");
            return;
        }

        Map<ConsignmentStatus, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(Consignment::status, Collectors.counting()));

        long recentDeliveries = all.stream()
            .filter(c -> c.status() == ConsignmentStatus.DELIVERED)
            .filter(c -> c.updatedAt() != null && c.updatedAt().isAfter(Instant.now().minusSeconds(86_400)))
            .count();

        long overdueCount = all.stream()
            .filter(c -> c.status() == ConsignmentStatus.DISPATCHED)
            .filter(c -> c.createdAt() != null && c.createdAt().isBefore(Instant.now().minusSeconds(48 * 3600)))
            .count();

        String rawData = """
            Date: %s UTC
            Total consignments: %d
            By status: %s
            Delivered in last 24h: %d
            Overdue (dispatched >48h ago): %d
            """.formatted(
            DATE_FMT.format(Instant.now()),
            all.size(),
            byStatus.entrySet().stream()
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(", ")),
            recentDeliveries,
            overdueCount
        );

        if (orchestrator == null || settings == null) {
            LOG.info("[MORNING BRIEF - raw data]\n{}", rawData);
            return;
        }

        String prompt = """
            Generate a concise morning supply chain briefing for USAID field coordinators.
            Use this raw data:

            %s

            Format: 3-4 bullet points. Lead with any alerts (overdue shipments).
            Tone: professional, operational. No fluff.
            """.formatted(rawData);

        try {
            var result = orchestrator.run(prompt, settings, workspace, Map.of("task_id", "morning-brief"));
            String brief = "[MORNING BRIEF — " + DATE_FMT.format(Instant.now()) + "]\n" + result.content();
            LOG.info(brief);
            publishToMessageBus(context, brief);
        } catch (Exception e) {
            LOG.warn("MorningBriefHeartbeatJob: agent run failed, publishing raw data", e);
            publishToMessageBus(context, "[MORNING BRIEF — raw]\n" + rawData);
        }
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish morning brief to message bus", e);
            }
        }
    }
}
