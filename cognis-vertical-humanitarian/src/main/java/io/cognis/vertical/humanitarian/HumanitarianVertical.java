package io.cognis.vertical.humanitarian;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.contact.ContactStore;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.sdk.CognisVertical;
import io.cognis.sdk.RouteDefinition;
import io.cognis.vertical.humanitarian.heartbeat.MorningBriefHeartbeatJob;
import io.cognis.vertical.humanitarian.heartbeat.OverdueShipmentHeartbeatJob;
import io.cognis.vertical.humanitarian.supply.SupplyTrackingTool;
import io.cognis.vertical.humanitarian.webhook.SmsWebhookRoute;
import io.cognis.vertical.humanitarian.webhook.WhatsAppWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Humanitarian vertical — UNICEF / USAID field operations support.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>{@link SupplyTrackingTool}           — warehouse-to-recipient consignment tracking</li>
 *   <li>{@link SmsWebhookRoute}              — inbound SMS (Twilio / Africa's Talking)</li>
 *   <li>{@link WhatsAppWebhookRoute}         — inbound WhatsApp (Meta Business API)</li>
 *   <li>{@link OverdueShipmentHeartbeatJob}  — hourly overdue-consignment LLM alert</li>
 *   <li>{@link MorningBriefHeartbeatJob}     — daily 06:00 UTC LLM supply briefing</li>
 * </ul>
 *
 * <h2>Cross-channel identity</h2>
 * SMS and WhatsApp messages from the same phone number share one conversation history.
 * A field officer in Gulu who switches from SMS to WhatsApp resumes the same session
 * without loss of context.
 *
 * <p>Discovered at runtime via {@code META-INF/services/io.cognis.sdk.CognisVertical}.
 */
public final class HumanitarianVertical implements CognisVertical {

    private static final Logger LOG = LoggerFactory.getLogger(HumanitarianVertical.class);
    private static final int MAX_HISTORY_TURNS = 10;

    // Set in initialize() — services injected by the runtime
    private AgentOrchestrator orchestrator;
    private AgentSettings agentSettings;
    private ContactStore contactStore;
    private MessageBus messageBus;
    private Path workspace;

    @Override
    public String name() {
        return "humanitarian";
    }

    @Override
    public List<Tool> tools() {
        return List.of(new SupplyTrackingTool());
    }

    @Override
    public void initialize(ToolContext context) {
        this.orchestrator  = context.service("agentOrchestrator", AgentOrchestrator.class);
        this.agentSettings = context.service("agentSettings", AgentSettings.class);
        this.contactStore  = context.service("contactStore", ContactStore.class);
        this.messageBus    = context.service("messageBus", MessageBus.class);
        this.workspace     = context.workspace();
        LOG.info("HumanitarianVertical initialized (orchestrator={}, contactStore={})",
            orchestrator != null, contactStore != null);
    }

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
            new SmsWebhookRoute((from, text)      -> routeFieldMessage(from, text, "sms")),
            new WhatsAppWebhookRoute((from, text) -> routeFieldMessage(from, text, "whatsapp"))
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

    // ── Channel message routing ───────────────────────────────────────────────

    /**
     * Route an inbound field message to the agent, using per-contact history for
     * cross-channel conversation continuity.
     *
     * @param phone   the sender's phone number (cross-channel identity key)
     * @param text    the message body
     * @param channel "sms" or "whatsapp"
     */
    private void routeFieldMessage(String phone, String text, String channel) {
        if (orchestrator == null || agentSettings == null) {
            LOG.debug("routeFieldMessage: orchestrator not injected, dropping message from {}", phone);
            return;
        }

        List<ChatMessage> history = List.of();
        if (contactStore != null) {
            try {
                history = contactStore.recentHistory(phone, MAX_HISTORY_TURNS);
            } catch (Exception e) {
                LOG.warn("Failed to load history for {}", phone, e);
            }
        }

        String prompt = "[Field message via %s from %s]: %s".formatted(channel, phone, text);
        LOG.info("Routing field message: channel={} phone={} text={}", channel, phone, text);

        try {
            var result = orchestrator.run(
                prompt,
                agentSettings,
                workspace,
                history,
                Map.of("client_id", phone, "channel", channel)
            );

            // Persist turn for next message — cross-channel continuity
            if (contactStore != null) {
                try {
                    contactStore.appendTurn(
                        phone,
                        ChatMessage.user(prompt),
                        ChatMessage.assistant(result.content()),
                        MAX_HISTORY_TURNS
                    );
                } catch (Exception e) {
                    LOG.warn("Failed to save history for {}", phone, e);
                }
            }

            // Publish response to message bus (WebSocket clients, dashboards, etc.)
            if (messageBus != null) {
                try {
                    messageBus.publish(ChatMessage.assistant(result.content()));
                } catch (Exception e) {
                    LOG.debug("Failed to publish field response to message bus", e);
                }
            }

        } catch (Exception e) {
            LOG.warn("Agent run failed for field message from {}", phone, e);
        }
    }
}
