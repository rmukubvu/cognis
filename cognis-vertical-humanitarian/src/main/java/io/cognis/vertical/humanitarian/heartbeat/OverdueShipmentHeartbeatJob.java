package io.cognis.vertical.humanitarian.heartbeat;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.humanitarian.supply.Consignment;
import io.cognis.vertical.humanitarian.supply.ConsignmentStatus;
import io.cognis.vertical.humanitarian.supply.SupplyStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proactive heartbeat that scans for consignments overdue by more than 48 hours.
 *
 * <p>Fires every hour ({@code "0 * * * *"}). For each {@code DISPATCHED} consignment
 * whose {@code createdAt} timestamp is more than 48 hours old, logs a structured warning.
 * In production, wire the message bus via {@link ToolContext} to notify field coordinators.
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
        return "0 * * * *"; // top of every hour
    }

    @Override
    public void run(ToolContext context) {
        SupplyStore store = context.service("supplyStore", SupplyStore.class);
        if (store == null) {
            LOG.debug("OverdueShipmentHeartbeatJob: supplyStore not in context, skipping");
            return;
        }

        Instant cutoff = Instant.now().minus(OVERDUE_HOURS, ChronoUnit.HOURS);

        List<Consignment> dispatched = store.findByStatus(ConsignmentStatus.DISPATCHED);
        long overdue = dispatched.stream()
            .filter(c -> c.createdAt() != null && c.createdAt().isBefore(cutoff))
            .peek(c -> LOG.warn(
                "OVERDUE consignment {} at {} for {} — dispatched {}h+ ago, no delivery confirmation",
                c.id(), c.location(), c.recipientPhone(), OVERDUE_HOURS
            ))
            .count();

        if (overdue == 0) {
            LOG.debug("OverdueShipmentHeartbeatJob: no overdue shipments");
        } else {
            LOG.warn("OverdueShipmentHeartbeatJob: {} overdue shipment(s) detected", overdue);
        }
    }
}
