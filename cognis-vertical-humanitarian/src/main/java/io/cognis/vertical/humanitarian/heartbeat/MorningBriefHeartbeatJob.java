package io.cognis.vertical.humanitarian.heartbeat;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.humanitarian.supply.Consignment;
import io.cognis.vertical.humanitarian.supply.ConsignmentStatus;
import io.cognis.vertical.humanitarian.supply.SupplyStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily 06:00 UTC briefing on supply chain status.
 *
 * <p>Fires every morning ({@code "0 6 * * *"}). Produces a structured summary:
 * total consignments, breakdown by status, and deliveries confirmed in the last 24 h.
 */
public final class MorningBriefHeartbeatJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(MorningBriefHeartbeatJob.class);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public String name() {
        return "humanitarian.morning-brief";
    }

    @Override
    public String cronExpression() {
        return "0 6 * * *"; // every day at 06:00 UTC
    }

    @Override
    public void run(ToolContext context) {
        SupplyStore store = context.service("supplyStore", SupplyStore.class);
        if (store == null) {
            LOG.debug("MorningBriefHeartbeatJob: supplyStore not in context, skipping");
            return;
        }

        List<Consignment> all = store.findAll();
        if (all.isEmpty()) {
            LOG.info("[MORNING BRIEF {}] No supply data recorded yet.", DATE_FMT.format(Instant.now()));
            return;
        }

        Map<ConsignmentStatus, Long> byStatus = all.stream()
            .collect(Collectors.groupingBy(Consignment::status, Collectors.counting()));

        long recentDeliveries = all.stream()
            .filter(c -> c.status() == ConsignmentStatus.DELIVERED)
            .filter(c -> c.updatedAt() != null
                && c.updatedAt().isAfter(Instant.now().minusSeconds(86_400)))
            .count();

        StringBuilder brief = new StringBuilder();
        brief.append("\n╔═══════════════════════════════════════╗\n");
        brief.append("  COGNIS SUPPLY BRIEF — ").append(DATE_FMT.format(Instant.now())).append("\n");
        brief.append("╚═══════════════════════════════════════╝\n");
        brief.append("  Total consignments : ").append(all.size()).append("\n");
        byStatus.forEach((status, count) ->
            brief.append("  ").append(pad(status.name(), 20)).append(": ").append(count).append("\n")
        );
        brief.append("  Delivered (24h)    : ").append(recentDeliveries).append("\n");

        LOG.info(brief.toString());
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}
