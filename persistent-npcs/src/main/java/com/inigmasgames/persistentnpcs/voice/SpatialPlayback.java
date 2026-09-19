package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.orbis.PlaybackId;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Native playback lifecycle exposed to the Orbis owner. */
public interface SpatialPlayback {
    PlaybackId playbackId();
    UUID npcStableId();
    UUID speakerId();
    CompletionStage<Void> completion();
    boolean isDone();
    void cancel();
}
