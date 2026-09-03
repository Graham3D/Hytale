package com.inigmasgames.persistentnpcs.voice;

import java.util.Locale;

/** Authored voice identity, intentionally independent from NPC and LLM identities. */
public record VoicePreset(
        String id,
        VoiceProvider provider,
        String referenceAudioPath,
        VocalEmotion defaultEmotion,
        VocalIntensity defaultIntensity,
        VocalPace defaultPace,
        Double outputGainDb) {

    public VoicePreset normalized() {
        String normalizedId = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (normalizedId.isBlank() || !normalizedId.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Voice preset id must be a safe non-empty key");
        }
        String reference = referenceAudioPath == null ? "" : referenceAudioPath.strip();
        if (!reference.isBlank() && (reference.contains("..")
                || reference.startsWith("/") || reference.startsWith("\\"))) {
            throw new IllegalArgumentException("Voice reference must be relative to its preset folder");
        }
        double gainDb = outputGainDb == null
                ? ("mara".equals(normalizedId) ? 4.0 : 0.0)
                : Math.max(-24.0, Math.min(12.0, outputGainDb));
        return new VoicePreset(normalizedId,
                provider == null ? VoiceProvider.CHATTERBOX : provider,
                reference,
                defaultEmotion == null ? VocalEmotion.CALM : defaultEmotion,
                defaultIntensity == null ? VocalIntensity.LOW : defaultIntensity,
                defaultPace == null ? VocalPace.NORMAL : defaultPace,
                gainDb);
    }

    public VocalState defaultVocalState() {
        return new VocalState(defaultEmotion, defaultIntensity, defaultPace);
    }
}
