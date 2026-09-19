package com.inigmasgames.persistentnpcs.llm;

import java.util.concurrent.CompletableFuture;

/** Provider-neutral lifecycle hook used by Orbis resource scheduling. */
public interface ManagedLlmResidency {
    CompletableFuture<Void> ensureResident();
    CompletableFuture<Boolean> unloadResident();
    boolean residencyPrepared();
}
