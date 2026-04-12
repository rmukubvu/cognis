package io.cognis.core.session;

import java.util.List;

/**
 * No-op {@link ConversationStore} for ephemeral subagent runs that do not
 * need conversation history persisted across invocations.
 *
 * <p>Used by {@link io.cognis.core.tool.impl.AgentTool} when spawning one-shot subagents
 * via {@code action=spawn}. The parent agent manages continuity; the child run is
 * fire-and-forget from a conversation-history perspective.
 */
public final class NoOpConversationStore implements ConversationStore {

    /** Shared singleton — stateless, so safe to share across threads and runs. */
    public static final NoOpConversationStore INSTANCE = new NoOpConversationStore();

    private NoOpConversationStore() {}

    @Override
    public void append(ConversationTurn turn) {
        // intentionally no-op
    }

    @Override
    public List<ConversationTurn> list() {
        return List.of();
    }
}
