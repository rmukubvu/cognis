package io.cognis.vertical.livestock.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.store.AnimalStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks which animals are currently outside the farm geofence.
 */
public final class GeofenceTool implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(GeofenceTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String name() {
        return "geofence_check";
    }

    @Override
    public String description() {
        return "Checks which animals are currently outside the farm geofence. " +
               "Returns breach list with animal ID, location, and time outside.";
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
            return "No animal store configured — geofence check unavailable.";
        }

        List<Animal> outside;
        try {
            outside = store.findOutsideGeofence();
        } catch (Exception e) {
            LOG.warn("GeofenceTool: failed to read animal store", e);
            return "Error reading animal store: " + e.getMessage();
        }

        if (outside.isEmpty()) {
            return "{\"breaches\":0,\"animals\":[],\"status\":\"All animals inside geofence.\"}";
        }

        List<Map<String, Object>> breachList = outside.stream()
            .map(a -> {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("id",       a.id());
                entry.put("species",  a.species());
                entry.put("section",  a.section());
                entry.put("lat",      a.lat());
                entry.put("lng",      a.lng());
                entry.put("lastSeen", a.lastSeen().toString());
                return entry;
            })
            .toList();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("breaches", outside.size());
        result.put("animals",  breachList);
        result.put("status",   outside.size() + " animal(s) outside geofence — possible theft or sensor drift.");

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            LOG.warn("GeofenceTool: failed to serialize result", e);
            return outside.size() + " animal(s) outside geofence.";
        }
    }
}
