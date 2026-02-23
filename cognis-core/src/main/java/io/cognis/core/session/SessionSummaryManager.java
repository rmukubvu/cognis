package io.cognis.core.session;

import java.io.IOException;

public interface SessionSummaryManager {
    void recordTurn(String prompt, String response) throws IOException;

    String currentSummary() throws IOException;
}
