package io.cognis.core.tool.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.cron.CronService;
import io.cognis.core.cron.FileCronStore;
import io.cognis.core.tool.ToolContext;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CronToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAddAndListJobs() throws Exception {
        CronService service = new CronService(new FileCronStore(tempDir.resolve("cron.json")), Clock.systemUTC());
        CronTool tool = new CronTool();

        ToolContext context = new ToolContext(tempDir, Map.of("cronService", service));

        String add = tool.execute(Map.of(
            "action", "add_every",
            "name", "digest",
            "message", "run digest",
            "everySeconds", 60
        ), context);

        String list = tool.execute(Map.of("action", "list"), context);

        assertThat(add).contains("Created cron job");
        assertThat(list).contains("digest").contains("every 60s");
    }

    @Test
    void shouldAddNaturalOneShotJob() throws Exception {
        CronService service = new CronService(new FileCronStore(tempDir.resolve("cron-natural.json")), Clock.systemUTC());
        CronTool tool = new CronTool();
        ToolContext context = new ToolContext(tempDir, Map.of("cronService", service));

        String add = tool.execute(Map.of(
            "action", "add_natural",
            "name", "reminder",
            "message", "standup",
            "when", "in 10m"
        ), context);

        String list = tool.execute(Map.of("action", "list"), context);

        assertThat(add).contains("Created natural-language job");
        assertThat(list).contains("reminder").contains("once at");
    }
}
