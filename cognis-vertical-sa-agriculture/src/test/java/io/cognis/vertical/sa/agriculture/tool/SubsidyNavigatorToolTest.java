package io.cognis.vertical.sa.agriculture.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubsidyNavigatorToolTest {

    @TempDir Path tempDir;

    private final SubsidyNavigatorTool tool = new SubsidyNavigatorTool();
    private ToolContext ctx() { return new ToolContext(tempDir); }

    @Test
    void nameAndDescriptionAreSet() {
        assertThat(tool.name()).isEqualTo("subsidy_navigator");
        assertThat(tool.description()).contains("CASP").contains("Land Bank");
    }

    @Test
    void listProgrammesWithoutProvinceReturnsAll() {
        String result = tool.execute(Map.of("action", "list_programmes"), ctx());
        assertThat(result)
            .contains("CASP")
            .contains("RECAP")
            .contains("Ilima")
            .contains("Land Bank")
            .contains("AgriSETA");
    }

    @Test
    void listProgrammesFiltersByProvince() {
        String result = tool.execute(Map.of("action", "list_programmes", "province", "limpopo"), ctx());
        assertThat(result).contains("CASP");
    }

    @Test
    void checkEligibilityWithoutProvinceAsksForProvince() {
        String result = tool.execute(Map.of("action", "check_eligibility"), ctx());
        assertThat(result).containsIgnoringCase("province");
    }

    @Test
    void checkEligibilityWithProvinceAndHectaresReturnsMatches() {
        String result = tool.execute(
            Map.of("action", "check_eligibility", "province", "limpopo", "hectares", 5.0), ctx());
        assertThat(result).contains("qualify");
    }

    @Test
    void getProgrammeDetailsForCaspReturnsFullInfo() {
        String result = tool.execute(Map.of("action", "get_programme_details", "programme_id", "casp"), ctx());
        assertThat(result)
            .contains("CASP")
            .contains("DAFF")
            .contains("apply", "Apply");
    }

    @Test
    void getProgrammeDetailsForLandBankReturnsLoanInfo() {
        String result = tool.execute(
            Map.of("action", "get_programme_details", "programme_id", "land_bank"), ctx());
        assertThat(result).contains("Land Bank").containsIgnoringCase("loan");
    }

    @Test
    void getProgrammeDetailsForUnknownIdReturnsError() {
        String result = tool.execute(
            Map.of("action", "get_programme_details", "programme_id", "unknown_xyz"), ctx());
        assertThat(result).contains("not found");
    }

    @Test
    void getProgrammeDetailsWithBlankIdListsAll() {
        String result = tool.execute(Map.of("action", "get_programme_details"), ctx());
        assertThat(result).contains("programme_id");
    }

    @Test
    void unknownActionReturnsError() {
        String result = tool.execute(Map.of("action", "wrong"), ctx());
        assertThat(result).contains("Unknown action");
    }
}
