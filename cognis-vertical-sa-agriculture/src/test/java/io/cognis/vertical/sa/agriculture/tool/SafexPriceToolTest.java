package io.cognis.vertical.sa.agriculture.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafexPriceToolTest {

    @TempDir Path tempDir;

    private final SafexPriceTool tool = new SafexPriceTool();
    private ToolContext ctx() { return new ToolContext(tempDir); }

    @Test
    void nameAndDescriptionAreSet() {
        assertThat(tool.name()).isEqualTo("safex_price");
        assertThat(tool.description()).contains("commodity").contains("maize");
    }

    @Test
    void getCommodityPriceReturnsMaizeData() {
        String result = tool.execute(Map.of("action", "get_commodity_price", "commodity", "maize"), ctx());
        assertThat(result)
            .contains("Maize")
            .contains("R")
            .contains("bag");
    }

    @Test
    void getCommodityPriceWithoutCommodityReturnsAllPrices() {
        String result = tool.execute(Map.of("action", "get_commodity_price"), ctx());
        assertThat(result)
            .contains("Maize")
            .contains("Wheat")
            .contains("Sunflower");
    }

    @Test
    void getCommodityPriceForUnknownCommodityReturnsError() {
        String result = tool.execute(Map.of("action", "get_commodity_price", "commodity", "avocado"), ctx());
        assertThat(result).contains("not found");
    }

    @Test
    void getMarketPricesForGautengReturnsJhbMarket() {
        String result = tool.execute(Map.of("action", "get_market_prices", "province", "gauteng"), ctx());
        assertThat(result).contains("Johannesburg");
    }

    @Test
    void getMarketPricesForKznReturnsDurban() {
        String result = tool.execute(Map.of("action", "get_market_prices", "province", "kwazulu-natal"), ctx());
        assertThat(result).contains("Durban");
    }

    @Test
    void getPriceAdviceForMaizeContainsRecommendation() {
        String result = tool.execute(Map.of("action", "get_price_advice", "commodity", "maize"), ctx());
        assertThat(result).containsIgnoringCase("maize");
        assertThat(result).contains("R");
    }

    @Test
    void getPriceAdviceWithHectaresIncludesVolumeEstimate() {
        String result = tool.execute(
            Map.of("action", "get_price_advice", "commodity", "maize", "hectares", 10.0), ctx());
        assertThat(result).contains("tonnes");
    }

    @Test
    void unknownActionReturnsError() {
        String result = tool.execute(Map.of("action", "fly_drone"), ctx());
        assertThat(result).contains("Unknown action");
    }

    @Test
    void schemaHasRequiredAction() {
        Map<String, Object> schema = tool.schema();
        assertThat(schema.get("required")).isEqualTo(java.util.List.of("action"));
    }
}
