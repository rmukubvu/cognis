package io.cognis.vertical.sa.agriculture.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Persistent profile for a South African emerging farmer, keyed by phone number.
 *
 * <p>Captures agronomic context (crops, hectares, province) and communication preferences
 * (language) so the agent can give contextualised, localised advice across sessions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FarmerProfile(
    String phone,
    String alias,
    String language,
    String province,
    List<String> crops,
    double hectares,
    String daffId,
    Instant registeredAt,
    Instant lastSeen
) {

    /** Supported SA languages. */
    public static final List<String> SUPPORTED_LANGUAGES =
        List.of("english", "zulu", "xhosa", "sotho", "tswana", "afrikaans");

    /** SA provinces. */
    public static final List<String> PROVINCES = List.of(
        "limpopo", "mpumalanga", "kwazulu-natal", "free-state",
        "north-west", "northern-cape", "western-cape", "eastern-cape", "gauteng"
    );

    @JsonCreator
    public FarmerProfile(
        @JsonProperty("phone")        String phone,
        @JsonProperty("alias")        String alias,
        @JsonProperty("language")     String language,
        @JsonProperty("province")     String province,
        @JsonProperty("crops")        List<String> crops,
        @JsonProperty("hectares")     double hectares,
        @JsonProperty("daffId")       String daffId,
        @JsonProperty("registeredAt") Instant registeredAt,
        @JsonProperty("lastSeen")     Instant lastSeen
    ) {
        this.phone        = phone        == null ? "" : phone;
        this.alias        = alias        == null ? "" : alias;
        this.language     = language     == null ? "english" : language;
        this.province     = province     == null ? "" : province;
        this.crops        = crops        == null ? List.of() : List.copyOf(crops);
        this.hectares     = hectares;
        this.daffId       = daffId       == null ? "" : daffId;
        this.registeredAt = registeredAt == null ? Instant.now() : registeredAt;
        this.lastSeen     = lastSeen     == null ? Instant.now() : lastSeen;
    }

    /** Creates a new profile with defaults. */
    public static FarmerProfile create(String phone) {
        Instant now = Instant.now();
        return new FarmerProfile(phone, "", "english", "", List.of(), 0.0, "", now, now);
    }

    public FarmerProfile withAlias(String newAlias) {
        return new FarmerProfile(phone, newAlias, language, province, crops, hectares, daffId, registeredAt, Instant.now());
    }

    public FarmerProfile withLanguage(String newLanguage) {
        return new FarmerProfile(phone, alias, newLanguage, province, crops, hectares, daffId, registeredAt, Instant.now());
    }

    public FarmerProfile withProvince(String newProvince) {
        return new FarmerProfile(phone, alias, language, newProvince, crops, hectares, daffId, registeredAt, Instant.now());
    }

    public FarmerProfile withCrops(List<String> newCrops) {
        return new FarmerProfile(phone, alias, language, province, newCrops, hectares, daffId, registeredAt, Instant.now());
    }

    public FarmerProfile withHectares(double newHectares) {
        return new FarmerProfile(phone, alias, language, province, crops, newHectares, daffId, registeredAt, Instant.now());
    }

    public FarmerProfile withDaffId(String newDaffId) {
        return new FarmerProfile(phone, alias, language, province, crops, hectares, newDaffId, registeredAt, Instant.now());
    }

    public FarmerProfile withLastSeen() {
        return new FarmerProfile(phone, alias, language, province, crops, hectares, daffId, registeredAt, Instant.now());
    }

    /** Returns true if the profile has enough context for personalised advice. */
    public boolean isEnriched() {
        return !province.isBlank() && !crops.isEmpty();
    }
}
