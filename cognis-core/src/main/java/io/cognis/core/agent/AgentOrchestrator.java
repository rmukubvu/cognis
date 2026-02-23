package io.cognis.core.agent;

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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(AgentOrchestrator.class);
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
        List<ChatMessage> transcript = new ArrayList<>();
        transcript.add(ChatMessage.system(buildSystemPrompt(settings.systemPrompt(), userPrompt)));
        transcript.add(ChatMessage.user(userPrompt));

        LlmProvider provider = providerRouter.resolve(settings.provider(), settings.model());
        LOG.debug("Using provider {} with model {}", provider.name(), settings.model());

        Map<String, Object> usage = Map.of();
        for (int i = 0; i < settings.maxToolIterations(); i++) {
            LlmResponse response = provider.chat(settings.model(), transcript, toolDefinitions());
            usage = response.usage();

            if (response.toolCalls().isEmpty()) {
                String content = response.content() == null ? "" : response.content();
                transcript.add(ChatMessage.assistant(content));
                AgentResult result = new AgentResult(content, List.copyOf(transcript), usage);
                postProcessTurn(userPrompt, result);
                return result;
            }

            transcript.add(ChatMessage.assistantWithToolCalls(response.content(), response.toolCalls()));
            for (ToolCall call : response.toolCalls()) {
                String toolOutput = executeTool(call, workspace);
                transcript.add(ChatMessage.tool(toolOutput, call.id()));
            }
        }

        String timeoutMessage = "Stopped after max tool iterations";
        transcript.add(ChatMessage.assistant(timeoutMessage));
        AgentResult result = new AgentResult(timeoutMessage, List.copyOf(transcript), usage);
        postProcessTurn(userPrompt, result);
        return result;
    }

    private String executeTool(ToolCall call, Path workspace) {
        return toolRegistry.find(call.name())
            .map(tool -> safelyExecute(tool, call.arguments(), workspace))
            .orElse("Error: Tool '" + call.name() + "' not found");
    }

    private String safelyExecute(Tool tool, Map<String, Object> input, Path workspace) {
        try {
            return tool.execute(input, new ToolContext(workspace, toolServices));
        } catch (RuntimeException ex) {
            LOG.warn("Tool {} failed", tool.name(), ex);
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
}
