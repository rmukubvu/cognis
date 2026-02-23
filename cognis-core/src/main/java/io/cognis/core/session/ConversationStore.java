package io.cognis.core.session;

import java.io.IOException;
import java.util.List;

public interface ConversationStore {
    void append(ConversationTurn turn) throws IOException;

    List<ConversationTurn> list() throws IOException;
}
