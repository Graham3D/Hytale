package com.inigmasgames.persistentnpcs.training.corpus;

import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Offline evaluation wrapper that captures the request at the same boundary used by
 * OpenAiCompatibleProvider. OrbisEvaluationHost can be constructed with this wrapper,
 * so no prompt is reconstructed from evaluation metadata.
 */
public final class ExactInputCaptureProvider implements LlmProvider {
    private final LlmProvider delegate;
    private final Function<LlmRequest, ProductionInputSnapshot> snapshotFactory;
    private final BiConsumer<LlmRequest, ProductionInputSnapshot> sink;

    public ExactInputCaptureProvider(LlmProvider delegate,
            Function<LlmRequest, ProductionInputSnapshot> snapshotFactory,
            BiConsumer<LlmRequest, ProductionInputSnapshot> sink) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.snapshotFactory = java.util.Objects.requireNonNull(snapshotFactory,
                "snapshotFactory");
        this.sink = java.util.Objects.requireNonNull(sink, "sink");
    }

    @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        capture(request);
        return delegate.generateResponse(request);
    }
    @Override public CompletableFuture<LlmResult> stream(LlmRequest request,
            Consumer<String> tokenConsumer) {
        capture(request);
        return delegate.stream(request, tokenConsumer);
    }
    private void capture(LlmRequest request) { sink.accept(request, snapshotFactory.apply(request)); }

    @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
        return delegate.checkStatus();
    }
    @Override public String providerId() { return "exact-capture(" + delegate.providerId() + ")"; }
    @Override public boolean streamingEnabled() { return delegate.streamingEnabled(); }
    @Override public String backendDescription() { return delegate.backendDescription(); }
    @Override public void cancel(UUID id) { delegate.cancel(id); }
    @Override public void endSession(UUID id) { delegate.endSession(id); }
    @Override public void close() { delegate.close(); }
}
