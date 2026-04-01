package io.cognis.core.tool.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.agent.Task;
import io.cognis.core.agent.TaskQueue;
import io.cognis.core.model.AgentResult;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.provider.LlmProvider;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-callable tool that implements the Coordinator pattern.
 * <p>
 * <strong>action=decompose</strong> — send a high-level {@code goal} to a cheap planner model,
 * receive a JSON task graph {@code [{id, prompt, role, dependsOn[]}]}, submit it to
 * {@link TaskQueue}, and return a {@code coordinationId} immediately.
 * <p>
 * <strong>action=result</strong> — await all tasks in a previously submitted coordination
 * and return the merged results map. Blocks until all tasks complete or the timeout elapses.
 * <p>
 * <strong>action=status</strong> — non-blocking check of how many tasks are pending/done.
 * <p>
 * This tool allows a parent agent to delegate an entire multi-step workflow with a single
 * tool call. Example prompt to the parent agent:
 * <pre>
 *   Use coordinator with action=decompose goal="Prepare the morning operations brief"
 *   Then use coordinator with action=result to collect the output
 * </pre>
 */
public final class CoordinatorTool implements Tool {
    private static final Logger LOG = LoggerFactory.getLogger(CoordinatorTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_RESULT_TIMEOUT_SECONDS = 300;

    /** System prompt used to ask the planner model for a JSON task graph. */
    private static final String DECOMPOSE_SYSTEM = """
        You are a task planner. Given a high-level goal, decompose it into a JSON array of tasks.
        Each task must have:
          - "id": short unique identifier (snake_case)
          - "prompt": clear instruction for the agent that will execute this task
          - "role": agent role label (e.g. "researcher", "writer", "analyst")
          - "dependsOn": array of task ids this task must wait for (empty [] if independent)

        Return ONLY the JSON array — no prose, no markdown fences.
        Example:
        [
          {"id":"fetch_data","prompt":"Fetch overdue shipments","role":"data-fetcher","dependsOn":[]},
          {"id":"summarise","prompt":"Write an executive summary","role":"writer","dependsOn":["fetch_data"]}
        ]
        """;

    private final TaskQueue taskQueue;
    private final ProviderRouter providerRouter;
    private final String plannerModel;

    /** Tracks submitted coordinations: coordinationId → futures map. */
    private final ConcurrentHashMap<String, Map<String, CompletableFuture<AgentResult>>> coordinations
        = new ConcurrentHashMap<>();

    public CoordinatorTool(TaskQueue taskQueue, ProviderRouter providerRouter, String plannerModel) {
        this.taskQueue = taskQueue;
        this.providerRouter = providerRouter;
        this.plannerModel = plannerModel != null ? plannerModel : "anthropic/claude-haiku-4-5-20251001";
    }

    @Override
    public String name() {
        return "coordinator";
    }

    @Override
    public String description() {
        return "Coordinator pattern: decompose a high-level goal into a parallel task graph and collect results. "
            + "action=decompose: submit a goal → returns coordinationId. "
            + "action=result: await all tasks → returns merged results. "
            + "action=status: non-blocking progress check.";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("action", Map.of("type", "string",
            "enum", List.of("decompose", "result", "status"),
            "description", "decompose=plan and start; result=await all outputs; status=check progress"));
        properties.put("goal", Map.of("type", "string",
            "description", "For decompose: the high-level goal to plan and execute"));
        properties.put("coordinationId", Map.of("type", "string",
            "description", "For result/status: the id returned by decompose"));
        properties.put("timeoutSeconds", Map.of("type", "integer",
            "description", "For result: max wait time (default 300s)"));
        return Map.of("type", "object", "properties", properties, "required", new String[]{"action"});
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = str(input.get("action"));
        return switch (action) {
            case "decompose" -> handleDecompose(input, context);
            case "result"    -> handleResult(input);
            case "status"    -> handleStatus(input);
            default -> toJson(Map.of("error", "Unknown action '" + action + "'. Use: decompose, result, status"));
        };
    }

    // -------------------------------------------------------------------------

    private String handleDecompose(Map<String, Object> input, ToolContext context) {
        String goal = str(input.get("goal"));
        if (goal.isBlank()) return toJson(Map.of("error", "goal is required for action=decompose"));

        // 1. Ask the planner model to decompose the goal into a task graph
        List<Task> tasks;
        try {
            tasks = decompose(goal);
        } catch (Exception e) {
            return toJson(Map.of("error", "Planning failed: " + e.getMessage()));
        }

        if (tasks.isEmpty()) return toJson(Map.of("error", "Planner returned no tasks"));

        // 2. Submit to TaskQueue
        String coordinationId = UUID.randomUUID().toString();
        Map<String, CompletableFuture<AgentResult>> futures;
        try {
            futures = taskQueue.submit(tasks);
        } catch (TaskQueue.CycleException e) {
            return toJson(Map.of("error", "Task graph has a dependency cycle: " + e.getMessage()));
        }

        coordinations.put(coordinationId, futures);

        List<String> taskIds = tasks.stream().map(Task::id).toList();
        LOG.info("Coordination {} started with {} tasks: {}", coordinationId, tasks.size(), taskIds);
        return toJson(Map.of(
            "coordinationId", coordinationId,
            "taskCount", tasks.size(),
            "tasks", taskIds,
            "status", "STARTED"
        ));
    }

    private String handleResult(Map<String, Object> input) {
        String coordinationId = str(input.get("coordinationId"));
        if (coordinationId.isBlank()) return toJson(Map.of("error", "coordinationId is required"));

        Map<String, CompletableFuture<AgentResult>> futures = coordinations.get(coordinationId);
        if (futures == null) return toJson(Map.of("error", "coordination not found: " + coordinationId));

        int timeout = input.get("timeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_RESULT_TIMEOUT_SECONDS;

        Map<String, Object> results = new HashMap<>();
        for (Map.Entry<String, CompletableFuture<AgentResult>> entry : futures.entrySet()) {
            String taskId = entry.getKey();
            try {
                AgentResult result = entry.getValue().get(timeout, TimeUnit.SECONDS);
                results.put(taskId, Map.of(
                    "status", result.isSuccess() ? "SUCCESS" : result.status().name(),
                    "output", result.content()
                ));
            } catch (TimeoutException e) {
                results.put(taskId, Map.of("status", "TIMEOUT"));
            } catch (ExecutionException e) {
                results.put(taskId, Map.of("status", "FAILED", "error",
                    e.getCause() != null ? e.getCause().getMessage() : "unknown"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.put(taskId, Map.of("status", "INTERRUPTED"));
            }
        }

        // Synthesise a concise summary for the LLM
        StringBuilder summary = new StringBuilder();
        results.forEach((id, v) -> {
            if (v instanceof Map<?, ?> m && "SUCCESS".equals(m.get("status"))) {
                summary.append("### ").append(id).append("\n").append(m.get("output")).append("\n\n");
            }
        });

        return toJson(Map.of(
            "coordinationId", coordinationId,
            "results", results,
            "summary", summary.toString().trim()
        ));
    }

    private String handleStatus(Map<String, Object> input) {
        String coordinationId = str(input.get("coordinationId"));
        if (coordinationId.isBlank()) return toJson(Map.of("error", "coordinationId is required"));

        Map<String, CompletableFuture<AgentResult>> futures = coordinations.get(coordinationId);
        if (futures == null) return toJson(Map.of("error", "coordination not found: " + coordinationId));

        long done = futures.values().stream().filter(CompletableFuture::isDone).count();
        return toJson(Map.of(
            "coordinationId", coordinationId,
            "total", futures.size(),
            "done", done,
            "pending", futures.size() - done,
            "complete", done == futures.size()
        ));
    }

    // -------------------------------------------------------------------------

    private List<Task> decompose(String goal) throws Exception {
        LlmProvider planner = providerRouter.resolve(null, plannerModel);
        var response = planner.chat(plannerModel,
            List.of(ChatMessage.system(DECOMPOSE_SYSTEM), ChatMessage.user(goal)),
            List.of());

        String raw = response.content();
        if (raw == null || raw.isBlank())
            throw new IllegalStateException("Planner returned empty response");

        // Strip any accidental markdown fences
        raw = raw.strip();
        if (raw.startsWith("```")) {
            raw = raw.replaceFirst("```[a-zA-Z]*\\n?", "").replaceFirst("```\\s*$", "").strip();
        }

        List<Map<String, Object>> dtos = JSON.readValue(raw, new TypeReference<>() {});
        List<Task> tasks = new ArrayList<>();
        for (Map<String, Object> dto : dtos) {
            @SuppressWarnings("unchecked")
            List<String> deps = dto.get("dependsOn") instanceof List<?> l ? (List<String>) l : List.of();
            tasks.add(new Task(
                str(dto.get("id")),
                str(dto.get("prompt")),
                str(dto.get("role")),
                deps, null, null
            ));
        }
        return tasks;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { return "{\"error\":\"serialization failed\"}"; }
    }
}
