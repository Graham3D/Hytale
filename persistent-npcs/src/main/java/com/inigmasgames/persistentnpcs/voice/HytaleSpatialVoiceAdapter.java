package com.inigmasgames.persistentnpcs.voice;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.voice.ClipPlayback;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoiceSpeaker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.orbis.PlaybackId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Narrow Update 6 adapter from Orbis playback requests to native entity voice.
 *
 * <p>This class owns only cached {@link VoiceSpeaker} handles for loaded NPC bodies. It does
 * not create turns, response IDs, queues, chunk order, cancellation state, or timing state.</p>
 */
public final class HytaleSpatialVoiceAdapter implements AutoCloseable, SpatialPlaybackAdapter {
    private final VoiceRuntimeConfig config;
    private final VoiceModule voiceModule;
    private final Consumer<String> log;
    private final Map<UUID, VoiceSpeaker> speakers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public HytaleSpatialVoiceAdapter(VoiceRuntimeConfig config, Consumer<String> log) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.log = log == null ? ignored -> { } : log;
        voiceModule = VoiceModule.get();
        if (config.voiceEnabled() && voiceModule != null && !voiceModule.isVoiceEnabled()
                && config.forceSingleplayerVoice()) {
            voiceModule.setVoiceEnabled(true);
            this.log.accept("VOICE_SINGLEPLAYER_ENABLED Hytale VoiceModule enabled through "
                    + "its supported server API before client voice configuration.");
        }
        if (available()) {
            this.log.accept("ORBIS_SPATIAL_ADAPTER_READY routing=entity-voice"
                    + " conversationListenRadius=" + config.effectiveConversationListenRadius()
                    + " remoteHailRadius=" + config.effectiveRemoteHailRadius()
                    + " npcSpeechMaxRadius=" + config.effectiveNpcSpeechMaxRadius()
                    + " hytaleGlobalSpatialMax=" + voiceModule.getMaxHearingDistance());
        } else {
            this.log.accept("ORBIS_SPATIAL_ADAPTER_UNAVAILABLE " + diagnosticStatus());
        }
    }

    public boolean available() {
        return config.voiceEnabled() && voiceModule != null && voiceModule.isVoiceEnabled()
                && !closed.get();
    }

    public String diagnosticStatus() {
        if (!config.voiceEnabled()) return "disabled in voice.json";
        if (voiceModule == null || !voiceModule.isVoiceEnabled()) {
            return "Hytale server voice disabled; set forceEnableSingleplayerVoice=true "
                    + "and bind Push to Talk to E";
        }
        return closed.get() ? "closed" : "READY: Orbis -> Hytale entity voice";
    }

    /** Called on the owning world tick; native speaker lifetime follows the loaded NPC entity. */
    public void observeNpc(UUID npcId, Ref<EntityStore> entityRef, boolean listening) {
        if (!available() || npcId == null) return;
        VoiceSpeaker current = speakers.get(npcId);
        if (entityRef == null || !entityRef.isValid()) {
            closeSpeaker(npcId, current, "entity-invalidated");
            return;
        }
        if (current != null && current.isOpen()) return;
        VoiceSpeaker opened = voiceModule.openEntityVoice(entityRef);
        VoiceSpeaker previous = speakers.put(npcId, opened);
        if (previous != null && previous != opened) previous.close();
        log.accept("VOICE_SPEAKER_OPEN npc=" + npcId + " speaker=" + opened.id()
                + " at=" + Instant.now());
    }

    @Override
    public SpatialPlaybackHandle playOrbis(UUID npcId, PlaybackId playbackId,
            List<byte[]> opusFrames) {
        if (!available()) throw new IllegalStateException("Hytale spatial voice is unavailable");
        if (npcId == null || playbackId == null || opusFrames == null
                || opusFrames.isEmpty()) {
            throw new IllegalArgumentException("complete spatial playback request required");
        }
        VoiceSpeaker speaker = speakers.get(npcId);
        if (speaker == null || !speaker.isOpen()) {
            throw new IllegalStateException("No active entity speaker for NPC " + npcId);
        }
        List<byte[]> immutableFrames = opusFrames.stream().map(byte[]::clone).toList();
        for (byte[] frame : immutableFrames) {
            if (frame.length > VoiceSpeaker.MAX_OPUS_FRAME_BYTES) {
                throw new IllegalArgumentException("Opus frame exceeds Hytale's 512-byte limit");
            }
        }
        ClipPlayback playback = speaker.play(immutableFrames);
        return new SpatialPlaybackHandle(playbackId, npcId, speaker.id(), playback);
    }

    public void entityRemoved(UUID npcId) {
        if (npcId != null) closeSpeaker(npcId, speakers.get(npcId), "entity-removed");
    }

    private void closeSpeaker(UUID npcId, VoiceSpeaker speaker, String reason) {
        if (speaker == null || !speakers.remove(npcId, speaker)) return;
        try {
            speaker.close();
        } finally {
            log.accept("VOICE_SPEAKER_CLOSED npc=" + npcId + " reason=" + reason);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Map.copyOf(speakers).forEach((npcId, speaker) ->
                closeSpeaker(npcId, speaker, "plugin-shutdown"));
    }
}
