package io.cognis.vertical.livestock.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.store.AnimalStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports water trough visit frequency per farm section.
 *
 * <p>Identifies sections where animals have not visited water in 24+ hours,
 * suggesting the trough may be empty or broken.
 */
public final class WaterMonitorTool implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(WaterMonitorTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String name() {
        return "water_monitor";
    }

    @Override
    public String description() {
        return "Reports water trough visit frequency per farm section. Identifies sections where " +
               "animals have not visited water in 24+ hours, suggesting trough may be empty or broken.";
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
            return "No animal store configured — water monitor unavailable.";
        }

        List<Animal> all;
        try {
            all = store.findAll();
        } catch (Exception e) {
            LOG.warn("WaterMonitorTool: failed to read animal store", e);
            return "Error reading animal store: " + e.getMessage();
        }

        if (all.isEmpty()) {
            return "{\"sections\":0,\"report\":[],\"status\":\"No animals registered.\"}";
        }

        Instant now            = Instant.now();
        Instant twentyFourAgo = now.minus(24, ChronoUnit.HOURS);

        // Group by section, track latest water visit per section
        Map<String, Instant> lastWaterBySectionInstant = new LinkedHashMap<>();
        Map<String, Integer> countBySection = new LinkedHashMap<>();

        for (Animal a : all) {
            String section = a.section();
            countBySection.merge(section, 1, Integer::sum);

            Instant visit = a.lastWaterVisit();
            if (visit != null) {
                Instant existing = lastWaterBySectionInstant.get(section);
                if (existing == null || visit.isAfter(existing)) {
                    lastWaterBySectionInstant.put(section, visit);
                }
            } else {
                // Ensure section appears even with no visits
                lastWaterBySectionInstant.putIfAbsent(section, null);
            }
        }

        List<Map<String, Object>> report = new ArrayList<>();
        int problemSections = 0;

        for (String section : countBySection.keySet()) {
            Instant lastVisit = lastWaterBySectionInstant.get(section);
            boolean problem   = (lastVisit == null || lastVisit.isBefore(twentyFourAgo));
            if (problem) {
                problemSections++;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("section",       section);
            entry.put("animalCount",   countBySection.get(section));
            entry.put("lastWaterVisit", lastVisit != null ? lastVisit.toString() : "never");
            entry.put("troughAlert",   problem);
            report.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sections",        countBySection.size());
        result.put("problemSections", problemSections);
        result.put("report",          report);
        result.put("status",          problemSections == 0
            ? "All sections have had water access in the last 24 hours."
            : problemSections + " section(s) with no water visit in 24h — check troughs.");

        try {
            return JSON.writeValueAsString(result);
        } catch (Exception e) {
            LOG.warn("WaterMonitorTool: failed to serialize result", e);
            return problemSections + " section(s) may have water trough issues.";
        }
    }
}
