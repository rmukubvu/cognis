package io.cognis.vertical.humanitarian;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.sdk.CognisVertical;
import io.cognis.sdk.RouteDefinition;
import io.cognis.vertical.humanitarian.heartbeat.MorningBriefHeartbeatJob;
import io.cognis.vertical.humanitarian.heartbeat.OverdueShipmentHeartbeatJob;
import io.cognis.vertical.humanitarian.supply.SupplyTrackingTool;
import io.cognis.vertical.humanitarian.webhook.SmsWebhookRoute;
import io.cognis.vertical.humanitarian.webhook.WhatsAppWebhookRoute;
import java.util.List;
import java.util.Set;

/**
 * Humanitarian vertical — UNICEF / USAID field operations support.
 *
 * <p>Contributes:
 * <ul>
 *   <li>{@link SupplyTrackingTool}           — warehouse-to-recipient consignment tracking</li>
 *   <li>{@link SmsWebhookRoute}              — inbound SMS (Twilio / Africa's Talking)</li>
 *   <li>{@link WhatsAppWebhookRoute}         — inbound WhatsApp (Meta Business API)</li>
 *   <li>{@link OverdueShipmentHeartbeatJob}  — hourly overdue-consignment alert</li>
 *   <li>{@link MorningBriefHeartbeatJob}     — daily 06:00 UTC supply chain briefing</li>
 * </ul>
 *
 * <p>Discovered at runtime via {@code META-INF/services/io.cognis.sdk.CognisVertical}.
 */
public final class HumanitarianVertical implements CognisVertical {

    @Override
    public String name() {
        return "humanitarian";
    }

    @Override
    public List<Tool> tools() {
        return List.of(new SupplyTrackingTool());
    }

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
            new SmsWebhookRoute((from, text) -> {}),
            new WhatsAppWebhookRoute((from, text) -> {})
        );
    }

    @Override
    public List<HeartbeatJob> heartbeatJobs() {
        return List.of(
            new OverdueShipmentHeartbeatJob(),
            new MorningBriefHeartbeatJob()
        );
    }

    @Override
    public VerticalPolicy policy() {
        return VerticalPolicy.ofTools(Set.of(
            "supply_tracking",
            "memory",
            "notify",
            "mcp"
        ));
    }
}
