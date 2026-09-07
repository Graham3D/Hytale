package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.UUID;

/** Serializable-shaped world solution, never a retained native Ref or a request to reacquire a better target. */
public record CommittedTarget(UUID worldId, Vec3 origin, Vec3 point, Vec3 direction, UUID entityId) {
    public CommittedTarget {
        if(worldId==null || origin==null || point==null || direction==null)
            throw new IllegalArgumentException("Incomplete committed world target");
    }
}
