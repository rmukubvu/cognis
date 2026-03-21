package io.cognis.vertical.humanitarian.supply;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SupplyTrackingToolTest {

    @TempDir
    Path tempDir;

    private SupplyTrackingTool tool;
    private InMemorySupplyStore store;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        tool = new SupplyTrackingTool();
        store = new InMemorySupplyStore();
        ctx = new ToolContext(tempDir, Map.of("supplyStore", store));
    }

    @Test
    void toolNameAndDescriptionAreSet() {
        assertThat(tool.name()).isEqualTo("supply_tracking");
        assertThat(tool.description()).isNotBlank();
    }

    @Test
    void logDispatchCreatesConsignment() {
        String result = tool.execute(Map.of(
            "action", "log_dispatch",
            "consignment_id", "C-001",
            "location", "Nairobi Warehouse"
        ), ctx);

        assertThat(result).contains("C-001").contains("dispatched");
        assertThat(store.findById("C-001")).isPresent();
        assertThat(store.findById("C-001").get().status()).isEqualTo(ConsignmentStatus.DISPATCHED);
    }

    @Test
    void confirmDeliveryUpdatesStatus() {
        tool.execute(Map.of("action", "log_dispatch", "consignment_id", "C-002", "location", "Kampala"), ctx);
        String result = tool.execute(Map.of(
            "action", "confirm_delivery",
            "consignment_id", "C-002",
            "location", "Gulu Health Centre"
        ), ctx);

        assertThat(result).contains("C-002").contains("delivered");
        assertThat(store.findById("C-002").get().status()).isEqualTo(ConsignmentStatus.DELIVERED);
    }

    @Test
    void checkStatusReturnsCurrentState() {
        tool.execute(Map.of("action", "log_dispatch", "consignment_id", "C-003", "location", "Dar es Salaam"), ctx);
        String result = tool.execute(Map.of("action", "check_status", "consignment_id", "C-003"), ctx);

        assertThat(result).contains("C-003").contains("DISPATCHED");
    }

    @Test
    void checkStatusReturnsNotFoundForMissingId() {
        String result = tool.execute(Map.of("action", "check_status", "consignment_id", "MISSING"), ctx);
        assertThat(result).contains("not found");
    }

    @Test
    void listOverdueReturnsOnlyOverdueConsignments() {
        InMemorySupplyStore localStore = new InMemorySupplyStore();
        ToolContext localCtx = new ToolContext(tempDir, Map.of("supplyStore", localStore));

        // dispatch two, manually set one to OVERDUE
        tool.execute(Map.of("action", "log_dispatch", "consignment_id", "C-010", "location", "A"), localCtx);
        tool.execute(Map.of("action", "log_dispatch", "consignment_id", "C-011", "location", "B"), localCtx);
        Consignment overdue = localStore.findById("C-010").get().withStatus(ConsignmentStatus.OVERDUE, "A");
        localStore.save(overdue);

        String result = tool.execute(Map.of("action", "list_overdue"), localCtx);
        assertThat(result).contains("C-010").doesNotContain("C-011");
    }

    @Test
    void listOverdueReturnsMessageWhenNonePresent() {
        String result = tool.execute(Map.of("action", "list_overdue"), ctx);
        assertThat(result).contains("No overdue");
    }

    @Test
    void logDispatchRequiresConsignmentId() {
        String result = tool.execute(Map.of("action", "log_dispatch"), ctx);
        assertThat(result).contains("Error").contains("consignment_id");
    }

    @Test
    void confirmDeliveryReturnsNotFoundForMissingConsignment() {
        String result = tool.execute(Map.of("action", "confirm_delivery", "consignment_id", "GHOST"), ctx);
        assertThat(result).contains("not found");
    }

    @Test
    void fallsBackToInMemoryStoreWhenNoStoreInContext() {
        ToolContext emptyCtx = new ToolContext(tempDir);
        String result = tool.execute(Map.of(
            "action", "log_dispatch",
            "consignment_id", "C-999",
            "location", "Fallback"
        ), emptyCtx);
        assertThat(result).contains("dispatched");
    }
}
