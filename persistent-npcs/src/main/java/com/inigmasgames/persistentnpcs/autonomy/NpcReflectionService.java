package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Legacy memory-only hook retained for binary compatibility. E7 reflection authority now belongs
 * to epistemic.ReflectionService because only the E4 store can validate assertion supports.
 */
public final class NpcReflectionService {
    private static final int MINIMUM_NEW_EPISODES = 3;
    private final MemoryStore memories;

    public NpcReflectionService(MemoryStore memories) {
        this.memories = memories;
    }

    public boolean maybeReflect(UUID npcId, Instant since, Instant now) {
        // Never create an unsupported memory-derived fact. The E7 service owns safe proposals.
        return false;
    }
}
