package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.UUID;

public interface LanguageModelProvider extends AiProvider {
    CompletableFuture<LlmResult> generateResponse(LlmRequest request);

    default CompletableFuture<LlmResult> stream(
            LlmRequest request, Consumer<String> tokenConsumer) {
        return generateResponse(request);
    }

    default CompletableFuture<LlmResult> generateResponse(
            LlmRequest request, Consumer<String> tokenConsumer) {
        return stream(request, tokenConsumer);
    }

    default boolean streamingEnabled() { return capabilities().streaming(); }

    default CompletableFuture<Void> warmUp() {
        return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<LlmProviderStatus> checkStatus();

    default String description() { return backendDescription(); }

    /** Ends sticky routing and propagates cancellation for one conversation session. */
    default void endSession(UUID sessionId) { cancel(sessionId); }
}
