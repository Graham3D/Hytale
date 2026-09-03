package com.inigmasgames.persistentnpcs.voice;

public record SpeechTranscript(
        String text, long decodeMillis, long whisperMillis, String language,
        String requestedEngine, String actualEngine, boolean fallback,
        String fallbackReason, String device, String computeMode, long workerPid) {

    public SpeechTranscript {
        text = text == null ? "" : text;
        language = language == null ? "" : language;
        requestedEngine = value(requestedEngine);
        actualEngine = value(actualEngine);
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
        device = value(device);
        computeMode = value(computeMode);
    }

    /** Source compatibility for remote/test providers that do not expose engine metadata. */
    public SpeechTranscript(String text, long decodeMillis, long whisperMillis,
            String language) {
        this(text, decodeMillis, whisperMillis, language, "UNKNOWN", "UNKNOWN",
                false, "", "UNKNOWN", "UNKNOWN", -1);
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
