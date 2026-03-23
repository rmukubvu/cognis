package io.cognis.vertical.livestock.heartbeat;

import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.bus.MessageBus;
import io.cognis.core.channel.ChannelReplySender;
import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.store.AnimalStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Night-time geofence theft watch — runs every 30 minutes between 20:00 and 06:00 UTC.
 *
 * <p>In South Africa, 182 cattle are stolen every day. Night-time geofence breaches
 * are treated as URGENT theft alerts. Silent if no animals are outside the fence.
 */
public final class NightGeofenceJob implements HeartbeatJob {

    private static final Logger LOG = LoggerFactory.getLogger(NightGeofenceJob.class);
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    @Override
    public String name() {
        return "livestock.night-geofence-watch";
    }

    @Override
    public String cronExpression() {
        // Every 30 minutes during night hours (20:00-23:59 and 00:00-06:00 UTC)
        return "0/30 20-23,0-6 * * *";
    }

    @Override
    public void run(ToolContext context) {
        AnimalStore animalStore = context.service("animalStore", AnimalStore.class);
        if (animalStore == null) {
            LOG.debug("NightGeofenceJob: no animal store configured, skipping.");
            return;
        }

        List<Animal> outside;
        try {
            outside = animalStore.findOutsideGeofence();
        } catch (Exception e) {
            LOG.warn("NightGeofenceJob: failed to read animal store", e);
            return;
        }

        if (outside.isEmpty()) {
            // Silent — no noise when all animals are safe
            return;
        }

        AgentOrchestrator orchestrator = context.service("agentOrchestrator", AgentOrchestrator.class);
        AgentSettings settings         = context.service("agentSettings",     AgentSettings.class);

        if (orchestrator == null || settings == null) {
            LOG.warn("[THEFT ALERT] {} animal(s) outside geofence but orchestrator not available. IDs: {}",
                outside.size(),
                outside.stream().map(Animal::id).collect(Collectors.joining(", ")));
            return;
        }

        String now         = TIME_FMT.format(Instant.now());
        String animalDetails = buildAnimalDetails(outside);

        String prompt = """
            URGENT: Potential livestock theft detected during night-time geofence watch.
            Time: %s
            Animals outside geofence (%d):
            %s
            Generate a concise, urgent theft alert for the farm manager.
            Include animal IDs, last known locations, and recommended immediate actions.
            South Africa context: 182 cattle are stolen every day — treat this as a real threat.
            Keep alert under 100 words.
            """.formatted(now, outside.size(), animalDetails);

        try {
            var result = orchestrator.run(prompt, settings, context.workspace(),
                Map.of("task_id", "livestock-theft-alert"));
            String alert = "[THEFT ALERT — %s]\n%s".formatted(now, result.content());
            LOG.warn(alert);
            publishToMessageBus(context, alert);
            sendUrgentSms(context, alert);
        } catch (Exception e) {
            LOG.warn("NightGeofenceJob: agent run failed", e);
        }
    }

    private String buildAnimalDetails(List<Animal> outside) {
        StringBuilder sb = new StringBuilder();
        for (Animal a : outside) {
            sb.append("  - ID: ").append(a.id())
              .append(", Species: ").append(a.species())
              .append(", Last location: ").append(a.lat()).append(",").append(a.lng())
              .append(", Last seen: ").append(a.lastSeen())
              .append("\n");
        }
        return sb.toString().trim();
    }

    private void publishToMessageBus(ToolContext context, String content) {
        MessageBus bus = context.service("messageBus", MessageBus.class);
        if (bus != null) {
            try {
                bus.publish(ChatMessage.assistant(content));
            } catch (Exception e) {
                LOG.debug("Failed to publish theft alert to message bus", e);
            }
        }
    }

    private void sendUrgentSms(ToolContext context, String alert) {
        ChannelReplySender replySender = context.service("replySender", ChannelReplySender.class);
        if (replySender == null) {
            return;
        }
        String farmOwnerPhone = System.getenv("LIVESTOCK_OWNER_PHONE");
        if (farmOwnerPhone == null || farmOwnerPhone.isBlank()) {
            LOG.debug("NightGeofenceJob: LIVESTOCK_OWNER_PHONE not set, skipping SMS alert.");
            return;
        }
        try {
            if (replySender.supports("sms")) {
                replySender.send(farmOwnerPhone, alert, "sms");
                LOG.info("NightGeofenceJob: theft alert SMS sent to {}", farmOwnerPhone);
            }
        } catch (Exception e) {
            LOG.warn("NightGeofenceJob: failed to send theft alert SMS", e);
        }
    }
}
