package io.cognis.vertical.livestock.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.tool.Tool;
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
 * Identifies animals showing abnormal behaviour patterns — low activity,
 * no movement in 6+ hours, or no water visit in 24+ hours.
 */
public final class HealthAlertTool implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(HealthAlertTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String name() {
        return "health_alert";
    }

    @Override
    public String description() {
        return "Identifies animals showing abnormal behaviour patterns — low activity, " +
               "no movement in 6+ hours, or no water visit in 24+ hours. Returns flagged animals with reason.";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        AnimalStore store = ctx.service("animalStore", AnimalStore.class);
        if (store == null) {
            return "No animal store configured — health alert check unavailable.";
        }

        List<Animal> all;
        try {
            all = store.findAll();
        } catch (Exception e) {
            LOG.warn("HealthAlertTool: failed to read animal store", e);
            return "Error reading animal store: " + e.getMessage();
        }

        if (all.isEmpty()) {
            return "{\"alerts\":0,\"flagged\":[],\"status\":\"No animals registered.\"}";
        }

        Instant now             = Instant.now();
        Instant sixHoursAgo    = now.minus(6,  ChronoUnit.HOURS);
        Instant twentyFourAgo  = now.minus(24, ChronoUnit.HOURS);

        List<Map<String, Object>> flagged = new ArrayList<>();
        for (Animal a : all) {
            List<String> reasons = new ArrayList<>();

            if (a.activityLevel() < 0.2) {
                reasons.add("Very low activity (" + String.format("%.2f", a.activityLevel()) + ")");
            }
            if (a.lastSeen().isBefore(sixHoursAgo)) {
                reasons.add("No recent location update (last seen " + a.lastSeen() + ")");
            }
            if (a.lastWaterVisit() == null || a.lastWaterVisit().isBefore(twentyFourAgo)) {
                reasons.add("No water trough visit in 24h");
            }

            if (!reasons.isEmpty()) {
                Map<String, Object> alert = new java.util.LinkedHashMap<>();
                alert.put("id",      a.id());
                alert.put("species", a.species());
                alert.put("section", a.section());
                alert.put("reasons", reasons);
                flagged.add(alert);
            }
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("alerts",  flagged.size());
        result.put("flagged", flagged);
        result.put("status",  flagged.isEmpty()
            ? "No health anomalies detected."
            : flagged.size() + " animal(s) flagged for attention.");

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            LOG.warn("HealthAlertTool: failed to serialize result", e);
            return flagged.size() + " animal(s) flagged for health attention.";
        }
    }
}
