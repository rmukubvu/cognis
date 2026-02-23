package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.cron.CronService;
import io.cognis.core.cron.FileCronStore;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.workflow.WorkflowService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteDailyBriefAction() {
        WorkflowTool tool = new WorkflowTool();
        WorkflowService service = new WorkflowService(null, null, null, null);
        ToolContext context = new ToolContext(tempDir, Map.of("workflowService", service));

        String output = tool.execute(Map.of("action", "daily_brief"), context);

        assertThat(output).contains("Cognis Daily Brief");
        assertThat(output).contains("Top Priorities");
    }

    @Test
    void shouldScheduleGoalCheckinWhenCronServiceIsAvailable() throws Exception {
        WorkflowTool tool = new WorkflowTool();
        WorkflowService service = new WorkflowService(null, null, null, null);
        CronService cronService = new CronService(
            new FileCronStore(tempDir.resolve("jobs.json")),
            Clock.fixed(Instant.parse("2026-02-21T08:00:00Z"), ZoneOffset.UTC)
        );

        ToolContext context = new ToolContext(
            tempDir,
            Map.of(
                "workflowService", service,
                "cronService", cronService
            )
        );

        String output = tool.execute(
            Map.of(
                "action", "goal_plan",
                "goal", "Launch Cognis beta",
                "schedule_daily", true
            ),
            context
        );

        assertThat(output).contains("Goal Execution Loop: Launch Cognis beta");
        assertThat(output).contains("Daily check-in scheduled");
        assertThat(cronService.list()).hasSize(1);
        assertThat(cronService.list().getFirst().message()).isEqualTo("workflow:goal_checkin:Launch Cognis beta");
    }
}
