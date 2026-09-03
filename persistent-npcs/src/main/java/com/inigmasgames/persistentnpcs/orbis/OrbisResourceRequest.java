package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import java.util.UUID;

public record OrbisResourceRequest(UUID requestId, ResourceWorkload workload,
        ResourcePriority priority, AiProvider provider, boolean foreground,
        long timeoutMillis) {
    public OrbisResourceRequest {
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(workload, "workload");
        java.util.Objects.requireNonNull(priority, "priority");
        java.util.Objects.requireNonNull(provider, "provider");
    }
}
