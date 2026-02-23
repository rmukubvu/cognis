package io.cognis.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.cognis.core.memory.FileMemoryStore;
import io.cognis.core.profile.FileProfileStore;
import io.cognis.core.session.ConversationStore;
import io.cognis.core.session.ConversationTurn;
import io.cognis.core.session.SessionSummaryManager;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBuildDailyExecutiveBriefWithGoalsTasksAndRelationshipNudge() throws Exception {
        FileProfileStore profileStore = new FileProfileStore(tempDir.resolve("profile.json"));
        profileStore.addGoal("Close 3 client meetings");
        profileStore.addRelationship("Bukiwe", "top tier friend");

        FileMemoryStore memoryStore = new FileMemoryStore(tempDir.resolve("memories.json"));
        memoryStore.remember("User task: prepare outreach draft", "test", List.of("task"));

        WorkflowService service = new WorkflowService(
            profileStore,
            memoryStore,
            new StaticSummaryManager("Discussed priorities for outreach pipeline."),
            new InMemoryConversationStore(),
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );

        String brief = service.buildDailyExecutiveBrief();

        assertThat(brief).contains("Cognis Daily Brief");
        assertThat(brief).contains("Close 3 client meetings");
        assertThat(brief).contains("prepare outreach draft");
        assertThat(brief).contains("Relationship Nudge");
        assertThat(brief).contains("Bukiwe");
    }

    @Test
    void shouldPersistGoalWhenBuildingExecutionPlan() throws Exception {
        FileProfileStore profileStore = new FileProfileStore(tempDir.resolve("profile.json"));
        WorkflowService service = new WorkflowService(
            profileStore,
            null,
            null,
            null,
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );

        String plan = service.buildGoalExecutionPlan("Ship mobile beta", 14);

        assertThat(plan).contains("Goal Execution Loop: Ship mobile beta");
        assertThat(profileStore.get().goals()).contains("Ship mobile beta");
    }

    @Test
    void shouldBuildRelationshipNudgeUsingPersonHintAndMemories() throws Exception {
        FileProfileStore profileStore = new FileProfileStore(tempDir.resolve("profile.json"));
        profileStore.addRelationship("Bukiwe", "weekly check-ins");

        FileMemoryStore memoryStore = new FileMemoryStore(tempDir.resolve("memories.json"));
        memoryStore.remember("Bukiwe is my top tier friend", "test", List.of("relationship"));

        WorkflowService service = new WorkflowService(
            profileStore,
            memoryStore,
            null,
            null,
            Clock.fixed(Instant.parse("2026-02-21T10:00:00Z"), ZoneOffset.UTC)
        );

        String nudge = service.buildRelationshipNudge("bukiwe");

        assertThat(nudge).contains("Bukiwe");
        assertThat(nudge).contains("top tier friend");
    }

    private static final class StaticSummaryManager implements SessionSummaryManager {
        private final String summary;

        private StaticSummaryManager(String summary) {
            this.summary = summary;
        }

        @Override
        public void recordTurn(String prompt, String response) {
        }

        @Override
        public String currentSummary() {
            return summary;
        }
    }

    private static final class InMemoryConversationStore implements ConversationStore {
        @Override
        public void append(ConversationTurn turn) {
        }

        @Override
        public List<ConversationTurn> list() {
            return List.of();
        }
    }
}
