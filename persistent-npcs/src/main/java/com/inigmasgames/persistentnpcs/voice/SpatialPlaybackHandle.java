package com.inigmasgames.persistentnpcs.voice;

import com.hypixel.hytale.server.core.modules.voice.ClipPlayback;
import com.inigmasgames.persistentnpcs.orbis.PlaybackId;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Limited native handle returned to the Orbis playback owner. */
public final class SpatialPlaybackHandle implements SpatialPlayback {
    private final PlaybackId playbackId;
    private final UUID npcStableId;
    private final UUID speakerId;
    private final ClipPlayback playback;

    SpatialPlaybackHandle(PlaybackId playbackId, UUID npcStableId,
            UUID speakerId, ClipPlayback playback) {
        this.playbackId = playbackId;
        this.npcStableId = npcStableId;
        this.speakerId = speakerId;
        this.playback = playback;
    }

    public PlaybackId playbackId() { return playbackId; }
    public UUID npcStableId() { return npcStableId; }
    public UUID speakerId() { return speakerId; }
    public CompletionStage<Void> completion() { return playback.completion(); }
    public boolean isDone() { return playback.isDone(); }
    public void cancel() { playback.cancel(); }
}
