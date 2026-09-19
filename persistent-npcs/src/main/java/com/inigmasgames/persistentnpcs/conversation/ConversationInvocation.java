package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Immutable branch ownership supplied by Orbis. */
public record ConversationInvocation(
        UUID responseId,
        UUID providerRequestId,
        LlmProvider provider,
        BooleanSupplier current,
        ConversationLifecycleObserver observer,
        long branchEpoch) {

    public ConversationInvocation {
        java.util.Objects.requireNonNull(responseId, "responseId");
        java.util.Objects.requireNonNull(providerRequestId, "providerRequestId");
        java.util.Objects.requireNonNull(provider, "provider");
        current = current == null ? () -> true : current;
        observer = observer == null ? ConversationLifecycleObserver.none() : observer;
        if (branchEpoch < 0) throw new IllegalArgumentException("branchEpoch cannot be negative");
    }

    public ConversationInvocation(UUID responseId, UUID providerRequestId,
            LlmProvider provider, BooleanSupplier current,
            ConversationLifecycleObserver observer) {
        this(responseId, providerRequestId, provider, current, observer, 0L);
    }

    public boolean isCurrent() {
        return current.getAsBoolean();
    }
}
