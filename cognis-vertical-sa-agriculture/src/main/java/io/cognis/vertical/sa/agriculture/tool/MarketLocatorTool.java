package io.cognis.vertical.sa.agriculture.tool;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Locates the nearest South African fresh produce market or agricultural co-operative
 * for a given province and crop type, with operating hours and contact details.
 */
public final class MarketLocatorTool implements Tool {

    private static final Map<String, MarketInfo> PROVINCE_MARKETS = Map.of(
        "gauteng",       new MarketInfo("Johannesburg Fresh Produce Market", "City Deep, Johannesburg",
                                        "Mon-Sat 06:00-14:00", "+27 11 992 8000", "www.jfpm.co.za"),
        "limpopo",       new MarketInfo("Johannesburg Fresh Produce Market", "City Deep (2h from Polokwane)",
                                        "Mon-Sat 06:00-14:00", "+27 11 992 8000", "www.jfpm.co.za"),
        "mpumalanga",    new MarketInfo("Tshwane Fresh Produce Market", "Pretoria West",
                                        "Mon-Sat 06:00-13:30", "+27 12 327 7200", "www.tshwanemarket.co.za"),
        "kwazulu-natal", new MarketInfo("Durban Fresh Produce Market", "Mobeni, Durban",
                                        "Mon-Sat 05:30-13:00", "+27 31 205 1891", "www.dfpm.co.za"),
        "free-state",    new MarketInfo("Bloemfontein Fresh Produce Market", "Pellissier, Bloemfontein",
                                        "Mon-Sat 06:00-13:00", "+27 51 408 8911", "bloemfontein.gov.za/market"),
        "western-cape",  new MarketInfo("Cape Town Market", "Epping Industrial, Cape Town",
                                        "Mon-Fri 06:00-14:00", "+27 21 505 1000", "www.capetownmarket.co.za"),
        "eastern-cape",  new MarketInfo("East London Market", "Braelyn, East London",
                                        "Mon-Sat 06:00-13:00", "+27 43 705 2000", "buffalocity.gov.za"),
        "north-west",    new MarketInfo("Klerksdorp Fresh Produce Market", "Klerksdorp",
                                        "Mon-Sat 06:00-13:00", "+27 18 462 3145", "klerksdorp.co.za"),
        "northern-cape", new MarketInfo("Kimberley Fresh Produce Market", "Kimberley",
                                        "Mon-Sat 06:00-13:00", "+27 53 830 6911", "sol-plaatje.gov.za")
    );

    @Override
    public String name() {
        return "market_locator";
    }

    @Override
    public String description() {
        return "Find nearest SA fresh produce market or co-op by province. " +
               "Actions: find_nearest_market, list_cooperatives, get_transport_tips.";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("find_nearest_market", "list_cooperatives", "get_transport_tips")
                ),
                "province", Map.of(
                    "type", "string",
                    "description", "SA province name"
                ),
                "crop", Map.of(
                    "type", "string",
                    "description", "Crop type for co-op matching (e.g. maize, wheat, sugarcane, citrus)"
                )
            ),
            "required", List.of("action"),
            "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        String action   = (String) input.get("action");
        String province = ((String) input.getOrDefault("province", "")).toLowerCase().trim();
        String crop     = ((String) input.getOrDefault("crop", "")).toLowerCase().trim();

        return switch (action) {
            case "find_nearest_market"  -> findNearestMarket(province);
            case "list_cooperatives"    -> listCooperatives(province, crop);
            case "get_transport_tips"   -> getTransportTips(province);
            default -> "Unknown action: " + action;
        };
    }

    private String findNearestMarket(String province) {
        if (province.isBlank()) {
            return "Please provide your province to find the nearest market.";
        }
        MarketInfo market = PROVINCE_MARKETS.get(province);
        if (market == null) {
            return "Province '" + province + "' not recognised. Available: " +
                   String.join(", ", PROVINCE_MARKETS.keySet());
        }
        return """
            Nearest Market for %s:
            Name:    %s
            Address: %s
            Hours:   %s
            Tel:     %s
            Web:     %s
            Tip: Arrive early — best prices before 08:00. Bring produce in clean, graded bags/boxes.
            """.formatted(capitalize(province), market.name(), market.address(),
                          market.hours(), market.phone(), market.website()).trim();
    }

    private String listCooperatives(String province, String crop) {
        return switch (province) {
            case "limpopo" ->
                "Key co-operatives in Limpopo:\n" +
                "  Limpopo Agri — maize, sunflower, sorghum | +27 15 291 4177\n" +
                "  Tzaneen Agri — tropical fruit, avocados  | +27 15 307 3700\n" +
                "  AFGRI Limpopo — grain inputs & marketing | +27 15 293 1200";
            case "mpumalanga" ->
                "Key co-operatives in Mpumalanga:\n" +
                "  Mpumalanga Agri — maize, wheat, soybeans | +27 13 752 2561\n" +
                "  NTK Co-op — grain, livestock              | +27 13 265 1500\n" +
                "  Sugarcane: SMRI Lowveld                  | +27 13 759 2058";
            case "kwazulu-natal" ->
                "Key co-operatives in KwaZulu-Natal:\n" +
                "  Illovo Sugar — sugarcane contracts         | +27 31 508 1000\n" +
                "  NCT Forestry — timber (long-term)          | +27 33 897 8200\n" +
                "  KZNAMC — maize & grain marketing           | +27 33 345 1200";
            case "western-cape" ->
                "Key co-operatives in Western Cape:\n" +
                "  VinPro — wine grapes & viticulture support | +27 21 807 3390\n" +
                "  Agri Western Cape — mixed produce          | +27 21 975 4440\n" +
                "  Subtrop — citrus, subtropical fruit        | +27 15 307 3700";
            default ->
                "Grain SA is the national grain co-operative umbrella body — contact them for your area: " +
                "+27 12 943 8000 | www.grainsa.co.za. " +
                "Also try Agri SA for province-specific referrals: +27 12 643 3400 | www.agrisa.co.za";
        };
    }

    private String getTransportTips(String province) {
        return """
            Produce Transport Tips for %s:
            • Harvest early morning — cooler temperatures preserve quality
            • Use clean, food-grade packaging; grade by size before market
            • Share transport with neighbouring farmers to reduce cost (contact local agri office)
            • Inform the market agent 24h ahead for guaranteed off-loading slots
            • For fresh produce: ice or cool-storage recommended for journeys > 3 hours
            • Keep SAPS roadworthy certificate and loading permit up to date
            Contact your nearest DAFF extension officer for transport subsidy programmes.
            """.formatted(province.isBlank() ? "SA" : capitalize(province)).trim();
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private record MarketInfo(String name, String address, String hours, String phone, String website) {}
}
