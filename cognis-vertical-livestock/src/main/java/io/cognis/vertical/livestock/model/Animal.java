package io.cognis.vertical.livestock.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Represents a tagged livestock animal with GPS location, activity, and geofence status.
 *
 * <p>Keyed by {@code id} (LoRaWAN EUI / RFID tag number). Immutable — all updates
 * return new instances via the {@code with*()} helpers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Animal(
    String id,
    String name,
    String species,
    String section,
    double lat,
    double lng,
    Instant lastSeen,
    double activityLevel,
    Instant lastWaterVisit,
    boolean insideGeofence
) {

    @JsonCreator
    public Animal(
        @JsonProperty("id")              String id,
        @JsonProperty("name")            String name,
        @JsonProperty("species")         String species,
        @JsonProperty("section")         String section,
        @JsonProperty("lat")             double lat,
        @JsonProperty("lng")             double lng,
        @JsonProperty("lastSeen")        Instant lastSeen,
        @JsonProperty("activityLevel")   double activityLevel,
        @JsonProperty("lastWaterVisit")  Instant lastWaterVisit,
        @JsonProperty("insideGeofence")  boolean insideGeofence
    ) {
        this.id             = id             == null ? "" : id;
        this.name           = name           == null ? "" : name;
        this.species        = species        == null ? "unknown" : species;
        this.section        = section        == null ? "Unknown" : section;
        this.lat            = lat;
        this.lng            = lng;
        this.lastSeen       = lastSeen       == null ? Instant.now() : lastSeen;
        this.activityLevel  = activityLevel;
        this.lastWaterVisit = lastWaterVisit;
        this.insideGeofence = insideGeofence;
    }

    /** Creates a new animal with minimal defaults. */
    public static Animal create(String id, String species, double lat, double lng) {
        return new Animal(id, "", species, "Unknown", lat, lng, Instant.now(), 1.0, null, true);
    }

    public Animal withLocation(double lat, double lng, String section) {
        return new Animal(id, name, species, section, lat, lng, Instant.now(), activityLevel, lastWaterVisit, insideGeofence);
    }

    public Animal withActivity(double activityLevel) {
        return new Animal(id, name, species, section, lat, lng, lastSeen, activityLevel, lastWaterVisit, insideGeofence);
    }

    public Animal withWaterVisit() {
        return new Animal(id, name, species, section, lat, lng, lastSeen, activityLevel, Instant.now(), insideGeofence);
    }

    public Animal withGeofenceStatus(boolean inside) {
        return new Animal(id, name, species, section, lat, lng, lastSeen, activityLevel, lastWaterVisit, inside);
    }
}
