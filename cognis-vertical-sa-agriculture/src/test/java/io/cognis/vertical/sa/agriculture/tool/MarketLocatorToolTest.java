package io.cognis.vertical.sa.agriculture.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketLocatorToolTest {

    @TempDir Path tempDir;

    private final MarketLocatorTool tool = new MarketLocatorTool();
    private ToolContext ctx() { return new ToolContext(tempDir); }

    @Test
    void nameAndDescriptionAreSet() {
        assertThat(tool.name()).isEqualTo("market_locator");
        assertThat(tool.description()).contains("market");
    }

    @Test
    void findNearestMarketForGautengReturnsJhb() {
        String result = tool.execute(Map.of("action", "find_nearest_market", "province", "gauteng"), ctx());
        assertThat(result).contains("Johannesburg");
    }

    @Test
    void findNearestMarketForKznReturnsDurban() {
        String result = tool.execute(Map.of("action", "find_nearest_market", "province", "kwazulu-natal"), ctx());
        assertThat(result).contains("Durban");
    }

    @Test
    void findNearestMarketForWesternCapeReturnsCT() {
        String result = tool.execute(Map.of("action", "find_nearest_market", "province", "western-cape"), ctx());
        assertThat(result).contains("Cape Town");
    }

    @Test
    void findNearestMarketWithoutProvinceAsksForIt() {
        String result = tool.execute(Map.of("action", "find_nearest_market"), ctx());
        assertThat(result).containsIgnoringCase("province");
    }

    @Test
    void findNearestMarketForUnknownProvinceReturnsError() {
        String result = tool.execute(Map.of("action", "find_nearest_market", "province", "atlantis"), ctx());
        assertThat(result).contains("not recognised");
    }

    @Test
    void listCooperativesForLimpopoReturnsSugarAndGrain() {
        String result = tool.execute(Map.of("action", "list_cooperatives", "province", "limpopo"), ctx());
        assertThat(result).contains("Limpopo").contains("maize");
    }

    @Test
    void listCooperativesForUnknownProvinceReturnsFallback() {
        String result = tool.execute(Map.of("action", "list_cooperatives", "province", "unknown"), ctx());
        assertThat(result).contains("Grain SA");
    }

    @Test
    void getTransportTipsReturnsContent() {
        String result = tool.execute(Map.of("action", "get_transport_tips", "province", "gauteng"), ctx());
        assertThat(result).contains("Harvest").contains("packaging");
    }

    @Test
    void unknownActionReturnsError() {
        String result = tool.execute(Map.of("action", "invalid"), ctx());
        assertThat(result).contains("Unknown action");
    }
}
