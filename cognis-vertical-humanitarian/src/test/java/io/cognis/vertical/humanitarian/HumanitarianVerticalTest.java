package io.cognis.vertical.humanitarian;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.humanitarian.heartbeat.MorningBriefHeartbeatJob;
import io.cognis.vertical.humanitarian.heartbeat.OverdueShipmentHeartbeatJob;
import io.cognis.vertical.humanitarian.supply.SupplyTrackingTool;
import io.cognis.vertical.humanitarian.webhook.SmsWebhookRoute;
import io.cognis.vertical.humanitarian.webhook.WhatsAppWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HumanitarianVerticalTest {

    @TempDir
    Path tempDir;

    @Test
    void nameIsHumanitarian() {
        assertThat(new HumanitarianVertical().name()).isEqualTo("humanitarian");
    }

    @Test
    void toolsContainsSupplyTrackingTool() {
        HumanitarianVertical vertical = new HumanitarianVertical();
        assertThat(vertical.tools())
            .hasSize(1)
            .first()
            .isInstanceOf(SupplyTrackingTool.class);
    }

    @Test
    void routesContainsSmsAndWhatsAppWebhooks() {
        HumanitarianVertical vertical = new HumanitarianVertical();
        assertThat(vertical.routes()).hasSize(2);
        assertThat(vertical.routes().stream().map(r -> r.getClass().getSimpleName()).toList())
            .containsExactlyInAnyOrder("SmsWebhookRoute", "WhatsAppWebhookRoute");
        assertThat(vertical.routes().stream().map(r -> r.path()).toList())
            .containsExactlyInAnyOrder("/webhook/sms", "/webhook/whatsapp");
    }

    @Test
    void providersIsEmpty() {
        assertThat(new HumanitarianVertical().providers()).isEmpty();
    }

    @Test
    void heartbeatJobsContainsOverdueAndMorningBrief() {
        List<HeartbeatJob> jobs = new HumanitarianVertical().heartbeatJobs();
        assertThat(jobs).hasSize(2);
        assertThat(jobs.stream().map(HeartbeatJob::name).toList())
            .containsExactlyInAnyOrder(
                "humanitarian.overdue-shipment-check",
                "humanitarian.morning-brief"
            );
    }

    @Test
    void policyAllowsSupplyTrackingAndMemory() {
        VerticalPolicy policy = new HumanitarianVertical().policy();
        assertThat(policy.allowsTool("supply_tracking")).isTrue();
        assertThat(policy.allowsTool("memory")).isTrue();
        assertThat(policy.allowsTool("shell")).isFalse();
        assertThat(policy.allowsTool("payments")).isFalse();
        assertThat(policy.isPermissive()).isFalse();
    }

    @Test
    void initializeDoesNotThrow() {
        HumanitarianVertical vertical = new HumanitarianVertical();
        vertical.initialize(new ToolContext(tempDir));
    }
}
