package com.inigmasgames.persistentnpcs.ai;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Hardware-neutral inference boundary. Core game state never crosses this contract. */
public interface AiProvider extends AutoCloseable {
    String providerId();

    AiServiceKind serviceKind();

    ProviderExecutionMode executionMode();

    AiProviderCapabilities capabilities();

    default CompletableFuture<AiProviderHealth> health() {
        return CompletableFuture.completedFuture(AiProviderHealth.healthy(
                backendDescription()));
    }

    default AiProviderMetrics metrics() { return AiProviderMetrics.empty(1); }

    default int concurrencyLimit() { return metrics().concurrencyLimit(); }

    default AiResourceRequirements resourceRequirements() {
        return new AiResourceRequirements(
                executionMode() == ProviderExecutionMode.REMOTE
                        ? ExecutionPlacement.REMOTE_CLOUD : ExecutionPlacement.UNKNOWN,
                backendDescription(), 0, 0, concurrencyLimit(), capabilities().streaming(),
                capabilities().cancellation(), 0);
    }

    default String backendDescription() { return providerId(); }

    default void cancel(UUID requestOrSessionId) { }

    default boolean available() { return true; }

    @Override
    default void close() { }
}
