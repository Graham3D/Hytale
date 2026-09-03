package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Admission lifecycle event. Rechecks are bounded to queued provider requests. */
public record OrbisResourceEvent(Type type, UUID requestId, ResourceWorkload workload,
        ResourcePriority priority, ExecutionPlacement placement, Instant at,
        long admissionWaitMillis, Map<String, String> facts) {
    public OrbisResourceEvent {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    public enum Type {
        RESOURCE_SNAPSHOT,
        RESOURCE_REQUESTED,
        RESOURCE_ADMITTED,
        RESOURCE_DEFERRED,
        RESOURCE_RECHECK,
        RESOURCE_RECLAIM_ATTEMPT,
        RESOURCE_ADMISSION_FAILED,
        RESOURCE_RELEASED,
        RESOURCE_PRESSURE,
        BACKEND_SELECTED,
        PROVIDER_BUSY,
        RESOURCE_TIMEOUT
    }
}
