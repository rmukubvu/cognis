package io.cognis.core.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cognis.core.model.AgentResult;
import io.cognis.core.model.ChatMessage;
import io.cognis.core.model.ToolCall;
import io.cognis.core.provider.LlmProvider;
import io.cognis.core.provider.LlmResponse;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.memory.ExtractedMemory;
import io.cognis.core.memory.HeuristicMemoryExtractor;
import io.cognis.core.memory.MemoryExtractor;
import io.cognis.core.memory.MemoryStore;
import io.cognis.core.observability.ObservabilityService;
import io.cognis.core.profile.ProfileStore;
import io.cognis.core.session.ConversationStore;
import io.cognis.core.session.ConversationTurn;
import io.cognis.core.session.SessionSummaryManager;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.tool.ToolRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9()\\-\\s]{7,}$");
    private static final String EXTERNAL_ACTION_GUARDRAIL = "I could not verify execution of that external action yet. "
        + "I need to run the relevant tool first and confirm its result before I can say it was sent/completed.";
    private static final String IDENTITY_POLICY = """
        ## Identity And Branding Policy
        - You are Cognis.
        - Never claim to be Claude, Anthropic, OpenAI, or any other underlying model/provider.
        - If asked who created or built you, answer: "I am Cognis." and keep the response focused on Cognis capabilities.
        - Do not mention internal provider names, model names, or vendor ownership unless explicitly asked for low-level technical diagnostics.
        """;
    private final ProviderRouter providerRouter;
    private final ToolRegistry toolRegistry;
    private final Map<String, Object> toolServices;
    private final ConversationStore conversationStore;
    private final MemoryExtractor memoryExtractor;

    public AgentOrchestrator(ProviderRouter providerRouter, ToolRegistry toolRegistry) {
        this(providerRouter, toolRegistry, Map.of(), null, new HeuristicMemoryExtractor());
    }

    public AgentOrchestrator(ProviderRouter providerRouter, ToolRegistry toolRegistry, Map<String, Object> toolServices) {
        this(providerRouter, toolRegistry, toolServices, null, new HeuristicMemoryExtractor());
    }

    public AgentOrchestrator(
        ProviderRouter providerRouter,
        ToolRegistry toolRegistry,
        Map<String, Object> toolServices,
        ConversationStore conversationStore
    ) {
        this(providerRouter, toolRegistry, toolServices, conversationStore, new HeuristicMemoryExtractor());
    }

    public AgentOrchestrator(
        ProviderRouter providerRouter,
        ToolRegistry toolRegistry,
        Map<String, Object> toolServices,
        ConversationStore conversationStore,
        MemoryExtractor memoryExtractor
    ) {
        this.providerRouter = providerRouter;
        this.toolRegistry = toolRegistry;
        this.toolServices = toolServices == null ? Map.of() : Map.copyOf(toolServices);
        this.conversationStore = conversationStore;
        this.memoryExtractor = memoryExtractor == null ? new HeuristicMemoryExtractor() : memoryExtractor;
    }

    public AgentResult run(String userPrompt, AgentSettings settings, Path workspace) {
        return run(userPrompt, settings, workspace, Map.of());
    }

    public AgentResult run(String userPrompt, AgentSettings settings, Path workspace, Map<String, Object> runMetadata) {
        List<ChatMessage> transcript = new ArrayList<>();
        transcript.add(ChatMessage.system(buildSystemPrompt(settings.systemPrompt(), userPrompt)));
        transcript.add(ChatMessage.user(userPrompt));
        RunContext runContext = new RunContext(runMetadata);

        LlmProvider provider = providerRouter.resolve(settings.provider(), settings.model());
        LOG.debug("Using provider {} with model {}", provider.name(), settings.model());

        Map<String, Object> usage = Map.of();
        boolean executedTool = false;
        boolean enforcedToolRetry = false;
        for (int i = 0; i < settings.maxToolIterations(); i++) {
            LlmResponse response = provider.chat(settings.model(), transcript, toolDefinitions());
            usage = response.usage();

            if (response.toolCalls().isEmpty()) {
                String content = response.content() == null ? "" : response.content();
                if (shouldEnforceExternalActionToolUse(userPrompt, content, executedTool)) {
                    if (!enforcedToolRetry) {
                        enforcedToolRetry = true;
                        transcript.add(ChatMessage.system(
                            "External actions must be executed via tools before confirmation. "
                                + "If the user asked to send/pay/order/call, call the required tool now."
                        ));
                        continue;
                    }
                    content = EXTERNAL_ACTION_GUARDRAIL;
                }
                transcript.add(ChatMessage.assistant(content));
                AgentResult result = new AgentResult(content, List.copyOf(transcript), usage);
                postProcessTurn(userPrompt, result);
                return result;
            }

            transcript.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            for (ToolCall call : response.toolCalls()) {
                executedTool = true;
                String toolOutput = executeTool(call, workspace, runContext);
                transcript.add(ChatMessage.tool(toolOutput, call.id()));
            }
        }

        String timeoutMessage = "Stopped after max tool iterations";
        transcript.add(ChatMessage.assistant(timeoutMessage));
        AgentResult result = new AgentResult(timeoutMessage, List.copyOf(transcript), usage);
        postProcessTurn(userPrompt, result);
        return result;
    }

    private String executeTool(ToolCall call, Path workspace, RunContext runContext) {
        return toolRegistry.find(call.name())
            .map(tool -> safelyExecute(tool, call.arguments(), workspace, runContext))
            .orElse("Error: Tool '" + call.name() + "' not found");
    }

    private String safelyExecute(Tool tool, Map<String, Object> input, Path workspace, RunContext runContext) {
        long started = System.currentTimeMillis();
        recordToolEvent("tool_started", tool.name(), input, null, started, runContext, null);
        try {
            String output = tool.execute(input, new ToolContext(workspace, toolServices));
            recordToolEvent("tool_succeeded", tool.name(), input, output, started, runContext, null);
            return output;
        } catch (RuntimeException ex) {
            LOG.warn("Tool {} failed", tool.name(), ex);
            String error = ex.getMessage() == null ? "execution_error" : ex.getMessage();
            recordToolEvent("tool_failed", tool.name(), input, null, started, runContext, error);
            return "Error executing tool '" + tool.name() + "': " + ex.getMessage();
        }
    }

    private List<Map<String, Object>> toolDefinitions() {
        return toolRegistry.all().stream()
            .map(tool -> Map.of(
                "type", "function",
                "function", Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", tool.schema())))
            .toList();
    }

    private void persistTurn(String prompt, AgentResult result) {
        if (conversationStore == null) {
            return;
        }
        try {
            conversationStore.append(new ConversationTurn(
                Instant.now(),
                prompt,
                result.content(),
                result.transcript()
            ));
        } catch (IOException e) {
            LOG.warn("Failed to persist conversation turn", e);
        }
    }

    private void postProcessTurn(String userPrompt, AgentResult result) {
        persistTurn(userPrompt, result);
        extractAndStoreMemories(userPrompt, result.content());
        updateSessionSummary(userPrompt, result.content());
    }

    private void extractAndStoreMemories(String userPrompt, String assistantResponse) {
        MemoryStore memoryStore = service("memoryStore", MemoryStore.class);
        if (memoryStore == null) {
            return;
        }
        try {
            List<ExtractedMemory> extracted = memoryExtractor.extract(userPrompt, assistantResponse);
            for (ExtractedMemory memory : extracted) {
                memoryStore.remember(memory.content(), "agent_loop", memory.tags());
            }
        } catch (Exception e) {
            LOG.debug("Memory extraction skipped: {}", e.getMessage());
        }
    }

    private void updateSessionSummary(String userPrompt, String assistantResponse) {
        SessionSummaryManager manager = service("sessionSummaryManager", SessionSummaryManager.class);
        if (manager == null) {
            return;
        }
        try {
            manager.recordTurn(userPrompt, assistantResponse);
        } catch (IOException e) {
            LOG.debug("Session summary update skipped: {}", e.getMessage());
        }
    }

    private String buildSystemPrompt(String basePrompt, String userPrompt) {
        StringBuilder prompt = new StringBuilder(basePrompt == null ? "" : basePrompt);
        prompt.append("\n\n").append(IDENTITY_POLICY);

        ProfileStore profileStore = service("profileStore", ProfileStore.class);
        if (profileStore != null) {
            try {
                String profile = profileStore.formatForPrompt();
                if (!profile.isBlank()) {
                    prompt.append("\n\n").append(profile);
                }
            } catch (IOException e) {
                LOG.debug("Profile prompt injection skipped: {}", e.getMessage());
            }
        }

        MemoryStore memoryStore = service("memoryStore", MemoryStore.class);
        if (memoryStore != null && userPrompt != null && !userPrompt.isBlank()) {
            try {
                var recalled = memoryStore.recall(userPrompt, 8);
                if (!recalled.isEmpty()) {
                    prompt.append("\n\n## Recalled Memories (relevant)\n\n");
                    recalled.forEach(entry -> prompt.append("- ").append(entry.content()).append("\n"));
                }
            } catch (IOException e) {
                LOG.debug("Memory prompt injection skipped: {}", e.getMessage());
            }
        }

        SessionSummaryManager summaryManager = service("sessionSummaryManager", SessionSummaryManager.class);
        if (summaryManager != null) {
            try {
                String summary = summaryManager.currentSummary();
                if (!summary.isBlank()) {
                    prompt.append("\n\n## Session Summary\n\n").append(summary);
                }
            } catch (IOException e) {
                LOG.debug("Session summary injection skipped: {}", e.getMessage());
            }
        }

        return prompt.toString();
    }

    private <T> T service(String key, Class<T> type) {
        Object service = toolServices.get(key);
        if (service == null || !type.isInstance(service)) {
            return null;
        }
        return type.cast(service);
    }

    private boolean shouldEnforceExternalActionToolUse(String userPrompt, String assistantContent, boolean executedTool) {
        if (executedTool) {
            return false;
        }
        String prompt = normalize(userPrompt);
        String response = normalize(assistantContent);
        if (prompt.isBlank() || response.isBlank()) {
            return false;
        }

        boolean intent = containsAny(prompt,
            "send a text", "text my", "sms", "send message", "message my",
            "call ", "pay ", "purchase ", "buy ", "order ", "book ", "request ride", "uber", "lyft", "twilio"
        );
        boolean claimed = containsAny(response,
            "text sent", "message sent", "sent to", "done", "i sent", "i've sent", "completed", "payment sent", "order placed"
        );
        return intent && claimed;
    }

    private void recordToolEvent(
        String type,
        String toolName,
        Map<String, Object> input,
        String output,
        long startedAtMillis,
        RunContext runContext,
        String error
    ) {
        ObservabilityService observability = service("observabilityService", ObservabilityService.class);
        if (observability == null) {
            return;
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        runContext.copyBaseAttributes(attrs);
        attrs.put("tool_name", toolName);
        attrs.put("duration_ms", Math.max(0, System.currentTimeMillis() - startedAtMillis));
        attrs.put("input_chars", String.valueOf(input == null ? Map.of() : input).length());
        if (error != null && !error.isBlank()) {
            attrs.put("error", error);
        }
        if (output != null) {
            attrs.put("output_chars", output.length());
            if (type.equals("tool_succeeded")) {
                attrs.putAll(extractToolMetadata(toolName, input, output));
            }
        }

        try {
            observability.record(type, attrs);
        } catch (IOException e) {
            LOG.debug("Failed to record tool audit event: {}", e.getMessage());
        }
    }

    private Map<String, Object> extractToolMetadata(String toolName, Map<String, Object> input, String output) {
        if (!"mcp".equals(toolName)) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = JSON.readValue(output, MAP_TYPE);
            Object status = parsed.get("http_status");
            Object ok = parsed.get("http_ok");
            if (status != null) {
                metadata.put("mcp_http_status", status);
            }
            if (ok != null) {
                metadata.put("mcp_http_ok", ok);
            }
            String mcpTool = String.valueOf(input == null ? "" : input.getOrDefault("tool", "")).trim();
            if (!mcpTool.isBlank()) {
                metadata.put("mcp_tool", mcpTool);
            }
            Object data = parsed.get("data");
            if (data instanceof Map<?, ?> map) {
                Object sid = map.get("sid");
                Object deliveryStatus = map.get("status");
                Object to = map.get("to");
                if (sid != null) {
                    metadata.put("provider_sid", String.valueOf(sid));
                }
                if (deliveryStatus != null) {
                    metadata.put("provider_status", String.valueOf(deliveryStatus));
                }
                if (to != null) {
                    metadata.put("provider_to", maskPhone(String.valueOf(to)));
                }
            }
        } catch (Exception ignored) {
            // Ignore non-JSON tool output.
        }
        return metadata;
    }

    private String maskPhone(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) {
            return raw;
        }
        if (!PHONE_PATTERN.matcher(raw).matches()) {
            return raw;
        }
        String digitsOnly = raw.replaceAll("[^0-9]", "");
        if (digitsOnly.length() <= 4) {
            return "***" + digitsOnly;
        }
        return "***" + digitsOnly.substring(digitsOnly.length() - 4);
    }

    private boolean containsAny(String source, String... tokens) {
        for (String token : tokens) {
            if (source.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase().trim();
    }

    private static final class RunContext {
        private final String clientId;
        private final String taskId;

        private RunContext(Map<String, Object> metadata) {
            this.clientId = string(metadata.get("client_id"));
            this.taskId = string(metadata.get("task_id"));
        }

        private void copyBaseAttributes(Map<String, Object> target) {
            if (!clientId.isBlank()) {
                target.put("client_id", clientId);
            }
            if (!taskId.isBlank()) {
                target.put("task_id", taskId);
            }
        }

        private static String string(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }
    }
}
