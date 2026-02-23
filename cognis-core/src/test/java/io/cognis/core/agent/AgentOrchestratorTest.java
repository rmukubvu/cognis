package io.cognis.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.model.ChatMessage;
import io.cognis.core.model.MessageRole;
import io.cognis.core.model.ToolCall;
import io.cognis.core.memory.MemoryEntry;
import io.cognis.core.memory.MemoryStore;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentOrchestratorTest {

    @Test
    void shouldExecuteToolAndReturnFinalAnswer() {
        ProviderRegistry providers = new ProviderRegistry();
        providers.register(new ToolThenAnswerProvider());

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

    private static final class ToolThenAnswerProvider implements LlmProvider {
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
}
