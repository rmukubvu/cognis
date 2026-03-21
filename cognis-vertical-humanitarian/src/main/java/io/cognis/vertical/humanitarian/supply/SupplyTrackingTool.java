package io.cognis.vertical.humanitarian.supply;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks humanitarian supply consignments from dispatch to delivery.
 *
 * <p>Supported actions:
 * <ul>
 *   <li>{@code log_dispatch} — record a new outbound consignment</li>
 *   <li>{@code confirm_delivery} — mark a consignment as delivered</li>
 *   <li>{@code check_status} — query current status of a consignment</li>
 *   <li>{@code list_overdue} — list all consignments in OVERDUE state</li>
 * </ul>
 *
 * <p>Requires a {@link SupplyStore} registered in the {@link ToolContext} under the key
 * {@code "supplyStore"}. Falls back to an {@link InMemorySupplyStore} if none is found.
 */
public final class SupplyTrackingTool implements Tool {

    @Override
    public String name() {
        return "supply_tracking";
    }

    @Override
    public String description() {
        return "Track humanitarian supply consignments. Actions: log_dispatch, confirm_delivery, check_status, list_overdue.";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("log_dispatch", "confirm_delivery", "check_status", "list_overdue")
                ),
                "consignment_id", Map.of("type", "string", "description", "Unique consignment identifier"),
                "location", Map.of("type", "string", "description", "Current or destination location"),
                "recipient_phone", Map.of("type", "string", "description", "Recipient phone number for SMS notification")
            ),
            "required", List.of("action"),
            "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        SupplyStore store = ctx.service("supplyStore", SupplyStore.class);
        if (store == null) {
            store = new InMemorySupplyStore();
        }

        String action = (String) input.get("action");
        String consignmentId = (String) input.getOrDefault("consignment_id", "");
        String location = (String) input.getOrDefault("location", "");
        String phone = (String) input.getOrDefault("recipient_phone", "");

        return switch (action) {
            case "log_dispatch" -> logDispatch(store, consignmentId, location, phone);
            case "confirm_delivery" -> confirmDelivery(store, consignmentId, location);
            case "check_status" -> checkStatus(store, consignmentId);
            case "list_overdue" -> listOverdue(store);
            default -> "Unknown action: " + action;
        };
    }

    private String logDispatch(SupplyStore store, String id, String location, String phone) {
        if (id == null || id.isBlank()) {
            return "Error: consignment_id is required for log_dispatch";
        }
        Consignment consignment = new Consignment(id, ConsignmentStatus.DISPATCHED, location, phone, Instant.now(), Instant.now());
        store.save(consignment);
        return "Consignment " + id + " dispatched from " + (location.isBlank() ? "unspecified location" : location);
    }

    private String confirmDelivery(SupplyStore store, String id, String location) {
        if (id == null || id.isBlank()) {
            return "Error: consignment_id is required for confirm_delivery";
        }
        Optional<Consignment> existing = store.findById(id);
        if (existing.isEmpty()) {
            return "Consignment " + id + " not found";
        }
        store.save(existing.get().withStatus(ConsignmentStatus.DELIVERED, location));
        return "Consignment " + id + " marked as delivered at " + (location.isBlank() ? "unspecified location" : location);
    }

    private String checkStatus(SupplyStore store, String id) {
        if (id == null || id.isBlank()) {
            return "Error: consignment_id is required for check_status";
        }
        return store.findById(id)
            .map(c -> "Consignment " + c.id() + ": status=" + c.status() + ", location=" + c.location())
            .orElse("Consignment " + id + " not found");
    }

    private String listOverdue(SupplyStore store) {
        List<Consignment> overdue = store.findByStatus(ConsignmentStatus.OVERDUE);
        if (overdue.isEmpty()) {
            return "No overdue consignments";
        }
        StringBuilder sb = new StringBuilder("Overdue consignments (" + overdue.size() + "):\n");
        overdue.forEach(c -> sb.append("  - ").append(c.id()).append(" at ").append(c.location()).append('\n'));
        return sb.toString().trim();
    }
}
