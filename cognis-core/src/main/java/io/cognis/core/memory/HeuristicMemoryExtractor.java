package io.cognis.core.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeuristicMemoryExtractor implements MemoryExtractor {
    private static final Pattern NAME_PATTERN = Pattern.compile("\\bmy name is ([A-Za-z][A-Za-z0-9_' -]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_PATTERN = Pattern.compile("\\b(?:i live in|i am in|i'm in|i am from|i'm from) ([A-Za-z][A-Za-z0-9,' -]{1,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERENCE_PATTERN = Pattern.compile("\\b(?:i prefer|i like|i love) ([^.\\n!?]{3,100})", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMINDER_PATTERN = Pattern.compile("\\bremind me to ([^.\\n!?]{3,120})", Pattern.CASE_INSENSITIVE);

    @Override
    public List<ExtractedMemory> extract(String userPrompt, String assistantResponse) {
        String input = userPrompt == null ? "" : userPrompt.trim();
        if (input.isBlank()) {
            return List.of();
        }

        Map<String, ExtractedMemory> extracted = new LinkedHashMap<>();
        addMatches(extracted, NAME_PATTERN, input, value -> new ExtractedMemory("User name is " + value, List.of("profile", "name")));
        addMatches(extracted, LOCATION_PATTERN, input, value -> new ExtractedMemory("User location is " + value, List.of("profile", "location")));
        addMatches(extracted, PREFERENCE_PATTERN, input, value -> new ExtractedMemory("User preference: " + normalizeTail(value), List.of("preference")));
        addMatches(extracted, REMINDER_PATTERN, input, value -> new ExtractedMemory("User task: " + normalizeTail(value), List.of("task")));

        for (String sentence : splitSentences(input)) {
            String normalized = sentence.trim();
            if (normalized.length() < 12 || normalized.length() > 180) {
                continue;
            }
            String lowered = normalized.toLowerCase(Locale.ROOT);
            if (containsMemorySignal(lowered)) {
                putIfAbsent(extracted, new ExtractedMemory(normalized, List.of("fact")));
            }
            if (extracted.size() >= 5) {
                break;
            }
        }

        return new ArrayList<>(extracted.values()).subList(0, Math.min(5, extracted.size()));
    }

    private void addMatches(
        Map<String, ExtractedMemory> extracted,
        Pattern pattern,
        String input,
        java.util.function.Function<String, ExtractedMemory> mapper
    ) {
        Matcher matcher = pattern.matcher(input);
        while (matcher.find() && extracted.size() < 5) {
            String value = normalizeTail(matcher.group(1));
            if (!value.isBlank()) {
                putIfAbsent(extracted, mapper.apply(value));
            }
        }
    }

    private void putIfAbsent(Map<String, ExtractedMemory> extracted, ExtractedMemory memory) {
        String key = memory.content().toLowerCase(Locale.ROOT);
        extracted.putIfAbsent(key, memory);
    }

    private List<String> splitSentences(String input) {
        String[] parts = input.split("[.!?\\n]+");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part.trim();
            if (!cleaned.isBlank()) {
                sentences.add(cleaned);
            }
        }
        return sentences;
    }

    private boolean containsMemorySignal(String lowered) {
        return lowered.contains("prefer")
            || lowered.contains("goal")
            || lowered.contains("always")
            || lowered.contains("never")
            || lowered.contains("deadline")
            || lowered.contains("meeting")
            || lowered.contains("timezone")
            || lowered.contains("important");
    }

    private String normalizeTail(String text) {
        String normalized = text == null ? "" : text.trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }
}
