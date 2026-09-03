package com.inigmasgames.persistentnpcs.voice;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public record VoiceRenderPlan(
        UUID npcId,
        String voicePresetId,
        String voiceEffectPreset,
        VoiceProvider provider,
        Optional<Path> referenceAudio,
        boolean usingTemporaryProviderVoice,
        VocalState vocalState,
        ChatterboxControls chatterboxControls,
        double outputGainDb,
        SpeechProjection projection,
        VoiceSampleType requestedSampleType,
        VoiceSampleType resolvedSampleType,
        String voiceRevision) {

    public VoiceRenderPlan(UUID npcId, String voicePresetId, String voiceEffectPreset,
            VoiceProvider provider, Optional<Path> referenceAudio,
            boolean usingTemporaryProviderVoice, VocalState vocalState,
            ChatterboxControls chatterboxControls, double outputGainDb,
            SpeechProjection projection) {
        this(npcId, voicePresetId, voiceEffectPreset, provider, referenceAudio,
                usingTemporaryProviderVoice, vocalState, chatterboxControls, outputGainDb,
                projection, VoiceSampleType.forEmotion(vocalState == null ? null
                        : vocalState.emotion()), VoiceSampleType.REFERENCE, "LEGACY");
    }

    public VoiceRenderPlan(UUID npcId, String voicePresetId, String voiceEffectPreset,
            VoiceProvider provider, Optional<Path> referenceAudio,
            boolean usingTemporaryProviderVoice, VocalState vocalState,
            ChatterboxControls chatterboxControls, double outputGainDb) {
        this(npcId, voicePresetId, voiceEffectPreset, provider, referenceAudio,
                usingTemporaryProviderVoice, vocalState, chatterboxControls, outputGainDb,
                SpeechProjection.NORMAL, VoiceSampleType.forEmotion(vocalState == null ? null
                        : vocalState.emotion()), VoiceSampleType.REFERENCE, "LEGACY");
    }
}
