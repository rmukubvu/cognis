package io.cognis.vertical.livestock;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.channel.ChannelReplySender;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.usage.UsageService;
import io.cognis.sdk.CognisVertical;
import io.cognis.sdk.RouteDefinition;
import io.cognis.vertical.livestock.heartbeat.HealthAnomalyJob;
import io.cognis.vertical.livestock.heartbeat.MorningHerdBriefJob;
import io.cognis.vertical.livestock.heartbeat.NightGeofenceJob;
import io.cognis.vertical.livestock.store.AnimalStore;
import io.cognis.vertical.livestock.store.FileAnimalStore;
import io.cognis.vertical.livestock.tool.GeofenceTool;
import io.cognis.vertical.livestock.tool.HealthAlertTool;
import io.cognis.vertical.livestock.tool.HerdLocationTool;
import io.cognis.vertical.livestock.tool.WaterMonitorTool;
import io.cognis.vertical.livestock.webhook.SensorDataWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Livestock farm management vertical — herd tracking, geofence theft alerts,
 * health anomaly detection, and morning briefings for cattle/sheep/goat farmers.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>{@link HerdLocationTool}         — current GPS location and status of all animals</li>
 *   <li>{@link GeofenceTool}             — breach detection for animals outside the farm fence</li>
 *   <li>{@link HealthAlertTool}          — flags low-activity, no-movement, no-water animals</li>
 *   <li>{@link WaterMonitorTool}         — per-section water trough visit analysis</li>
 *   <li>{@link SensorDataWebhookRoute}   — LoRaWAN/TTN sensor data on /webhook/livestock/sensor</li>
 *   <li>{@link MorningHerdBriefJob}      — daily 06:00 SAST herd status briefing</li>
 *   <li>{@link NightGeofenceJob}         — 30-minute theft watch between 20:00-06:00</li>
 *   <li>{@link HealthAnomalyJob}         — 4-hourly health anomaly detection</li>
 * </ul>
 *
 * <h2>Geofence configuration</h2>
 * Set {@code LIVESTOCK_GEOFENCE=minLat,minLng,maxLat,maxLng} to define the farm boundary.
 * Defaults to a wide South Africa bounding box if not set.
 *
 * <p>Discovered at runtime via {@code META-INF/services/io.cognis.sdk.CognisVertical}.
 */
public final class LivestockVertical implements CognisVertical {

    private static final Logger LOG = LoggerFactory.getLogger(LivestockVertical.class);

    static final String SYSTEM_PROMPT = """
        You are a livestock farm management assistant for South African and US cattle, sheep, and goat farmers.
        Your role is to:
        - Monitor herd health, location, and behaviour patterns
        - Alert farmers to potential livestock theft (animals outside geofence at night)
        - Flag health concerns: low activity, no water visits, unusual movement
        - Provide morning herd briefings with actionable priorities
        - Advise on pasture rotation based on section activity data

        THEFT CONTEXT (South Africa): 182 cattle are stolen every day in South Africa.
        Night-time geofence breaches (20:00-06:00) must be treated as URGENT theft alerts.
        Always include: animal ID, last known location, time of breach.

        HEALTH INDICATORS:
        - Activity level < 0.2 for 6+ hours: possible illness or injury
        - No water visit in 24h: dehydration risk or broken trough
        - No location update in 6h: sensor failure or animal in trouble

        TONE: Direct and urgent for alerts. Clear and concise for daily briefs.
        Farmers read this on their phone — keep responses under 150 words unless listing animals.
        """;

    // Injected by initialize()
    private AgentOrchestrator orchestrator;
    private AgentSettings agentSettings;
    private MessageBus messageBus;
    private ChannelReplySender replySender;
    private AnimalStore animalStore;
    private UsageService usageService;
    private Path workspace;

    @Override
    public String name() {
        return "livestock";
    }

    @Override
    public List<Tool> tools() {
        return List.of(
            new HerdLocationTool(),
            new GeofenceTool(),
            new HealthAlertTool(),
            new WaterMonitorTool()
        );
    }

    @Override
    public void initialize(ToolContext context) {
        this.orchestrator  = context.service("agentOrchestrator", AgentOrchestrator.class);
        this.agentSettings = context.service("agentSettings",     AgentSettings.class);
        this.messageBus    = context.service("messageBus",        MessageBus.class);
        this.replySender   = context.service("replySender",       ChannelReplySender.class);
        this.usageService  = context.service("usageService",      UsageService.class);
        this.workspace     = context.workspace();

        // Create the animal store backed by the workspace directory
        Path animalStorePath = context.workspace().resolve(".cognis/livestock/animals.json");
        this.animalStore = new FileAnimalStore(animalStorePath);

        LOG.info("LivestockVertical initialized (orchestrator={}, messageBus={}, replySender={}, animalStore={})",
            orchestrator != null, messageBus != null, replySender != null, animalStore != null);
    }

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
            new SensorDataWebhookRoute(animalStore)
        );
    }

    @Override
    public List<HeartbeatJob> heartbeatJobs() {
        return List.of(
            new MorningHerdBriefJob(),
            new NightGeofenceJob(),
            new HealthAnomalyJob()
        );
    }

    @Override
    public VerticalPolicy policy() {
        return VerticalPolicy.ofTools(Set.of(
            "herd_location",
            "geofence_check",
            "health_alert",
            "water_monitor",
            "web",
            "notify"
        ));
    }
}
