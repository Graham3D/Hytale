package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/** Resolves immutable authored voice identity separately from model and conversation state. */
public final class NpcVoiceService {
    private final VoicePresetRepository presets;
    private final ChatterboxPerformanceController performance;
    private final Consumer<String> log;

    public NpcVoiceService(
            VoicePresetRepository presets,
            ChatterboxPerformanceController performance,
            Consumer<String> log) {
        this.presets = presets;
        this.performance = performance;
        this.log = log == null ? ignored -> { } : log;
    }

    public VoiceRenderPlan plan(NpcProfile profile, VocalState state) {
        return plan(profile, state, SpeechProjection.NORMAL);
    }

    public VoiceRenderPlan plan(
            NpcProfile profile, VocalState state, SpeechProjection projection) {
        VoicePreset preset = presets.resolve(profile);
        ChatterboxControls controls = performance.controls(profile.id(), preset, state);
        VocalState effectiveState = state == null ? preset.defaultVocalState() : state;
        SpeechProjection effectiveProjection = projection == null
                ? SpeechProjection.NORMAL : projection;
        VoicePresetRepository.ResolvedVoiceSample sample =
                presets.resolveSample(preset, effectiveState.emotion());
        Optional<Path> reference = sample.path();
        if (reference.isEmpty()) {
            throw new IllegalStateException("NPC voice is not ready: missing or invalid "
                    + VoicePresetRepository.expectedFilename(profile.name(),
                            VoiceSampleType.REFERENCE));
        }
        String effect = profile.voiceEffectPreset() == null
                || profile.voiceEffectPreset().isBlank() ? "none" : profile.voiceEffectPreset();
        java.util.UUID stableIdentity = profile.stableId() == null ? profile.id()
                : profile.stableId();
        VoiceRenderPlan plan = new VoiceRenderPlan(stableIdentity, preset.id(), effect,
                preset.provider(), reference, false,
                effectiveState, controls,
                Math.min(6.0, preset.outputGainDb() + effectiveProjection.gainBoostDb()),
                effectiveProjection, sample.requestedType(), sample.resolvedType(),
                sample.revision());
        log.accept("VOICE_PLAN npc=" + profile.id() + " preset=" + preset.id()
                + " provider=" + preset.provider() + " reference="
                + reference.map(Path::toString).orElse("CHATTERBOX_BUILT_IN_FALLBACK")
                + " requestedSample=" + sample.requestedType()
                + " resolvedSample=" + sample.resolvedType()
                + " referenceFallback=" + sample.fellBackToReference()
                + " voiceRevision=" + sample.revision()
                + " emotion=" + plan.vocalState().emotion() + " intensity="
                + plan.vocalState().intensity() + " pace=" + plan.vocalState().pace()
                + " exaggeration=" + controls.exaggeration() + " cfgWeight="
                + controls.cfgWeight() + " temperature=" + controls.temperature()
                + " projection=" + plan.projection()
                + " outputGainDb=" + plan.outputGainDb());
        return plan;
    }
}
