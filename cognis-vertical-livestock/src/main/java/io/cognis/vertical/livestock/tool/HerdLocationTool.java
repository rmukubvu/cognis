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
 * Returns the current GPS location and status of all animals in the herd.
 */
public final class HerdLocationTool implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(HerdLocationTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String name() {
        return "herd_location";
    }

    @Override
    public String description() {
        return "Returns current GPS location and status of all animals in the herd. " +
               "Use this to get a full herd overview.";
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
            return "No animal store configured — no animals registered yet.";
        }

        List<Animal> animals;
        try {
            animals = store.findAll();
        } catch (Exception e) {
            LOG.warn("HerdLocationTool: failed to read animal store", e);
            return "Error reading animal store: " + e.getMessage();
        }

        if (animals.isEmpty()) {
            return "No animals registered yet. Register animals by sending sensor data to /webhook/livestock/sensor.";
        }

        List<Map<String, Object>> animalList = animals.stream()
            .map(a -> {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("id",             a.id());
                entry.put("species",        a.species());
                entry.put("section",        a.section());
                entry.put("lat",            a.lat());
                entry.put("lng",            a.lng());
                entry.put("lastSeen",       a.lastSeen().toString());
                entry.put("insideGeofence", a.insideGeofence());
                entry.put("activityLevel",  a.activityLevel());
                return entry;
            })
            .toList();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalAnimals", animals.size());
        result.put("animals",      animalList);

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            LOG.warn("HerdLocationTool: failed to serialize result", e);
            return "Herd has " + animals.size() + " animals registered.";
        }
    }
}
