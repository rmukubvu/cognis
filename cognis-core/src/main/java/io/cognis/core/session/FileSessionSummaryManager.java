package io.cognis.core.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSessionSummaryManager implements SessionSummaryManager {
    private final Path summaryPath;
    private final int maxChars;

    public FileSessionSummaryManager(Path summaryPath, int maxChars) {
        this.summaryPath = summaryPath;
        this.maxChars = Math.max(64, maxChars);
    }

    @Override
    public synchronized void recordTurn(String prompt, String response) throws IOException {
        String current = currentSummary();
        String snippet = "User: " + shrink(prompt) + " | Assistant: " + shrink(response);
        String merged = current.isBlank() ? snippet : current + "\n" + snippet;
        if (merged.length() > maxChars) {
            merged = merged.substring(merged.length() - maxChars);
        }
        save(merged);
    }

    @Override
    public synchronized String currentSummary() throws IOException {
        if (!Files.exists(summaryPath)) {
            return "";
        }
        return Files.readString(summaryPath, StandardCharsets.UTF_8).trim();
    }

    private void save(String summary) throws IOException {
        Files.createDirectories(summaryPath.getParent());
        Files.writeString(summaryPath, summary + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private String shrink(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 177) + "...";
    }
}
