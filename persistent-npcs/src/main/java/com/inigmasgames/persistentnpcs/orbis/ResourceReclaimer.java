package com.inigmasgames.persistentnpcs.orbis;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ResourceReclaimer {
    CompletableFuture<ResourceReclaimResult> reclaim(
            ResourceWorkload requestedWorkload, String reason);

    static ResourceReclaimer unavailable() {
        return (workload, reason) -> CompletableFuture.completedFuture(
                new ResourceReclaimResult("NO_PROVIDER_LIFECYCLE_REGISTERED",
                        "UNAVAILABLE", false));
    }
}
