package io.cognis.core.tool.impl;

import io.cognis.core.cron.CronJob;
import io.cognis.core.cron.CronService;
import io.cognis.core.cron.NaturalTimeParser;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CronTool implements Tool {
    private final NaturalTimeParser timeParser = new NaturalTimeParser();

    @Override
    public String name() {
        return "cron";
    }

    @Override
    public String description() {
        return "Manage scheduled jobs (add_every, add_in, add_at, add_natural, list, remove, run_due)";
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = String.valueOf(input.getOrDefault("action", "")).trim();
        CronService cronService = context.service("cronService", CronService.class);
        if (cronService == null) {
            return "Error: cron service is not configured";
        }

        try {
            return switch (action) {
                case "add_every" -> addEvery(cronService, input);
                case "add_in" -> addIn(cronService, input);
                case "add_at" -> addAt(cronService, input);
                case "add_natural" -> addNatural(cronService, input);
                case "list" -> list(cronService);
                case "remove" -> remove(cronService, input);
                case "run_due" -> runDue(cronService);
                default -> "Error: unsupported action: " + action;
            };
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String addEvery(CronService cronService, Map<String, Object> input) throws Exception {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String message = String.valueOf(input.getOrDefault("message", "")).trim();
        int everySeconds = Integer.parseInt(String.valueOf(input.getOrDefault("everySeconds", "0")));

        if (name.isBlank() || message.isBlank() || everySeconds <= 0) {
            return "Error: name, message, and everySeconds (>0) are required";
        }

        CronJob job = cronService.addEvery(name, everySeconds, message);
        return "Created cron job: " + job.id();
    }

    private String addIn(CronService cronService, Map<String, Object> input) throws Exception {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String message = String.valueOf(input.getOrDefault("message", "")).trim();
        int inSeconds = Integer.parseInt(String.valueOf(input.getOrDefault("inSeconds", "0")));

        if (name.isBlank() || message.isBlank() || inSeconds <= 0) {
            return "Error: name, message, and inSeconds (>0) are required";
        }

        CronJob job = cronService.addIn(name, inSeconds, message);
        return "Created one-shot job: " + job.id();
    }

    private String addAt(CronService cronService, Map<String, Object> input) throws Exception {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String message = String.valueOf(input.getOrDefault("message", "")).trim();
        String at = String.valueOf(input.getOrDefault("at", "")).trim();
        if (name.isBlank() || message.isBlank() || at.isBlank()) {
            return "Error: name, message, and at are required";
        }
        long runAt = timeParser.parseToEpochMs(at, Clock.systemUTC(), ZoneId.systemDefault());
        CronJob job = cronService.addAt(name, runAt, message);
        return "Created one-shot job: " + job.id();
    }

    private String addNatural(CronService cronService, Map<String, Object> input) throws Exception {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String message = String.valueOf(input.getOrDefault("message", "")).trim();
        String when = String.valueOf(input.getOrDefault("when", "")).trim();
        if (name.isBlank() || message.isBlank() || when.isBlank()) {
            return "Error: name, message, and when are required";
        }
        long runAt = timeParser.parseToEpochMs(when, Clock.systemUTC(), ZoneId.systemDefault());
        CronJob job = cronService.addAt(name, runAt, message);
        return "Created natural-language job: " + job.id();
    }

    private String list(CronService cronService) throws Exception {
        List<CronJob> jobs = cronService.list();
        if (jobs.isEmpty()) {
            return "No jobs";
        }

        List<String> lines = new ArrayList<>();
        for (CronJob job : jobs) {
            if (job.deleteAfterRun()) {
                lines.add(job.id()
                    + " | " + job.name()
                    + " | once at "
                    + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(job.nextRunAtEpochMs())));
            } else {
                lines.add(job.id() + " | " + job.name() + " | every " + job.everySeconds() + "s");
            }
        }
        return String.join("\n", lines);
    }

    private String remove(CronService cronService, Map<String, Object> input) throws Exception {
        String id = String.valueOf(input.getOrDefault("id", "")).trim();
        if (id.isBlank()) {
            return "Error: id is required";
        }
        return cronService.remove(id) ? "Removed: " + id : "Not found: " + id;
    }

    private String runDue(CronService cronService) throws Exception {
        List<String> executed = new ArrayList<>();
        int count = cronService.runDue(job -> executed.add(job.id() + ": " + job.message()));
        if (count == 0) {
            return "No due jobs";
        }
        return "Executed " + count + " jobs\n" + String.join("\n", executed);
    }
}
