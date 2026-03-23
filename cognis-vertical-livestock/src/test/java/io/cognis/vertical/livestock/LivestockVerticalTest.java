package io.cognis.vertical.livestock;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.heartbeat.HeartbeatJob;
import io.cognis.core.sandbox.VerticalPolicy;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.vertical.livestock.heartbeat.HealthAnomalyJob;
import io.cognis.vertical.livestock.heartbeat.MorningHerdBriefJob;
import io.cognis.vertical.livestock.heartbeat.NightGeofenceJob;
import io.cognis.vertical.livestock.tool.GeofenceTool;
import io.cognis.vertical.livestock.tool.HealthAlertTool;
import io.cognis.vertical.livestock.tool.HerdLocationTool;
import io.cognis.vertical.livestock.tool.WaterMonitorTool;
import io.cognis.vertical.livestock.webhook.SensorDataWebhookRoute;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LivestockVerticalTest {

    @TempDir Path tempDir;

    private LivestockVertical initializedVertical() {
        LivestockVertical v = new LivestockVertical();
        v.initialize(new ToolContext(tempDir));
        return v;
    }

    @Test
    void nameIsLivestock() {
        assertThat(new LivestockVertical().name()).isEqualTo("livestock");
    }

    @Test
    void toolsContainsFourTools() {
        List<Tool> tools = new LivestockVertical().tools();
        assertThat(tools).hasSize(4);
    }

    @Test
    void toolNamesAreCorrect() {
        List<Tool> tools = new LivestockVertical().tools();
        assertThat(tools.stream().map(Tool::name).toList())
            .containsExactlyInAnyOrder("herd_location", "geofence_check", "health_alert", "water_monitor");
    }

    @Test
    void toolClassesAreCorrect() {
        List<Tool> tools = new LivestockVertical().tools();
        assertThat(tools.stream().map(t -> t.getClass().getSimpleName()).toList())
            .containsExactlyInAnyOrder("HerdLocationTool", "GeofenceTool", "HealthAlertTool", "WaterMonitorTool");
    }

    @Test
    void routesContainsSensorDataWebhook() {
        LivestockVertical v = initializedVertical();
        assertThat(v.routes()).hasSize(1);
        assertThat(v.routes().get(0)).isInstanceOf(SensorDataWebhookRoute.class);
        assertThat(v.routes().get(0).path()).isEqualTo("/webhook/livestock/sensor");
        assertThat(v.routes().get(0).method()).isEqualTo("POST");
    }

    @Test
    void heartbeatJobsContainsThreeJobs() {
        List<HeartbeatJob> jobs = new LivestockVertical().heartbeatJobs();
        assertThat(jobs).hasSize(3);
        assertThat(jobs.stream().map(j -> j.getClass().getSimpleName()).toList())
            .containsExactlyInAnyOrder("MorningHerdBriefJob", "NightGeofenceJob", "HealthAnomalyJob");
    }

    @Test
    void heartbeatJobNamesAreCorrect() {
        List<HeartbeatJob> jobs = new LivestockVertical().heartbeatJobs();
        assertThat(jobs.stream().map(HeartbeatJob::name).toList())
            .containsExactlyInAnyOrder(
                "livestock.morning-herd-brief",
                "livestock.night-geofence-watch",
                "livestock.health-anomaly-check"
            );
    }

    @Test
    void heartbeatJobCronExpressionsAreCorrect() {
        List<HeartbeatJob> jobs = new LivestockVertical().heartbeatJobs();
        assertThat(jobs.stream().filter(j -> j instanceof MorningHerdBriefJob)
            .map(HeartbeatJob::cronExpression).findFirst())
            .hasValue("0 4 * * *");
        assertThat(jobs.stream().filter(j -> j instanceof NightGeofenceJob)
            .map(HeartbeatJob::cronExpression).findFirst())
            .hasValue("0/30 20-23,0-6 * * *");
        assertThat(jobs.stream().filter(j -> j instanceof HealthAnomalyJob)
            .map(HeartbeatJob::cronExpression).findFirst())
            .hasValue("0 */4 * * *");
    }

    @Test
    void policyIsNotPermissive() {
        VerticalPolicy policy = new LivestockVertical().policy();
        assertThat(policy.isPermissive()).isFalse();
    }

    @Test
    void policyAllowsLivestockToolsAndDeniesOthers() {
        VerticalPolicy policy = new LivestockVertical().policy();
        assertThat(policy.allowsTool("herd_location")).isTrue();
        assertThat(policy.allowsTool("geofence_check")).isTrue();
        assertThat(policy.allowsTool("health_alert")).isTrue();
        assertThat(policy.allowsTool("water_monitor")).isTrue();
        assertThat(policy.allowsTool("web")).isTrue();
        assertThat(policy.allowsTool("notify")).isTrue();
        assertThat(policy.allowsTool("shell")).isFalse();
        assertThat(policy.allowsTool("payments")).isFalse();
        assertThat(policy.allowsTool("safex_price")).isFalse();
    }

    @Test
    void initializeWithEmptyContextDoesNotThrow() {
        LivestockVertical v = new LivestockVertical();
        v.initialize(new ToolContext(tempDir));
    }

    @Test
    void providersIsEmpty() {
        assertThat(new LivestockVertical().providers()).isEmpty();
    }

    @Test
    void systemPromptContainsKeyElements() {
        assertThat(LivestockVertical.SYSTEM_PROMPT)
            .contains("livestock")
            .contains("theft")
            .contains("geofence")
            .contains("South Africa")
            .contains("water");
    }
}
