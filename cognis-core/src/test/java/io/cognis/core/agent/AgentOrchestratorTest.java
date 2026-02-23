package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import io.cognis.core.model.MessageRole;
import io.cognis.core.model.ToolCall;
import io.cognis.core.model.AgentResult;
import io.cognis.core.memory.MemoryEntry;
import io.cognis.core.memory.MemoryStore;
import io.cognis.core.observability.AuditEvent;
import io.cognis.core.observability.AuditStore;
import io.cognis.core.observability.ObservabilityService;
import io.cognis.core.provider.LlmProvider;
import io.cognis.core.provider.LlmResponse;
import io.cognis.core.provider.ProviderRegistry;
import io.cognis.core.provider.ProviderRouter;
import io.cognis.core.session.ConversationStore;
import io.cognis.core.session.SessionSummaryManager;
import io.cognis.core.session.ConversationTurn;
import io.cognis.core.tool.Tool;
import io.cognis.core.tool.ToolContext;
import io.cognis.core.tool.ToolRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {

    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new EchoToolThenAnswerProvider());

        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());

        AgentOrchestrator orchestrator = new AgentOrchestrator(new ProviderRouter(providers), tools);

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 4);
        var result = orchestrator.run("hello", settings, Path.of("."));

        assertThat(result.content()).isEqualTo("final answer");
        assertThat(result.transcript()).extracting(ChatMessage::role)
            .contains(MessageRole.TOOL, MessageRole.ASSISTANT);
    }

    @Test
    void shouldPersistConversationTurnWhenStoreConfigured() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new StubAnswerProvider());
        ToolRegistry tools = new ToolRegistry();
        InMemoryConversationStore store = new InMemoryConversationStore();

        AgentOrchestrator orchestrator = new AgentOrchestrator(
            new ProviderRouter(providers),
            tools,
            Map.of(),
            store
        );

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 2);
        var result = orchestrator.run("hello", settings, Path.of("."));

        assertThat(result.content()).isEqualTo("answer");
        assertThat(store.turns).hasSize(1);
        assertThat(store.turns.getFirst().prompt()).isEqualTo("hello");
        assertThat(store.turns.getFirst().response()).isEqualTo("answer");
    }

    @Test
    void shouldExtractMemoriesAndUpdateSessionSummary() throws Exception {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new StubAnswerProvider());
        ToolRegistry tools = new ToolRegistry();
        InMemoryMemoryStore memoryStore = new InMemoryMemoryStore();
        InMemorySummaryManager summaryManager = new InMemorySummaryManager();

        AgentOrchestrator orchestrator = new AgentOrchestrator(
            new ProviderRouter(providers),
            tools,
            Map.of(
                "memoryStore", memoryStore,
                "sessionSummaryManager", summaryManager
            )
        );

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 2);
        var result = orchestrator.run("My name is Robson and I prefer concise answers.", settings, Path.of("."));

        assertThat(result.content()).isEqualTo("answer");
        assertThat(memoryStore.entries).isNotEmpty();
        assertThat(memoryStore.entries.getFirst().content().toLowerCase()).contains("name");
        assertThat(summaryManager.summary).contains("User:");
    }

    @Test
    void shouldRecordToolEventsWithMcpMetadata() throws Exception {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new McpToolThenAnswerProvider());
        ToolRegistry tools = new ToolRegistry();
        tools.register(new McpEchoTool());
        InMemoryAuditStore auditStore = new InMemoryAuditStore();
        ObservabilityService observability = new ObservabilityService(auditStore, Clock.systemUTC());

        AgentOrchestrator orchestrator = new AgentOrchestrator(
            new ProviderRouter(providers),
            tools,
            Map.of("observabilityService", observability)
        );

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 4);
        orchestrator.run("send a text to +14379615920", settings, Path.of("."), Map.of(
            "client_id", "robson",
            "task_id", "t-1"
        ));

        assertThat(auditStore.events).extracting(AuditEvent::type)
            .contains("tool_started", "tool_succeeded");
        AuditEvent success = auditStore.events.stream()
            .filter(event -> "tool_succeeded".equals(event.type()))
            .findFirst()
            .orElseThrow();
        assertThat(success.attributes()).containsEntry("tool_name", "mcp");
        assertThat(success.attributes()).containsEntry("mcp_tool", "twilio.send_sms");
        assertThat(success.attributes()).containsEntry("provider_sid", "SM123");
        assertThat(success.attributes()).containsEntry("provider_status", "queued");
        assertThat(success.attributes()).containsEntry("client_id", "robson");
        assertThat(success.attributes()).containsEntry("task_id", "t-1");
    }

    @Test
    void shouldReturnConciseSmsConfirmationAfterSuccessfulTwilioCall() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new McpToolThenAnswerProvider());
        ToolRegistry tools = new ToolRegistry();
        tools.register(new McpEchoTool());

        AgentOrchestrator orchestrator = new AgentOrchestrator(new ProviderRouter(providers), tools);

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 4);
        AgentResult result = orchestrator.run("Send a text to alice to buy oranges", settings, Path.of("."));

        assertThat(result.content()).isEqualTo("SMS sent to Alice.");
    }

    @Test
    void shouldBlockUnverifiedExternalActionClaims() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new HallucinatedActionProvider());
        ToolRegistry tools = new ToolRegistry();
        AgentOrchestrator orchestrator = new AgentOrchestrator(new ProviderRouter(providers), tools);

        AgentSettings settings = new AgentSettings("system", "openrouter", "test-model", 3);
        AgentResult result = orchestrator.run("Send a text to my wife", settings, Path.of("."));

        assertThat(result.content()).contains("could not verify execution");
    }

    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Echoes input";
        }

        @Override
        public String execute(Map<String, Object> input, ToolContext context) {
            return String.valueOf(input.getOrDefault("text", ""));
        }
    }

    private static final class EchoToolThenAnswerProvider implements LlmProvider {
        private int calls;

        @Override
        public String name() {
            return "openrouter";
        }

        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            calls++;
            if (calls == 1) {
                return new LlmResponse(
                    "",
                    List.of(new ToolCall("1", "echo", Map.of("text", "tool-output"))),
                    Map.of("total_tokens", 12)
                );
            }
            return new LlmResponse("final answer", List.of(), Map.of("total_tokens", 20));
        }
    }

    private static final class McpToolThenAnswerProvider implements LlmProvider {
        private int calls;

        @Override
        public String name() {
            return "openrouter";
        }

        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            calls++;
            if (calls == 1) {
                return new LlmResponse(
                    "",
                    List.of(new ToolCall("1", "mcp", Map.of(
                        "action", "call_tool",
                        "tool", "twilio.send_sms",
                        "arguments", Map.of("body", Map.of("To", "+14379615920", "Body", "hello"))
                    ))),
                    Map.of("total_tokens", 12)
                );
            }
            return new LlmResponse("Verbose status with ids and phone numbers", List.of(), Map.of("total_tokens", 20));
        }
    }

    private static final class StubAnswerProvider implements LlmProvider {
        @Override
        public String name() {
            return "openrouter";
        }

        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            return new LlmResponse("answer", List.of(), Map.of());
        }
    }

    private static final class HallucinatedActionProvider implements LlmProvider {
        @Override
        public String name() {
            return "openrouter";
        }

        @Override
        public LlmResponse chat(String model, List<ChatMessage> messages, List<Map<String, Object>> tools) {
            return new LlmResponse("Done, text sent successfully to your wife.", List.of(), Map.of());
        }
    }

    private static final class McpEchoTool implements Tool {
        @Override
        public String name() {
            return "mcp";
        }

        @Override
        public String description() {
            return "MCP mock";
        }

        @Override
        public String execute(Map<String, Object> input, ToolContext context) {
            return """
                {
                  "ok": true,
                  "http_status": 200,
                  "http_ok": true,
                  "data": {
                    "sid": "SM123",
                    "status": "queued",
                    "to": "+14379615920"
                  }
                }
                """;
        }
    }

    private static final class InMemoryConversationStore implements ConversationStore {
        private final List<ConversationTurn> turns = new java.util.ArrayList<>();

        @Override
        public void append(ConversationTurn turn) throws IOException {
            turns.add(turn);
        }

        @Override
        public List<ConversationTurn> list() throws IOException {
            return List.copyOf(turns);
        }
    }

    private static final class InMemoryMemoryStore implements MemoryStore {
        private final List<MemoryEntry> entries = new java.util.ArrayList<>();

        @Override
        public MemoryEntry remember(String content, String source, List<String> tags) throws IOException {
            MemoryEntry entry = new MemoryEntry(
                String.valueOf(entries.size() + 1),
                content,
                tags,
                List.of(),
                source,
                Instant.now(),
                Instant.now()
            );
            entries.add(entry);
            return entry;
        }

        @Override
        public boolean forget(String id) throws IOException {
            return entries.removeIf(entry -> entry.id().equals(id));
        }

        @Override
        public List<MemoryEntry> recall(String query, int maxResults) throws IOException {
            return List.copyOf(entries);
        }

        @Override
        public List<MemoryEntry> list() throws IOException {
            return List.copyOf(entries);
        }

        @Override
        public int count() throws IOException {
            return entries.size();
        }

        @Override
        public String formatContext(int maxEntries) throws IOException {
            return "";
        }
    }

    private static final class InMemorySummaryManager implements SessionSummaryManager {
        private String summary = "";

        @Override
        public void recordTurn(String prompt, String response) throws IOException {
            summary = "User: " + prompt + " Assistant: " + response;
        }

        @Override
        public String currentSummary() throws IOException {
            return summary;
        }
    }

    private static final class InMemoryAuditStore implements AuditStore {
        private final List<AuditEvent> events = new java.util.ArrayList<>();

        @Override
        public List<AuditEvent> load() throws IOException {
            return List.copyOf(events);
        }

        @Override
        public void save(List<AuditEvent> events) throws IOException {
            this.events.clear();
            this.events.addAll(events);
        }
    }
}
