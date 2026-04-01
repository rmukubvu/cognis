package io.cognis.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.agent.AgentOrchestrator;
import io.cognis.core.agent.AgentSettings;
import io.cognis.core.agent.AgentStore;
import io.cognis.core.agent.DynamicAgent;
import io.cognis.core.agent.SubagentRegistry;
import io.cognis.core.agent.SubagentRun;
import io.cognis.core.agent.SubagentRunHandle;
import io.cognis.core.agent.TraceContext;
import io.cognis.core.model.AgentResult;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.provider.LlmProvider;
import io.cognis.core.provider.LlmResponse;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.session.ConversationTurn;
import io.cognis.core.session.FileConversationStore;
import io.cognis.core.session.NoOpConversationStore;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.tool.ToolRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.cognis.core.agent.AgentPool;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentTool implements Tool {
    private static final Logger LOG = LoggerFactory.getLogger(AgentTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DEPTH = 2;
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final ToolRegistry parentRegistry;
    private final AgentStore agentStore;
    private final ProviderRouter providerRouter;
    private final AgentSettings defaultSettings;
    private final SubagentRegistry subagentRegistry;
    private final AgentPool agentPool;

    public AgentTool(
        ToolRegistry parentRegistry,
        AgentStore agentStore,
        ProviderRouter providerRouter,
        AgentSettings defaultSettings,
        SubagentRegistry subagentRegistry,
        AgentPool agentPool
    ) {
        this.parentRegistry = parentRegistry;
        this.agentStore = agentStore;
        this.providerRouter = providerRouter;
        this.defaultSettings = defaultSettings;
        this.subagentRegistry = subagentRegistry;
        this.agentPool = agentPool;
    }

    @Override
    public String name() {
        return "agent";
    }

    @Override
    public String description() {
        return "Spawn async subagents (spawn/await/await_all/steer/kill/status) or manage persistent named agents (create/chat/list)";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
            "type", "string",
            "enum", List.of("spawn", "await", "await_all", "steer", "kill", "status", "create", "chat", "list"),
            "description", "spawn=async one-shot (returns runId immediately); "
                + "await=block on one runId; await_all=block on many runIds in parallel; "
                + "steer=redirect a running subagent; kill=stop+cascade; status=non-blocking check; "
                + "create=make persistent named agent; chat=message named agent; list=show agents"));
        properties.put("task", Map.of("type", "string", "description", "For spawn/chat/steer: the task or message"));
        properties.put("role", Map.of("type", "string", "description", "For spawn: role label, e.g. 'researcher', 'triage'"));
        properties.put("runId", Map.of("type", "string", "description", "For await/steer/kill/status: the runId returned by spawn"));
        properties.put("runIds", Map.of("type", "array", "items", Map.of("type", "string"), "description", "For await_all: list of runIds to wait on"));
        properties.put("name", Map.of("type", "string", "description", "For create/chat: unique agent name"));
        properties.put("description", Map.of("type", "string", "description", "For create: natural-language description of the agent's role"));
        properties.put("model", Map.of("type", "string", "description", "Optional model override"));
        properties.put("tools", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Optional tool allowlist for spawn/create"));
        properties.put("context", Map.of("type", "object", "description", "Optional key-value context map injected into the subagent prompt"));
        properties.put("timeoutSeconds", Map.of("type", "integer", "description", "Await timeout in seconds (default 300)"));
        return Map.of("type", "object", "properties", properties, "required", new String[]{"action"});
    }

    @Override
    public String execute(Map<String, Object> input, ToolContext context) {
        String action = str(input.get("action"));
        return switch (action) {
            case "spawn"     -> handleSpawn(input, context);
            case "await"     -> handleAwait(input);
            case "await_all" -> handleAwaitAll(input);
            case "steer"     -> handleSteer(input, context);
            case "kill"      -> handleKill(input);
            case "status"    -> handleStatus(input);
            case "create"    -> handleCreate(input, context);
            case "chat"      -> handleChat(input, context);
            case "list"      -> handleList();
            default -> "Error: unsupported action '" + action
                + "'. Valid: spawn, await, await_all, steer, kill, status, create, chat, list";
        };
    }

    // -------------------------------------------------------------------------
    // Async spawn lifecycle
    // -------------------------------------------------------------------------

    private String handleSpawn(Map<String, Object> input, ToolContext context) {
        int depth = (int) context.services().getOrDefault("agentDepth", 0);
        if (depth >= MAX_DEPTH) {
            return toJson(Map.of("error", "max subagent depth (" + MAX_DEPTH + ") reached"));
        }
        String task = str(input.get("task"));
        if (task.isBlank()) return toJson(Map.of("error", "task is required for spawn"));

        String role = str(input.get("role"));
        if (role.isBlank()) role = "assistant";

        @SuppressWarnings("unchecked")
        List<String> tools = input.get("tools") instanceof List<?> l ? (List<String>) l : null;
        String model = str(input.get("model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inputContext = input.get("context") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();

        String runId = UUID.randomUUID().toString();
        String parentRunId = str(context.services().getOrDefault("currentRunId", ""));
        String resolvedModel = model.isBlank() ? defaultSettings.model() : model;

        // Create a child trace span so the full spawn chain is observable
        Object tc = context.services().get("traceContext");
        TraceContext childTrace = (tc instanceof TraceContext t ? t : TraceContext.root()).child();

        try {
            subagentRegistry.register(
                runId, parentRunId.isBlank() ? null : parentRunId,
                task, role, resolvedModel,
                childTrace.traceId(), childTrace.spanId()
            );
        } catch (IOException e) {
            return toJson(Map.of("error", "failed to register run: " + e.getMessage()));
        }

        AgentSettings settings = new AgentSettings(
            buildSpawnSystemPrompt(role, inputContext),
            defaultSettings.provider(), resolvedModel, defaultSettings.maxToolIterations()
        );
        submitChildRun(runId, task, settings, buildChildOrchestrator(tools, context, depth + 1, runId, childTrace), context);

        return toJson(Map.of("runId", runId, "status", "CREATED", "role", role));
    }

    private String handleAwait(Map<String, Object> input) {
        String runId = str(input.get("runId"));
        if (runId.isBlank()) return toJson(Map.of("error", "runId is required for await"));
        SubagentRunHandle handle = subagentRegistry.getHandle(runId);
        if (handle == null) return toJson(Map.of("error", "run not found: " + runId));
        return toJson(collectOne(runId, handle, resolveTimeout(input)));
    }

    private String handleAwaitAll(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        List<String> runIds = input.get("runIds") instanceof List<?> l ? (List<String>) l : List.of();
        if (runIds.isEmpty()) return toJson(Map.of("error", "runIds is required for await_all"));
        int timeout = resolveTimeout(input);

        Map<String, Object> results = new LinkedHashMap<>();
        for (String runId : runIds) {
            SubagentRunHandle handle = subagentRegistry.getHandle(runId);
            results.put(runId, handle == null
                ? Map.of("status", "NOT_FOUND")
                : collectOne(runId, handle, timeout));
        }
        return toJson(Map.of("results", results));
    }

    private String handleSteer(Map<String, Object> input, ToolContext context) {
        String runId = str(input.get("runId"));
        String newTask = str(input.get("task"));
        if (runId.isBlank() || newTask.isBlank()) {
            return toJson(Map.of("error", "runId and task are required for steer"));
        }

        SubagentRunHandle oldHandle = subagentRegistry.getHandle(runId);
        if (oldHandle == null) return toJson(Map.of("error", "run not found: " + runId));

        // Cancel the old run before re-submitting
        if (!oldHandle.future.isDone()) {
            oldHandle.future.cancel(true);
            if (oldHandle.thread != null) oldHandle.thread.interrupt();
        }

        // Use the in-memory snapshot to preserve parentRunId, role, model, and trace lineage
        SubagentRun existing = oldHandle.snapshot;
        // Steer creates a new span under the same trace so re-execution is traceable
        TraceContext steeredTrace = new TraceContext(
            existing.traceId() != null ? existing.traceId() : TraceContext.root().traceId(),
            java.util.UUID.randomUUID().toString(),
            existing.spanId()
        );
        try {
            subagentRegistry.reregister(
                runId, existing.parentRunId(), newTask, existing.role(), existing.model(),
                steeredTrace.traceId(), steeredTrace.spanId()
            );
        } catch (IOException e) {
            return toJson(Map.of("error", "failed to reregister run: " + e.getMessage()));
        }

        int depth = (int) context.services().getOrDefault("agentDepth", 0);
        AgentSettings settings = new AgentSettings(
            buildSpawnSystemPrompt(existing.role(), Map.of()),
            defaultSettings.provider(), existing.model(), defaultSettings.maxToolIterations()
        );
        submitChildRun(runId, newTask, settings, buildChildOrchestrator(null, context, depth + 1, runId, steeredTrace), context);

        return toJson(Map.of("runId", runId, "status", "CREATED", "steered", true));
    }

    private String handleKill(Map<String, Object> input) {
        String runId = str(input.get("runId"));
        if (runId.isBlank()) return toJson(Map.of("error", "runId is required for kill"));
        return toJson(Map.of("runId", runId, "killed", cascadeKill(runId)));
    }

    private String handleStatus(Map<String, Object> input) {
        String runId = str(input.get("runId"));
        if (runId.isBlank()) return toJson(Map.of("error", "runId is required for status"));
        return subagentRegistry.find(runId)
            .map(run -> toJson(Map.of(
                "runId",     run.runId(),
                "status",    run.status().name(),
                "role",      run.role() == null ? "" : run.role(),
                "task",      run.task() == null ? "" : run.task(),
                "model",     run.model() == null ? "" : run.model(),
                "createdAt", ts(run.createdAt()),
                "startedAt", ts(run.startedAt()),
                "endedAt",   ts(run.endedAt())
            )))
            .orElse(toJson(Map.of("error", "run not found: " + runId)));
    }

    // -------------------------------------------------------------------------
    // Persistent named agents
    // -------------------------------------------------------------------------

    private String handleCreate(Map<String, Object> input, ToolContext context) {
        String name = str(input.get("name"));
        if (name.isBlank()) return "Error: name is required for create";
        if (!name.matches("[a-z0-9_-]+"))
            return "Error: name must be lowercase alphanumeric with hyphens/underscores only";

        String description = str(input.get("description"));
        if (description.isBlank()) return "Error: description is required for create";

        try {
            if (agentStore.find(name).isPresent())
                return "Error: agent '" + name + "' already exists.";
        } catch (IOException e) {
            LOG.warn("Failed to check agent store for '{}'", name, e);
        }

        @SuppressWarnings("unchecked")
        List<String> tools = input.get("tools") instanceof List<?> l ? (List<String>) l : null;
        String model = str(input.get("model"));
        String resolvedModel = model.isBlank() ? defaultSettings.model() : model;

        DynamicAgent agent = new DynamicAgent(
            name, description, generateSystemPrompt(description, resolvedModel),
            resolvedModel, tools, defaultSettings.maxToolIterations(), Instant.now()
        );
        try {
            agentStore.save(agent);
        } catch (IOException e) {
            return "Error: failed to save agent '" + name + "': " + e.getMessage();
        }
        return "Agent '" + name + "' created. Use action=chat,name=" + name + " to start a conversation.";
    }

    private String handleChat(Map<String, Object> input, ToolContext context) {
        int depth = (int) context.services().getOrDefault("agentDepth", 0);
        if (depth >= MAX_DEPTH) return "Error: max subagent depth reached";

        String name = str(input.get("name"));
        if (name.isBlank()) return "Error: name is required for chat";
        String task = str(input.get("task"));
        if (task.isBlank()) return "Error: task is required for chat";

        DynamicAgent agent;
        try {
            agent = agentStore.find(name).orElse(null);
        } catch (IOException e) {
            return "Error: failed to load agent '" + name + "': " + e.getMessage();
        }
        if (agent == null)
            return "Error: agent '" + name + "' not found. Use action=list to see available agents.";

        FileConversationStore agentConvStore = new FileConversationStore(
            context.workspace().resolve("agent-conversations").resolve(name).resolve("history.json"));
        List<ChatMessage> priorTurns = loadPriorTurns(agentConvStore);

        List<String> toolAllowlist = agent.allowedTools() == null ? List.of() : agent.allowedTools();
        String resolvedModel = agent.model() != null && !agent.model().isBlank()
            ? agent.model() : defaultSettings.model();
        int resolvedIterations = agent.maxToolIterations() > 0
            ? agent.maxToolIterations() : defaultSettings.maxToolIterations();

        Object tc = context.services().get("traceContext");
        TraceContext chatTrace = (tc instanceof TraceContext t ? t : TraceContext.root()).child();
        AgentOrchestrator child = new AgentOrchestrator(
            providerRouter,
            buildSubRegistry(toolAllowlist.isEmpty() ? null : toolAllowlist, true),
            buildChildServices(context, depth + 1, null, chatTrace),
            agentConvStore
        );
        AgentResult result = child.run(
            task,
            new AgentSettings(agent.systemPrompt(), defaultSettings.provider(), resolvedModel, resolvedIterations),
            context.workspace(),
            priorTurns,
            Map.of("subagent_name", name)
        );
        return result.content();
    }

    private String handleList() {
        try {
            List<DynamicAgent> agents = agentStore.list();
            if (agents.isEmpty()) return "No agents created yet. Use action=create to create one.";
            StringBuilder sb = new StringBuilder("Available agents:\n");
            for (DynamicAgent a : agents) {
                sb.append("- ").append(a.name());
                if (a.description() != null && !a.description().isBlank())
                    sb.append(": ").append(a.description());
                if (a.model() != null && !a.model().isBlank())
                    sb.append(" [").append(a.model()).append("]");
                sb.append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "Error: failed to list agents: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Submits a child run to the pool and registers its handle. Shared by spawn and steer. */
    private void submitChildRun(
        String runId, String task, AgentSettings settings,
        AgentOrchestrator child, ToolContext context
    ) {
        Future<AgentResult> future = agentPool.submit(() -> {
            subagentRegistry.markStarted(runId, Thread.currentThread());
            try {
                AgentResult result = child.run(task, settings, context.workspace());
                subagentRegistry.markDone(runId, result.content());
                return result;
            } catch (Exception e) {
                subagentRegistry.markFailed(runId, e.getMessage());
                throw e;
            }
        });
        subagentRegistry.registerHandle(runId, future);
    }

    private int cascadeKill(String runId) {
        int count = 0;
        try {
            for (SubagentRun child : subagentRegistry.listByParent(runId)) {
                count += cascadeKill(child.runId());
            }
            subagentRegistry.markKilled(runId);
            count++;
        } catch (IOException e) {
            LOG.warn("Cascade kill failed for runId {}: {}", runId, e.getMessage());
        }
        return count;
    }

    private Map<String, Object> collectOne(String runId, SubagentRunHandle handle, int timeoutSec) {
        try {
            AgentResult r = handle.future.get(timeoutSec, TimeUnit.SECONDS);
            return Map.of("status", "DONE", "output", r.content());
        } catch (TimeoutException e) {
            return Map.of("status", "TIMEOUT", "runId", runId);
        } catch (CancellationException e) {
            return Map.of("status", "KILLED", "runId", runId);
        } catch (ExecutionException e) {
            String msg = e.getCause() == null ? "unknown" : e.getCause().getMessage();
            return Map.of("status", "FAILED", "error", msg == null ? "unknown" : msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("status", "INTERRUPTED", "runId", runId);
        }
    }

    private AgentOrchestrator buildChildOrchestrator(
        List<String> tools, ToolContext context, int childDepth, String runId, TraceContext childTrace
    ) {
        // excludeAgent=false so children can spawn grandchildren (depth guard enforces MAX_DEPTH)
        return new AgentOrchestrator(
            providerRouter,
            buildSubRegistry(tools, false),
            buildChildServices(context, childDepth, runId, childTrace),
            NoOpConversationStore.INSTANCE
        );
    }

    private Map<String, Object> buildChildServices(ToolContext context, int childDepth, String runId, TraceContext childTrace) {
        Map<String, Object> child = new HashMap<>(context.services());
        child.put("agentDepth", childDepth);
        if (runId != null) child.put("currentRunId", runId);
        if (childTrace != null) child.put("traceContext", childTrace);
        return child;
    }

    private ToolRegistry buildSubRegistry(List<String> allowlist, boolean excludeAgent) {
        Set<String> allowed = allowlist != null && !allowlist.isEmpty() ? new HashSet<>(allowlist) : null;
        if (allowed == null && !excludeAgent) return parentRegistry;
        ToolRegistry sub = new ToolRegistry();
        parentRegistry.all().forEach(tool -> {
            if (excludeAgent && "agent".equals(tool.name())) return;
            if (allowed == null || allowed.contains(tool.name())) sub.register(tool);
        });
        return sub;
    }

    private List<ChatMessage> loadPriorTurns(FileConversationStore store) {
        List<ChatMessage> turns = new ArrayList<>();
        try {
            List<ConversationTurn> history = store.list();
            List<ConversationTurn> recent = history.size() > 10
                ? history.subList(history.size() - 10, history.size()) : history;
            for (ConversationTurn turn : recent) {
                turns.add(ChatMessage.user(turn.prompt()));
                turns.add(ChatMessage.assistant(turn.response()));
            }
        } catch (IOException e) {
            LOG.debug("Could not load agent conversation history: {}", e.getMessage());
        }
        return turns;
    }

    private String generateSystemPrompt(String description, String model) {
        String metaPrompt = "Write a concise, focused system prompt for an AI agent with the following role:\n\n"
            + description + "\n\nKeep it under 200 words.";
        try {
            LlmProvider provider = providerRouter.resolve(defaultSettings.provider(), model);
            LlmResponse response = provider.chat(model, List.of(ChatMessage.user(metaPrompt)), List.of());
            String generated = response.content();
            if (generated != null && !generated.isBlank()) return generated.trim();
        } catch (Exception e) {
            LOG.warn("System prompt generation failed, using template: {}", e.getMessage());
        }
        return "You are a specialised AI agent: " + description;
    }

    private String buildSpawnSystemPrompt(String role, Map<String, Object> inputContext) {
        StringBuilder sb = new StringBuilder("You are a specialised ").append(role).append(" agent.");
        if (!inputContext.isEmpty()) {
            sb.append("\n\nContext:\n");
            inputContext.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        return sb.toString();
    }

    private int resolveTimeout(Map<String, Object> input) {
        return input.get("timeoutSeconds") instanceof Number n ? n.intValue() : DEFAULT_TIMEOUT_SECONDS;
    }

    private String ts(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
