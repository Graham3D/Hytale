package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.conversation.ConversationLifecycleObserver;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Existing cognition and Hytale output exposed as services to the Orbis owner. */
public interface OrbisCognitionGateway {
    CompletableFuture<ConversationOutcome> begin(
            BranchCognitionSnapshot snapshot,
            PinnedLlmProvider provider,
            ConversationLifecycleObserver observer);

    CompletableFuture<Void> commit(
            BranchCognitionSnapshot snapshot,
            ConversationOutcome outcome);

    /** Phase 3 exact canonical chunks. Older test/service adapters remain source-compatible. */
    default CompletableFuture<Void> commit(
            BranchCognitionSnapshot snapshot,
            ConversationOutcome outcome,
            List<CanonicalSpeechChunk> chunks) {
        return commit(snapshot, outcome);
    }

    /** Displays one immutable phrase already validated by deterministic Orbis policy. */
    default CompletableFuture<Void> commitPhrase(
            BranchCognitionSnapshot snapshot, CanonicalSpeechChunk chunk) {
        return CompletableFuture.completedFuture(null);
    }

    /** Finalizes display after zero or more phrases were committed during generation. */
    default CompletableFuture<Void> finalizeCommit(
            BranchCognitionSnapshot snapshot, ConversationOutcome outcome,
            List<CanonicalSpeechChunk> chunks, int alreadyCommittedCount) {
        return alreadyCommittedCount == 0 ? commit(snapshot, outcome, chunks)
                : CompletableFuture.completedFuture(null);
    }

    /** Records player-known dialogue only after native playback reaches a terminal state. */
    default CompletableFuture<Void> deliveryCompleted(
            BranchCognitionSnapshot snapshot,
            ConversationOutcome outcome,
            SpeechDeliveryReport delivery) {
        return CompletableFuture.completedFuture(null);
    }

    void failed(BranchCognitionSnapshot snapshot, CancellationReason reason, Throwable failure);
}
