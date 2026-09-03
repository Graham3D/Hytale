package com.inigmasgames.persistentnpcs.voice;

/** Local voice-worker configuration. Heavy inference always runs outside Hytale threads. */
public record VoiceRuntimeConfig(
        Boolean enabled,
        String pythonExecutable,
        String ttsDevice,
        String whisperModel,
        String whisperDevice,
        String whisperComputeType,
        String sttProvider,
        String moonshineModel,
        Integer utteranceGapMillis,
        Integer opusBitrate,
        Boolean debugLogging,
        Boolean exportDebugWav,
        Boolean forceEnableSingleplayerVoice,
        Double conversationListenRadius,
        Double remoteHailRadius,
        Double npcSpeechMaxRadius) {

    public boolean voiceEnabled() { return enabled == null || enabled; }
    public String effectiveTtsDevice() {
        return ttsDevice == null || ttsDevice.isBlank() ? "auto" : ttsDevice.strip();
    }
    public String effectiveWhisperModel() {
        return whisperModel == null || whisperModel.isBlank() ? "base.en" : whisperModel.strip();
    }
    public String effectiveWhisperDevice() {
        return whisperDevice == null || whisperDevice.isBlank() ? "cpu" : whisperDevice.strip();
    }
    public String effectiveWhisperComputeType() {
        return whisperComputeType == null || whisperComputeType.isBlank()
                ? "int8" : whisperComputeType.strip();
    }
    public String effectiveSttProvider() {
        String value = sttProvider == null ? "AUTO" : sttProvider.strip().toUpperCase();
        return switch (value) {
            case "AUTO", "MOONSHINE", "FASTER_WHISPER" -> value;
            default -> throw new IllegalArgumentException(
                    "sttProvider must be AUTO, MOONSHINE, or FASTER_WHISPER");
        };
    }
    public String effectiveMoonshineModel() {
        String value = moonshineModel == null ? "TINY_STREAMING"
                : moonshineModel.strip().toUpperCase();
        return switch (value) {
            case "TINY_STREAMING", "BASE_STREAMING" -> value;
            default -> throw new IllegalArgumentException(
                    "moonshineModel must be TINY_STREAMING or BASE_STREAMING");
        };
    }
    public int effectiveUtteranceGapMillis() {
        return utteranceGapMillis == null ? 250 : Math.max(200, Math.min(1000, utteranceGapMillis));
    }
    public int effectiveOpusBitrate() {
        return opusBitrate == null ? 24_000 : Math.max(12_000, Math.min(48_000, opusBitrate));
    }
    public boolean debug() { return debugLogging == null || debugLogging; }
    public boolean exportWav() { return exportDebugWav != null && exportDebugWav; }
    public boolean forceSingleplayerVoice() {
        return forceEnableSingleplayerVoice == null || forceEnableSingleplayerVoice;
    }
    public double effectiveConversationListenRadius() {
        return bounded(conversationListenRadius, 5.0, 2.0, 32.0);
    }
    public double effectiveRemoteHailRadius() {
        double listen = effectiveConversationListenRadius();
        return bounded(remoteHailRadius, listen * 3.0, listen, 96.0);
    }
    public double effectiveNpcSpeechMaxRadius() {
        double listen = effectiveConversationListenRadius();
        return bounded(npcSpeechMaxRadius, listen * 3.0, listen, 96.0);
    }

    public VoiceRuntimeConfig validated() {
        effectiveUtteranceGapMillis();
        effectiveOpusBitrate();
        effectiveSttProvider();
        effectiveMoonshineModel();
        effectiveConversationListenRadius();
        effectiveRemoteHailRadius();
        effectiveNpcSpeechMaxRadius();
        return this;
    }

    private static double bounded(Double value, double fallback, double minimum,
            double maximum) {
        double selected = value == null || !Double.isFinite(value) ? fallback : value;
        return Math.max(minimum, Math.min(maximum, selected));
    }
}
