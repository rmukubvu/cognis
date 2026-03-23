package io.cognis.vertical.livestock.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.sdk.RouteDefinition;
import io.cognis.sdk.RouteHandler;
import io.cognis.sdk.RouteResponse;
import io.cognis.vertical.livestock.model.Animal;
import io.cognis.vertical.livestock.model.Geofence;
import io.cognis.vertical.livestock.store.AnimalStore;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles LoRaWAN sensor uplinks from livestock tracking tags on {@code /webhook/livestock/sensor}.
 *
 * <p>Supports two payload formats:
 * <ul>
 *   <li>The Things Network (TTN) uplink — nested {@code end_device_ids} + {@code uplink_message.decoded_payload}</li>
 *   <li>Generic flat format — {@code device_id}, {@code lat}, {@code lng}, {@code activity}, {@code near_water}</li>
 * </ul>
 *
 * <p>Responds 200 immediately and processes the update asynchronously on a virtual thread —
 * the same pattern used by {@code SaWhatsAppWebhookRoute} to avoid gateway timeouts.
 */
public final class SensorDataWebhookRoute implements RouteDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SensorDataWebhookRoute.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String PATH = "/webhook/livestock/sensor";

    /** Wide South Africa bounding box as the default farm geofence. */
    private static final Geofence DEFAULT_GEOFENCE = new Geofence(
        "south-africa-wide", -35.0, 16.0, -22.0, 33.0
    );

    private final AnimalStore animalStore;
    private final Geofence geofence;

    public SensorDataWebhookRoute(AnimalStore animalStore) {
        this.animalStore = animalStore;
        this.geofence    = parseGeofenceEnv();
    }

    @Override public String method() { return "POST"; }
    @Override public String path()   { return PATH; }

    @Override
    public RouteHandler handler() {
        return (method, path, headers, body, response) -> {
            if (!"POST".equalsIgnoreCase(method)) {
                response.status(405);
                response.json("{\"error\":\"method_not_allowed\"}");
                return;
            }
            handleSensorData(body, response);
        };
    }

    private void handleSensorData(InputStream body, RouteResponse response) {
        try {
            byte[] raw = body.readAllBytes();
            if (raw.length == 0) {
                response.status(200);
                response.json("{\"status\":\"empty\"}");
                return;
            }

            Map<String, Object> payload = JSON.readValue(raw, MAP_TYPE);

            // ACK immediately — processing is async
            response.status(200);
            response.json("{\"status\":\"accepted\"}");

            Thread.ofVirtual().start(() -> processPayload(payload));

        } catch (Exception e) {
            LOG.warn("SensorDataWebhookRoute: failed to parse sensor payload", e);
            response.status(400);
            response.json("{\"error\":\"invalid_payload\"}");
        }
    }

    private void processPayload(Map<String, Object> payload) {
        try {
            SensorReading reading = extractReading(payload);
            if (reading == null) {
                LOG.warn("SensorDataWebhookRoute: could not extract sensor reading from payload");
                return;
            }

            LOG.info("Sensor update: device={} lat={} lng={} activity={} nearWater={}",
                reading.deviceId(), reading.lat(), reading.lng(), reading.activity(), reading.nearWater());

            if (animalStore == null) {
                LOG.warn("SensorDataWebhookRoute: no AnimalStore configured, dropping sensor data");
                return;
            }

            // Load existing or create new animal
            Animal existing = animalStore.findById(reading.deviceId())
                .orElseGet(() -> Animal.create(reading.deviceId(), "unknown", reading.lat(), reading.lng()));

            // Update location
            Animal updated = existing.withLocation(reading.lat(), reading.lng(), existing.section());

            // Update activity if provided
            if (reading.activity() >= 0) {
                updated = updated.withActivity(reading.activity());
            }

            // Update water visit if near trough
            if (reading.nearWater()) {
                updated = updated.withWaterVisit();
            }

            // Check geofence
            boolean inside = geofence.contains(reading.lat(), reading.lng());
            updated = updated.withGeofenceStatus(inside);

            if (!inside) {
                LOG.warn("GEOFENCE BREACH: animal {} at {},{} is outside farm boundary",
                    reading.deviceId(), reading.lat(), reading.lng());
            }

            animalStore.upsert(updated);

        } catch (Exception e) {
            LOG.warn("SensorDataWebhookRoute: failed to process sensor payload", e);
        }
    }

    /**
     * Tries TTN format first, then falls back to the generic flat format.
     * Returns {@code null} if the payload cannot be parsed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private SensorReading extractReading(Map<String, Object> payload) {
        // ── TTN format ──────────────────────────────────────────────────────────
        // { "end_device_ids": {"device_id": "..."}, "uplink_message": { "decoded_payload": {...} } }
        Object endDeviceIds = payload.get("end_device_ids");
        Object uplinkMessage = payload.get("uplink_message");
        if (endDeviceIds instanceof Map endDeviceMap && uplinkMessage instanceof Map uplinkMap) {
            String deviceId = String.valueOf(endDeviceMap.getOrDefault("device_id", "")).trim();
            Object decodedPayload = uplinkMap.get("decoded_payload");
            if (!deviceId.isBlank() && decodedPayload instanceof Map decodedMap) {
                double lat      = toDouble(decodedMap.get("latitude"),  0.0);
                double lng      = toDouble(decodedMap.get("longitude"), 0.0);
                double activity = toDouble(decodedMap.get("activity"),  -1.0);
                boolean nearWater = Boolean.TRUE.equals(decodedMap.get("near_water"));
                return new SensorReading(deviceId, lat, lng, activity, nearWater);
            }
        }

        // ── Generic flat format ─────────────────────────────────────────────────
        // { "device_id": "...", "lat": ..., "lng": ..., "activity": ..., "near_water": ... }
        Object deviceIdObj = payload.get("device_id");
        if (deviceIdObj != null) {
            String deviceId = String.valueOf(deviceIdObj).trim();
            if (!deviceId.isBlank()) {
                double lat      = toDouble(payload.get("lat"),      0.0);
                double lng      = toDouble(payload.get("lng"),      0.0);
                double activity = toDouble(payload.get("activity"), -1.0);
                boolean nearWater = Boolean.TRUE.equals(payload.get("near_water"));
                return new SensorReading(deviceId, lat, lng, activity, nearWater);
            }
        }

        return null;
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Geofence parseGeofenceEnv() {
        String env = System.getenv("LIVESTOCK_GEOFENCE");
        if (env == null || env.isBlank()) {
            return DEFAULT_GEOFENCE;
        }
        try {
            String[] parts = env.split(",");
            if (parts.length != 4) {
                LOG.warn("LIVESTOCK_GEOFENCE must be 'minLat,minLng,maxLat,maxLng' — using default SA bounding box");
                return DEFAULT_GEOFENCE;
            }
            return new Geofence(
                "farm-geofence",
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()),
                Double.parseDouble(parts[3].trim())
            );
        } catch (Exception e) {
            LOG.warn("Failed to parse LIVESTOCK_GEOFENCE env var '{}' — using default SA bounding box", env);
            return DEFAULT_GEOFENCE;
        }
    }

    private record SensorReading(
        String deviceId,
        double lat,
        double lng,
        double activity,
        boolean nearWater
    ) {}
}
