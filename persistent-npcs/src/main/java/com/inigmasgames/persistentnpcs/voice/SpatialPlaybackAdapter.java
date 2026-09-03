package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.orbis.PlaybackId;
import java.util.List;
import java.util.UUID;

/** Limited codec/native boundary. It owns no response queue or branch state. */
@FunctionalInterface
public interface SpatialPlaybackAdapter {
    SpatialPlayback playOrbis(UUID npcStableId, PlaybackId playbackId,
            List<byte[]> opusFrames);
}
