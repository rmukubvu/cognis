package io.cognis.vertical.livestock.model;

/**
 * Axis-aligned bounding-box geofence for a farm property.
 *
 * <p>Coordinates are WGS-84 decimal degrees. A farm geofence is typically configured
 * via the {@code LIVESTOCK_GEOFENCE} environment variable (minLat,minLng,maxLat,maxLng).
 */
public record Geofence(
    String name,
    double minLat,
    double minLng,
    double maxLat,
    double maxLng
) {

    /** Returns {@code true} if the given coordinates fall within this bounding box (inclusive). */
    public boolean contains(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }
}
