package com.inigmasgames.persistentnpcs.orbis;

/** Detects streaming-final collapse without inventing or merging lexical content. */
public final class TranscriptIntegrityGuard {
    public record Assessment(boolean suspect, String reason) { }
    private TranscriptIntegrityGuard() { }

    public static Assessment assess(String stablePartial, String finalTranscript) {
        String partial = normalize(stablePartial);
        String complete = normalize(finalTranscript);
        if (partial.isBlank() || complete.isBlank()) {
            return new Assessment(!partial.isBlank() && complete.isBlank(),
                    complete.isBlank() && !partial.isBlank() ? "FINAL_BLANK_AFTER_STABLE_PARTIAL"
                            : "NO_COMPARABLE_PARTIAL");
        }
        if (partial.length() >= 12 && complete.length() * 2 < partial.length()) {
            return new Assessment(true, "FINAL_MATERIALLY_SHORTER_THAN_STABLE_PARTIAL");
        }
        if (partial.startsWith(complete) && partial.length() - complete.length() >= 8) {
            return new Assessment(true, "FINAL_COLLAPSED_TO_PARTIAL_PREFIX");
        }
        return new Assessment(false, "CONSISTENT");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }
}
