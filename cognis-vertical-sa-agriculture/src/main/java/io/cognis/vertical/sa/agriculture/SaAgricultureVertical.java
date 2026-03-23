package io.cognis.vertical.sa.agriculture;

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
import io.cognis.vertical.sa.agriculture.heartbeat.MarketPriceHeartbeatJob;
import io.cognis.vertical.sa.agriculture.tool.MarketLocatorTool;
import io.cognis.vertical.sa.agriculture.tool.SafexPriceTool;
import io.cognis.vertical.sa.agriculture.tool.SubsidyNavigatorTool;
import io.cognis.vertical.sa.agriculture.webhook.SaSmsWebhookRoute;
import io.cognis.vertical.sa.agriculture.webhook.SaWhatsAppWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * South Africa Agriculture vertical — emerging farmer advisor.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>{@link SafexPriceTool}         — SAFEX commodity prices and sell/hold advice</li>
 *   <li>{@link SubsidyNavigatorTool}   — DAFF/Land Bank programme eligibility and application guidance</li>
 *   <li>{@link MarketLocatorTool}      — nearest fresh produce market and co-operative by province</li>
 *   <li>{@link SaWhatsAppWebhookRoute} — inbound WhatsApp (Meta Business API) on /webhook/sa/whatsapp</li>
 *   <li>{@link SaSmsWebhookRoute}      — inbound SMS (Twilio / Africa's Talking) on /webhook/sa/sms</li>
 *   <li>{@link MarketPriceHeartbeatJob}— daily 06:00 SAST LLM-generated market brief</li>
 * </ul>
 *
 * <h2>Multilingual support</h2>
 * The system prompt instructs the agent to detect the farmer's language
 * (Zulu, Xhosa, Sotho, Tswana, Afrikaans, English) and respond in kind.
 *
 * <h2>Cross-channel identity</h2>
 * A farmer who switches from SMS to WhatsApp retains full conversation history
 * via the shared {@link ContactStore} keyed by phone number.
 *
 * <p>Discovered at runtime via {@code META-INF/services/io.cognis.sdk.CognisVertical}.
 */
public final class SaAgricultureVertical implements CognisVertical {

    private static final Logger LOG = LoggerFactory.getLogger(SaAgricultureVertical.class);
    private static final int MAX_HISTORY_TURNS = 10;

    static final String SYSTEM_PROMPT = """
        You are an agricultural advisor for South African emerging and smallholder farmers.
        Your role is to provide practical, actionable advice on:
        - Commodity prices (SAFEX: maize, wheat, sunflower, soybeans) and when to sell vs hold
        - Fresh produce market prices and how to access them (Johannesburg, Durban, Cape Town markets)
        - Government support programmes: CASP, RECAP, Ilima/Letsema, Land Bank, AgriSETA
        - Seasonal planting and harvesting guidance for SA growing regions
        - Finding the nearest agricultural co-operative or market

        LANGUAGE: Detect the farmer's language from their message and reply in the same language.
        Supported: English, Zulu (eNingizimu Afrika), Xhosa (Mzantsi Afrika), Sesotho, Setswana, Afrikaans.
        If unsure, reply in English but offer to continue in their preferred language.

        TONE: Practical and encouraging. Avoid jargon. Farmers read this on a basic phone via WhatsApp.
        Keep responses concise — under 150 words unless detailed steps are needed.

        PROFILE BUILDING: Ask about province, crop type, and farm size early in the conversation
        so you can give personalised advice. Remember these across the conversation.

        USE TOOLS: Always use safex_price, subsidy_navigator, and market_locator tools to give
        accurate, current data. Do not guess prices or programme eligibility.

        IMPORTANT: Never claim a government programme application was submitted unless
        the farmer confirms they have done it themselves.
        """;

    // Injected by initialize()
    private AgentOrchestrator orchestrator;
    private AgentSettings agentSettings;
    private ContactStore contactStore;
    private MessageBus messageBus;
    private Path workspace;

    @Override
    public String name() {
        return "sa-agriculture";
    }

    @Override
    public List<Tool> tools() {
        return List.of(
            new SafexPriceTool(),
            new SubsidyNavigatorTool(),
            new MarketLocatorTool()
        );
    }

    @Override
    public void initialize(ToolContext context) {
        this.orchestrator  = context.service("agentOrchestrator", AgentOrchestrator.class);
        this.agentSettings = context.service("agentSettings", AgentSettings.class);
        this.contactStore  = context.service("contactStore", ContactStore.class);
        this.messageBus    = context.service("messageBus", MessageBus.class);
        this.workspace     = context.workspace();
        LOG.info("SaAgricultureVertical initialized (orchestrator={}, contactStore={})",
            orchestrator != null, contactStore != null);
    }

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
            new SaWhatsAppWebhookRoute((from, text) -> routeFarmerMessage(from, text, "whatsapp")),
            new SaSmsWebhookRoute((from, text)      -> routeFarmerMessage(from, text, "sms"))
        );
    }

    @Override
    public List<HeartbeatJob> heartbeatJobs() {
        return List.of(new MarketPriceHeartbeatJob());
    }

    @Override
    public VerticalPolicy policy() {
        return VerticalPolicy.ofTools(Set.of(
            "safex_price",
            "subsidy_navigator",
            "market_locator",
            "web",
            "memory",
            "notify"
        ));
    }

    // ── Farmer message routing ────────────────────────────────────────────────

    /**
     * Routes an inbound farmer message through the agent with per-contact history.
     *
     * @param phone   the farmer's phone number (cross-channel identity key)
     * @param text    the message body
     * @param channel "sms" or "whatsapp"
     */
    private void routeFarmerMessage(String phone, String text, String channel) {
        if (orchestrator == null || agentSettings == null) {
            LOG.debug("routeFarmerMessage: orchestrator not injected, dropping message from {}", phone);
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

        // Build agent settings with the SA agriculture system prompt
        AgentSettings saSettings = new AgentSettings(
            SYSTEM_PROMPT,
            agentSettings.provider(),
            agentSettings.model(),
            agentSettings.maxToolIterations()
        );

        String prompt = "[Farmer message via %s from %s]: %s".formatted(channel, phone, text);
        LOG.info("Routing farmer message: channel={} phone={}", channel, phone);

        try {
            var result = orchestrator.run(
                prompt,
                saSettings,
                workspace,
                history,
                Map.of("client_id", phone, "channel", channel, "vertical", "sa-agriculture")
            );

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

            if (messageBus != null) {
                try {
                    messageBus.publish(ChatMessage.assistant(result.content()));
                } catch (Exception e) {
                    LOG.debug("Failed to publish farmer response to message bus", e);
                }
            }

        } catch (Exception e) {
            LOG.warn("Agent run failed for farmer message from {}", phone, e);
        }
    }
}
