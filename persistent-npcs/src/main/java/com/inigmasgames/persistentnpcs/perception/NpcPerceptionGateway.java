package com.inigmasgames.persistentnpcs.perception;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Boundary seam: production captures Hytale state; evaluation supplies detached world facts. */
@FunctionalInterface
public interface NpcPerceptionGateway {
    CompletableFuture<RawPerceptionSnapshot> captureRaw(
            NpcProfile profile, UUID focusedPlayerId, UUID responseId);
}
