package com.inigmasgames.persistentnpcs.cognition;

import java.util.Locale;
import java.util.Set;

/** Semantic gate used before any lexical repetition suppression. */
public final class MaterialInformationGuard {
    private static final Set<String> QUALIFIERS = Set.of(
            "now", "today", "tonight", "tomorrow", "yesterday", "here", "there",
            "near", "inside", "outside", "urgent", "danger", "hurt", "missing",
            "left", "arrived", "changed", "will", "promise", "need", "want", "asked");

    private MaterialInformationGuard() { }

    public static boolean containsMaterialUpdate(String newest, String previous) {
        String current = normalize(newest);
        if (current.isBlank()) return false;
        String old = normalize(previous);
        if (old.isBlank() || !current.equals(old)) {
            Set<String> currentTerms = terms(current);
            Set<String> oldTerms = terms(old);
            if (!oldTerms.containsAll(currentTerms)) return true;
            if (currentTerms.stream().anyMatch(QUALIFIERS::contains)
                    && !oldTerms.containsAll(currentTerms)) return true;
            if (current.contains("?") || hasRequest(current)) return true;
        }
        return false;
    }

    private static boolean hasRequest(String value) {
        return value.contains("please ") || value.startsWith("tell ")
                || value.startsWith("go ") || value.startsWith("come ")
                || value.contains(" can you ") || value.contains(" would you ");
    }

    private static Set<String> terms(String value) {
        return java.util.Arrays.stream(value.split("[^a-z0-9]+"))
                .filter(term -> term.length() >= 2)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").strip();
    }
}
