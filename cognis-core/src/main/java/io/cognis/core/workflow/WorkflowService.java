package io.cognis.core.workflow;

import io.cognis.core.memory.MemoryEntry;
import io.cognis.core.memory.MemoryStore;
import io.cognis.core.profile.ProfileStore;
import io.cognis.core.profile.UserProfile;
import io.cognis.core.session.ConversationStore;
import io.cognis.core.session.ConversationTurn;
import io.cognis.core.session.SessionSummaryManager;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class WorkflowService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US);

    private final ProfileStore profileStore;
    private final MemoryStore memoryStore;
    private final SessionSummaryManager summaryManager;
    private final ConversationStore conversationStore;
    private final Clock clock;

    public WorkflowService(
        ProfileStore profileStore,
        MemoryStore memoryStore,
        SessionSummaryManager summaryManager,
        ConversationStore conversationStore
    ) {
        this(profileStore, memoryStore, summaryManager, conversationStore, Clock.systemUTC());
    }

    public WorkflowService(
        ProfileStore profileStore,
        MemoryStore memoryStore,
        SessionSummaryManager summaryManager,
        ConversationStore conversationStore,
        Clock clock
    ) {
        this.profileStore = profileStore;
        this.memoryStore = memoryStore;
        this.summaryManager = summaryManager;
        this.conversationStore = conversationStore;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String buildDailyExecutiveBrief() throws IOException {
        UserProfile profile = loadProfile();
        List<MemoryEntry> taskMemories = taggedMemories("task", 6);
        List<String> priorities = derivePriorities(profile, taskMemories);
        List<String> risks = deriveRisks(taskMemories);
        String summary = loadSummary();

        StringBuilder out = new StringBuilder();
        out.append("Cognis Daily Brief - ")
            .append(today(profile))
            .append("\n\n");

        out.append("Top Priorities:\n");
        appendBullets(out, priorities);

        out.append("\nRisks & Watchouts:\n");
        appendBullets(out, risks);

        if (!summary.isBlank()) {
            out.append("\nContext Snapshot:\n- ")
                .append(truncate(summary.replace('\n', ' '), 220))
                .append("\n");
        }

        String relationshipNudge = buildRelationshipNudge(null);
        if (!relationshipNudge.isBlank()) {
            out.append("\nRelationship Nudge:\n- ")
                .append(relationshipNudge)
                .append("\n");
        }
        return out.toString().trim();
    }

    public String buildGoalExecutionPlan(String goal, int horizonDays) throws IOException {
        String normalizedGoal = normalize(goal);
        if (normalizedGoal.isBlank()) {
            return "Error: goal is required";
        }
        int days = Math.max(1, horizonDays);
        if (profileStore != null) {
            profileStore.addGoal(normalizedGoal);
        }

        StringBuilder out = new StringBuilder();
        out.append("Goal Execution Loop: ").append(normalizedGoal).append("\n");
        out.append("Horizon: ").append(days).append(" day(s)\n\n");
        out.append("Plan:\n");
        out.append("1. Define success criteria and deadline.\n");
        out.append("2. Break into 3 concrete tasks with owners and due dates.\n");
        out.append("3. Execute highest-impact task today.\n");
        out.append("4. Run daily check-in: progress, blocker, next action.\n");
        out.append("5. End-of-horizon review: outcome, lessons, follow-up goal.\n");
        return out.toString().trim();
    }

    public String buildRelationshipNudge(String personHint) throws IOException {
        UserProfile profile = loadProfile();
        if (profile.relationships().isEmpty()) {
            return "";
        }

        String person = pickPerson(profile, personHint);
        if (person.isBlank()) {
            return "";
        }

        String notes = profile.relationships().getOrDefault(person, "").trim();
        List<MemoryEntry> mentions = recallPersonMentions(person, 3);
        String lastMention = mentions.isEmpty() ? "" : mentions.getFirst().content();

        StringBuilder out = new StringBuilder();
        out.append("Check in with ").append(person);
        if (!notes.isBlank()) {
            out.append(" (").append(truncate(notes, 100)).append(")");
        }
        if (!lastMention.isBlank()) {
            out.append(". Relevant memory: ").append(truncate(lastMention, 120));
        }
        return out.toString();
    }

    public String buildGoalCheckIn(String goal) throws IOException {
        String normalizedGoal = normalize(goal);
        String title = normalizedGoal.isBlank() ? "your current goal" : normalizedGoal;
        List<ConversationTurn> turns = recentTurns(4);
        String latestContext = turns.isEmpty() ? "" : turns.getFirst().prompt();

        StringBuilder out = new StringBuilder();
        out.append("Goal Check-in: ").append(title).append("\n");
        out.append("Answer in 30 seconds:\n");
        out.append("1. What moved forward since yesterday?\n");
        out.append("2. What is blocked?\n");
        out.append("3. What single action will you complete next?\n");
        if (!latestContext.isBlank()) {
            out.append("Recent context: ").append(truncate(latestContext, 120));
        }
        return out.toString().trim();
    }

    private UserProfile loadProfile() throws IOException {
        if (profileStore == null) {
            return UserProfile.empty();
        }
        return profileStore.get();
    }

    private String today(UserProfile profile) {
        ZoneId zone = safeZone(profile.timezone());
        LocalDate date = LocalDate.now(clock.withZone(zone));
        return DATE_FMT.format(date) + " (" + zone + ")";
    }

    private ZoneId safeZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    private List<String> derivePriorities(UserProfile profile, List<MemoryEntry> tasks) {
        LinkedHashSet<String> priorities = new LinkedHashSet<>();
        profile.goals().stream()
            .map(goal -> "Advance goal: " + goal)
            .limit(3)
            .forEach(priorities::add);
        tasks.stream()
            .map(MemoryEntry::content)
            .map(this::normalizeTaskLine)
            .limit(3)
            .forEach(priorities::add);
        if (priorities.isEmpty()) {
            priorities.add("Capture top 3 outcomes for today.");
        }
        return List.copyOf(priorities);
    }

    private List<String> deriveRisks(List<MemoryEntry> tasks) {
        List<String> risks = new ArrayList<>();
        for (MemoryEntry task : tasks) {
            String lowered = task.content().toLowerCase(Locale.ROOT);
            if (lowered.contains("deadline") || lowered.contains("urgent") || lowered.contains("tomorrow")) {
                risks.add("Potential deadline risk: " + truncate(task.content(), 120));
            }
        }
        if (risks.isEmpty()) {
            risks.add("No explicit blockers captured. Run a midday check-in.");
        }
        return risks;
    }

    private String loadSummary() throws IOException {
        if (summaryManager == null) {
            return "";
        }
        return summaryManager.currentSummary();
    }

    private List<MemoryEntry> taggedMemories(String tag, int maxResults) throws IOException {
        if (memoryStore == null) {
            return List.of();
        }
        return memoryStore.recall(tag, maxResults).stream()
            .filter(entry -> entry.tags() != null && entry.tags().stream().anyMatch(t -> tag.equalsIgnoreCase(t)))
            .toList();
    }

    private List<MemoryEntry> recallPersonMentions(String person, int maxResults) throws IOException {
        if (memoryStore == null || person.isBlank()) {
            return List.of();
        }
        return memoryStore.recall(person, maxResults);
    }

    private List<ConversationTurn> recentTurns(int max) throws IOException {
        if (conversationStore == null) {
            return List.of();
        }
        return conversationStore.list().stream()
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .limit(Math.max(1, max))
            .toList();
    }

    private String pickPerson(UserProfile profile, String personHint) {
        if (personHint != null && !personHint.isBlank()) {
            for (String person : profile.relationships().keySet()) {
                if (person.equalsIgnoreCase(personHint.trim())) {
                    return person;
                }
            }
        }
        return profile.relationships().keySet().stream().findFirst().orElse("");
    }

    private String normalizeTaskLine(String input) {
        String text = input == null ? "" : input.trim();
        if (text.toLowerCase(Locale.ROOT).startsWith("user task:")) {
            text = text.substring("user task:".length()).trim();
        }
        return text;
    }

    private void appendBullets(StringBuilder out, List<String> lines) {
        for (String line : lines) {
            out.append("- ").append(line).append("\n");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
