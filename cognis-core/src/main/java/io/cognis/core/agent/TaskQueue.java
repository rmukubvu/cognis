package io.cognis.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.model.AgentResult;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.tool.impl.AgentTool;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAG-based task executor that drives agent spawning via the existing {@link AgentTool} machinery.
 * <p>
 * Given a list of {@link Task}s with declared {@code dependsOn} edges, {@code TaskQueue}:
 * <ol>
 *   <li>Validates the graph for cycles using Kahn's BFS algorithm</li>
 *   <li>Spawns all tasks with no unresolved dependencies immediately (in parallel)</li>
 *   <li>Chains dependents via {@link CompletableFuture#allOf} + {@code thenCompose}, so
 *       they start the moment their dependencies complete — no polling loop needed</li>
 * </ol>
 *
 * <pre>{@code
 * // Example: fetch A and B in parallel, then C only after both succeed
 * List<Task> tasks = List.of(
 *     new Task("fetch-shipments",  "List all overdue shipments",     "data-fetcher"),
 *     new Task("fetch-weather",    "Get eastern Africa 7-day forecast", "data-fetcher"),
 *     new Task("compose-brief",    "Write the morning operations brief", "writer",
 *              List.of("fetch-shipments", "fetch-weather"), null, null)
 * );
 * Map<String, CompletableFuture<AgentResult>> futures = taskQueue.submit(tasks);
 * String brief = futures.get("compose-brief").get().content();
 * }</pre>
 */
public final class TaskQueue {
    private static final Logger LOG = LoggerFactory.getLogger(TaskQueue.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentTool agentTool;
    private final ToolContext context;

    public TaskQueue(AgentTool agentTool, ToolContext context) {
        this.agentTool = agentTool;
        this.context = context;
    }

    /**
     * Validate and submit a task graph.
     *
     * @return map of task ID → {@link CompletableFuture} that resolves when the task completes
     * @throws CycleException if the dependency graph contains a cycle
     */
    public Map<String, CompletableFuture<AgentResult>> submit(List<Task> tasks) throws CycleException {
        validateIds(tasks);
        validateNoCycles(tasks);

        // Build adjacency: task id → futures of its dependencies
        Map<String, CompletableFuture<AgentResult>> futures = new HashMap<>();

        // Process in topological order (guaranteed by validateNoCycles) so that when we
        // look up dep futures they already exist in the map
        List<Task> ordered = topologicalSort(tasks);

        for (Task task : ordered) {
            if (task.dependsOn().isEmpty()) {
                futures.put(task.id(), spawnAsync(task));
            } else {
                @SuppressWarnings("unchecked")
                CompletableFuture<AgentResult>[] depFutures = task.dependsOn().stream()
                    .map(dep -> futures.get(dep).exceptionally(ex -> {
                        // Propagate dependency failure gracefully
                        LOG.warn("Dependency '{}' of task '{}' failed: {}", dep, task.id(), ex.getMessage());
                        return AgentResult.maxIterations(
                            "Dependency '" + dep + "' failed: " + ex.getMessage(), List.of(), Map.of());
                    }))
                    .toArray(CompletableFuture[]::new);

                CompletableFuture<Void> allDone = CompletableFuture.allOf(depFutures);
                futures.put(task.id(), allDone.thenCompose(__ -> spawnAsync(task)));
            }
        }
        return futures;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private CompletableFuture<AgentResult> spawnAsync(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> spawnInput = new HashMap<>();
                spawnInput.put("action", "spawn");
                spawnInput.put("task", task.prompt());
                spawnInput.put("role", task.role() != null ? task.role() : "assistant");
                if (task.toolAllowlist() != null) spawnInput.put("tools", task.toolAllowlist());
                if (task.model() != null) spawnInput.put("model", task.model());

                String spawnResult = agentTool.execute(spawnInput, context);
                Map<?, ?> parsed = JSON.readValue(spawnResult, Map.class);
                String runId = String.valueOf(parsed.get("runId"));

                if (runId.isBlank() || "null".equals(runId)) {
                    return AgentResult.maxIterations(
                        "Spawn failed for task '" + task.id() + "': " + spawnResult, List.of(), Map.of());
                }

                // Await the spawned run
                Map<String, Object> awaitInput = Map.of("action", "await", "runId", runId);
                String awaitResult = agentTool.execute(awaitInput, context);
                @SuppressWarnings("unchecked")
                Map<String, Object> awaitParsed = JSON.readValue(awaitResult, Map.class);
                String output = String.valueOf(awaitParsed.getOrDefault("output", awaitResult));
                return new AgentResult(output, List.of(), Map.of());

            } catch (Exception e) {
                LOG.error("Task '{}' failed during execution", task.id(), e);
                return AgentResult.maxIterations(
                    "Task '" + task.id() + "' error: " + e.getMessage(), List.of(), Map.of());
            }
        });
    }

    /** Kahn's BFS topological sort — also validates for cycles. */
    private List<Task> topologicalSort(List<Task> tasks) throws CycleException {
        Map<String, Task> byId = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (Task t : tasks) {
            byId.put(t.id(), t);
            inDegree.put(t.id(), 0);
            dependents.put(t.id(), new ArrayList<>());
        }
        for (Task t : tasks) {
            for (String dep : t.dependsOn()) {
                inDegree.merge(t.id(), 1, Integer::sum);
                dependents.get(dep).add(t.id());
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<Task> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(byId.get(id));
            for (String dep : dependents.get(id)) {
                inDegree.merge(dep, -1, Integer::sum);
                if (inDegree.get(dep) == 0) queue.add(dep);
            }
        }

        if (sorted.size() != tasks.size()) {
            throw new CycleException("Task graph contains a cycle");
        }
        return sorted;
    }

    private void validateIds(List<Task> tasks) {
        Map<String, Task> byId = new HashMap<>();
        for (Task t : tasks) {
            if (t.id() == null || t.id().isBlank())
                throw new IllegalArgumentException("All tasks must have a non-blank id");
            if (byId.put(t.id(), t) != null)
                throw new IllegalArgumentException("Duplicate task id: " + t.id());
        }
        for (Task t : tasks) {
            for (String dep : t.dependsOn()) {
                if (!byId.containsKey(dep))
                    throw new IllegalArgumentException(
                        "Task '" + t.id() + "' depends on unknown task '" + dep + "'");
            }
        }
    }

    private void validateNoCycles(List<Task> tasks) throws CycleException {
        topologicalSort(tasks); // throws CycleException if cycle detected
    }

    /** Thrown when the submitted task graph contains a dependency cycle. */
    public static final class CycleException extends Exception {
        public CycleException(String message) {
            super(message);
        }
    }
}
