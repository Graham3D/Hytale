package com.inigmasgames.persistentnpcs.llm.orbisllm;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmReasoningTelemetry;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmRuntimeDiagnosticSource;
import com.inigmasgames.persistentnpcs.llm.LlmUsage;
import com.inigmasgames.persistentnpcs.llm.ManagedLlmResidency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Experimental Phase 1 provider adapter; cognition contracts remain entirely in Java. */
public final class OrbisLlamaCppProvider implements LlmProvider,
        LlmRuntimeDiagnosticSource, ManagedLlmResidency {
    public static final String ID = "ORBIS_LLAMA_CPP_NEMOTRON";
    public static final String MODEL = "nvidia-nemotron-3-nano-4b-q4_k_m";
    private final Path manifestPath;
    private final OrbisLlmProcessManager manager;

    public OrbisLlamaCppProvider(Path dataDirectory, Path manifestPath,
            Consumer<String> log) {
        this.manifestPath = manifestPath.toAbsolutePath().normalize();
        manager = new OrbisLlmProcessManager(dataDirectory, this.manifestPath, log);
    }

    @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        return generateResponse(request, ignored -> { });
    }

    @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request,
            Consumer<String> tokenConsumer) {
        boolean thinking = request.executionPolicy().thinkingEnabled();
        return manager.generate(request, tokenConsumer).thenApply(result -> new LlmResult(
                result.text(), new LlmLatency(result.startedAt(), result.ttftMillis(),
                        result.completionMillis(), request.responseFormat() == null),
                List.of(), result.finishReason(),
                new LlmUsage(result.promptTokens(), result.completionTokens(),
                        result.promptTokens() + result.completionTokens(), true),
                new LlmReasoningTelemetry(
                        request.executionPolicy().requestedReasoningMode().name(),
                        thinking ? "ENABLED" : "DISABLED", thinking,
                        result.reasoningEvents(), result.reasoningTokens(),
                        result.finalAnswerTokens(), true, result.promptEvaluationMillis())));
    }

    @Override public void cancel(UUID id) { manager.cancel(id, "ORBIS_CANCEL"); }
    @Override public void endSession(UUID id) { manager.cancel(id, "SESSION_ENDED"); }
    @Override public CompletableFuture<Void> warmUp() { return manager.ensureReady(); }
    @Override public CompletableFuture<Void> ensureResident() { return manager.ensureReady(); }
    @Override public CompletableFuture<Boolean> unloadResident() { return manager.unload(); }
    @Override public boolean residencyPrepared() { return manager.resident(); }
    @Override public boolean streamingEnabled() { return true; }
    @Override public String providerId() { return ID; }
    @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }
    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(true, true, true, Set.of("text", "json", "gbnf"));
    }
    @Override public int concurrencyLimit() { return 1; }
    @Override public String backendDescription() {
        return "OrbisLLM pinned llama.cpp b10701 model=" + MODEL
                + " gpuLayers=4 state=" + manager.state();
    }
    @Override public AiResourceRequirements resourceRequirements() {
        return new AiResourceRequirements(ExecutionPlacement.LOCAL_PARTIAL_GPU,
                backendDescription(), 6144, 4096, 1, true, true, 1200,
                880, 256, 128);
    }
    @Override public JsonObject runtimeDiagnostics(UUID npcId) {
        JsonObject value = manager.diagnostics();
        value.addProperty("provider", ID);
        value.addProperty("model", MODEL);
        value.addProperty("npcId", npcId == null ? "" : npcId.toString());
        return value;
    }
    @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
        if (manager.manifestVerified() && manager.state() != OrbisLlmProcessManager.State.CRASHED
                && manager.state() != OrbisLlmProcessManager.State.DEGRADED) {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    "orbisllm://local", MODEL, true, true, true,
                    manager.resident() ? "Pinned configured model is available; runtime ready"
                            : "Pinned configured model is available; manifest verified"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                OrbisLlmRuntimeManifest.loadVerified(manifestPath);
                return new LlmProviderStatus("orbisllm://local", MODEL, true, true,
                        true, "Pinned configured model is available; runtime manifest verified");
            } catch (RuntimeException failure) {
                return new LlmProviderStatus("orbisllm://local", MODEL,
                        Files.isRegularFile(manifestPath), false, true,
                        "Pinned runtime unavailable: " + failure.getMessage());
            }
        });
    }
    @Override public CompletableFuture<AiProviderHealth> health() {
        return checkStatus().thenApply(status -> status.reachable()
                ? AiProviderHealth.healthy(status.reason())
                : AiProviderHealth.unavailable(status.reason()));
    }
    @Override public boolean available() { return Files.isRegularFile(manifestPath); }
    @Override public void close() { manager.close(); }
}
