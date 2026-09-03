package com.inigmasgames.persistentnpcs.vision;

import java.util.Optional;
import java.util.UUID;

/** Optional visual supplement; it can never override structured Hytale state. */
public interface VisionProvider {
    Optional<String> describe(UUID npcId, String question);

    boolean available();
}
