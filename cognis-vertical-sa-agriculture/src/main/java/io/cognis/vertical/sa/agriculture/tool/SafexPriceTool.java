package io.cognis.vertical.sa.agriculture.tool;

import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.List;
import java.util.Map;

/**
 * Provides SAFEX (South African Futures Exchange) commodity price guidance and
 * Johannesburg Fresh Produce Market (JFPM) price references.
 *
 * <p>Supported actions:
 * <ul>
 *   <li>{@code get_commodity_price} — SAFEX futures price for a commodity</li>
 *   <li>{@code get_market_prices}   — fresh produce market prices by province</li>
 *   <li>{@code get_price_advice}    — sell-now vs hold recommendation</li>
 * </ul>
 *
 * <p>Returns reference price data and guidance text. In production, replace the
 * static data with live calls to SAFEX or JFPM data feeds.
 */
public final class SafexPriceTool implements Tool {

    /** Reference SAFEX prices in ZAR per tonne (indicative, for demo/advisory use). */
    private static final Map<String, CommodityData> COMMODITIES = Map.of(
        "maize",      new CommodityData("Maize (Yellow)",    3_850,  3_200, "bag (90kg)"),
        "wheat",      new CommodityData("Wheat",             5_900,  4_800, "tonne"),
        "sunflower",  new CommodityData("Sunflower seed",    7_200,  5_900, "tonne"),
        "soybeans",   new CommodityData("Soybeans",          8_100,  6_700, "tonne"),
        "sorghum",    new CommodityData("Sorghum",           3_400,  2_800, "tonne"),
        "sugarcane",  new CommodityData("Sugarcane",         650,    520,   "tonne")
    );

    @Override
    public String name() {
        return "safex_price";
    }

    @Override
    public String description() {
        return "SA commodity prices and market advice. Actions: get_commodity_price, get_market_prices, get_price_advice. " +
               "Commodities: maize, wheat, sunflower, soybeans, sorghum, sugarcane.";
    }

    @Override
    public Map<String, Object> schema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "action", Map.of(
                    "type", "string",
                    "enum", List.of("get_commodity_price", "get_market_prices", "get_price_advice")
                ),
                "commodity", Map.of(
                    "type", "string",
                    "description", "Commodity name: maize, wheat, sunflower, soybeans, sorghum, sugarcane"
                ),
                "province", Map.of(
                    "type", "string",
                    "description", "SA province for market prices"
                ),
                "hectares", Map.of(
                    "type", "number",
                    "description", "Farm size in hectares for volume-based advice"
                )
            ),
            "required", List.of("action"),
            "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext ctx) {
        String action    = (String) input.get("action");
        String commodity = ((String) input.getOrDefault("commodity", "")).toLowerCase();
        String province  = (String) input.getOrDefault("province", "");
        double hectares  = input.get("hectares") instanceof Number n ? n.doubleValue() : 0.0;

        return switch (action) {
            case "get_commodity_price" -> getCommodityPrice(commodity);
            case "get_market_prices"   -> getMarketPrices(province);
            case "get_price_advice"    -> getPriceAdvice(commodity, hectares);
            default -> "Unknown action: " + action + ". Use: get_commodity_price, get_market_prices, get_price_advice";
        };
    }

    private String getCommodityPrice(String commodity) {
        if (commodity.isBlank()) {
            return buildAllPrices();
        }
        CommodityData data = COMMODITIES.get(commodity);
        if (data == null) {
            return "Commodity '" + commodity + "' not found. Available: " + String.join(", ", COMMODITIES.keySet());
        }
        return """
            SAFEX Reference Price — %s
            Current: R %,.0f per %s
            Floor (30-day low): R %,.0f per %s
            Source: SAFEX indicative — verify at www.jse.co.za before trading
            """.formatted(data.name(), data.current(), data.unit(), data.floor(), data.unit());
    }

    private String buildAllPrices() {
        StringBuilder sb = new StringBuilder("SAFEX Reference Prices (ZAR):\n");
        COMMODITIES.forEach((k, v) ->
            sb.append("  %-12s R %,6.0f / %s%n".formatted(v.name(), v.current(), v.unit()))
        );
        sb.append("Source: indicative — verify at www.jse.co.za");
        return sb.toString();
    }

    private String getMarketPrices(String province) {
        return switch (province.trim().toLowerCase()) {
            case "gauteng", "mpumalanga", "limpopo" ->
                "Johannesburg Fresh Produce Market (City Deep):\n" +
                "  Tomatoes: R 180-240/box | Spinach: R 45-65/bag | Onions: R 95-130/bag\n" +
                "  Potatoes: R 120-160/bag | Cabbages: R 55-80/head\n" +
                "Open: Mon-Sat 06:00-14:00. Tel: +27 11 992 8000";
            case "kwazulu-natal" ->
                "Durban Fresh Produce Market:\n" +
                "  Tomatoes: R 165-220/box | Peppers: R 90-130/box | Beans: R 75-100/bag\n" +
                "Open: Mon-Sat 05:30-13:00. Tel: +27 31 205 1891";
            case "western-cape" ->
                "Cape Town Market (Epping):\n" +
                "  Tomatoes: R 195-260/box | Grapes: R 140-200/box | Citrus: R 85-120/box\n" +
                "Open: Mon-Fri 06:00-14:00. Tel: +27 21 505 1000";
            case "free-state", "north-west", "northern-cape" ->
                "Tshwane Fresh Produce Market:\n" +
                "  Maize meal: R 380-420/bag | Potatoes: R 130-170/bag | Carrots: R 60-85/bag\n" +
                "Open: Mon-Sat 06:00-13:30. Tel: +27 12 327 7200";
            default ->
                "Specify a province to get local market prices. " +
                "Main markets: Johannesburg (Gauteng/Limpopo/Mpumalanga), Durban (KZN), " +
                "Cape Town (Western Cape), Tshwane (Free State/North West).";
        };
    }

    private String getPriceAdvice(String commodity, double hectares) {
        CommodityData data = COMMODITIES.get(commodity);
        if (data == null) {
            return "Provide a commodity name for price advice: maize, wheat, sunflower, soybeans, sorghum, sugarcane.";
        }
        double pctAboveFloor = ((data.current() - data.floor()) / data.floor()) * 100;
        String trend = pctAboveFloor > 15 ? "above seasonal floor by %.0f%% — favourable selling window".formatted(pctAboveFloor)
                     : pctAboveFloor > 5  ? "moderately above floor by %.0f%% — acceptable to sell".formatted(pctAboveFloor)
                                          : "near seasonal floor — consider holding if you have storage";
        String volumeNote = hectares > 0
            ? "\nEstimated yield at 4t/ha: %,.0f tonnes | At current price: R %,.0f gross".formatted(
                hectares * 4, hectares * 4 * data.current())
            : "";

        return """
            Price advice — %s
            Current SAFEX: R %,.0f / %s
            Trend: %s
            %s
            Always confirm with your local co-op or Land Bank before selling.
            """.formatted(data.name(), data.current(), data.unit(), trend, volumeNote).trim();
    }

    private record CommodityData(String name, double current, double floor, String unit) {}
}
