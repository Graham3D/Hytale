package com.inigmasgames.persistentnpcs.llm;

/**
 * Exact provider/model captured for one Orbis NPC branch. The delegate is a
 * service handle, not persistent/trace data, and never follows later runtime
 * provider selection changes.
 */
public record PinnedLlmProvider(
        String provider,
        String model,
        String endpoint,
        LlmProvider delegate) {

    public PinnedLlmProvider {
        provider = provider == null || provider.isBlank() ? "unknown" : provider;
        model = model == null || model.isBlank() ? "unknown" : model;
        endpoint = endpoint == null ? "" : endpoint;
        java.util.Objects.requireNonNull(delegate, "delegate");
    }
}
