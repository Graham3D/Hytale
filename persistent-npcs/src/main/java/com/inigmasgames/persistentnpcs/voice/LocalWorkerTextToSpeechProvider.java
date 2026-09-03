package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Existing cached-conditioning Chatterbox Turbo worker behind the R036 TTS contract. */
public final class LocalWorkerTextToSpeechProvider implements TextToSpeechProvider {
    private final VoiceRuntimeConfig config;
    private final VoicePresetRepository presets;
    private final Consumer<String> log;
    private volatile TurboVoiceWorker worker;
    private final ConcurrentHashMap<UUID, java.util.Set<UUID>> responseRequests =
            new ConcurrentHashMap<>();

    public LocalWorkerTextToSpeechProvider(VoiceRuntimeConfig config,
            VoicePresetRepository presets, Consumer<String> log) {
        this.config = config;
        this.presets = presets;
        this.log = log;
    }

    public CompletableFuture<com.google.gson.JsonObject> bootstrap() {
        return CompletableFuture.supplyAsync(this::worker)
                .thenCompose(TurboVoiceWorker::readiness);
    }

    @Override
    public CompletableFuture<OpusClip> synthesize(UUID requestId, UUID responseId,
            VoiceRenderPlan plan, String text) {
        responseRequests.computeIfAbsent(responseId, ignored -> ConcurrentHashMap.newKeySet())
                .add(requestId);
        return worker().synthesize(requestId, plan, text).whenComplete((result, failure) -> {
            java.util.Set<UUID> requests = responseRequests.get(responseId);
            if (requests != null) {
                requests.remove(requestId);
                if (requests.isEmpty()) responseRequests.remove(responseId, requests);
            }
        });
    }

    @Override public void cancel(UUID responseId) {
        java.util.Set<UUID> requests = responseRequests.remove(responseId);
        TurboVoiceWorker current = worker;
        if (requests != null && current != null) requests.forEach(current::cancel);
    }
    public CompletableFuture<Boolean> unloadResidentModel() {
        if (!responseRequests.isEmpty()) return CompletableFuture.completedFuture(false);
        TurboVoiceWorker current = worker;
        return current == null ? CompletableFuture.completedFuture(false)
                : current.unloadTtsModel();
    }

    public CompletableFuture<Boolean> ensureResident() {
        return worker().warmTtsModel();
    }
    public CompletableFuture<com.google.gson.JsonObject> ensureResident(
            java.util.List<java.nio.file.Path> references) {
        return worker().warmTtsModel(references);
    }
    @Override public String providerId() { return "chatterbox-turbo-local-worker"; }
    @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
    @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }
    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(true, true, true, Set.of("text", "opus"));
    }
    @Override public CompletableFuture<AiProviderHealth> health() {
        TurboVoiceWorker current = worker;
        if (current == null) return CompletableFuture.completedFuture(new AiProviderHealth(
                AiProviderHealth.Status.DEGRADED, "Chatterbox worker has not started yet",
                Instant.now()));
        TurboVoiceWorker.WorkerResidency runtime = current.runtimeResidency();
        if (runtime != null && "LOADING".equals(runtime.state()) && runtime.alive()) {
            return CompletableFuture.completedFuture(new AiProviderHealth(
                    AiProviderHealth.Status.DEGRADED,
                    "Chatterbox Turbo worker is loading; " + backendDescription(),
                    Instant.now()));
        }
        return current.readiness().handle((ready, failure) -> failure == null
                ? AiProviderHealth.healthy(backendDescription())
                : AiProviderHealth.unavailable(rootMessage(failure)));
    }
    @Override public String backendDescription() {
        TurboVoiceWorker current = worker;
        TurboVoiceWorker.WorkerResidency runtime = current == null ? null
                : current.runtimeResidency();
        return "Chatterbox Turbo external worker; device="
                + (runtime == null ? config.effectiveTtsDevice() : runtime.ttsDevice())
                + "; workerPid=" + (runtime == null ? "UNKNOWN" : runtime.pid())
                + "; cudaAllocatedMiB="
                + (runtime == null ? "UNKNOWN" : runtime.cudaAllocatedMiB())
                + "; cudaReservedMiB="
                + (runtime == null ? "UNKNOWN" : runtime.cudaReservedMiB())
                + "; modelResident=" + (runtime == null ? "UNKNOWN" : runtime.modelResident())
                + "; conditioningCacheEntries="
                + (runtime == null ? "UNKNOWN" : runtime.conditioningCacheEntries());
    }
    @Override public AiResourceRequirements resourceRequirements() {
        TurboVoiceWorker current = worker;
        TurboVoiceWorker.WorkerResidency runtime = current == null ? null
                : current.runtimeResidency();
        String device = (runtime == null ? config.effectiveTtsDevice()
                : runtime.ttsDevice()).toLowerCase(java.util.Locale.ROOT);
        ExecutionPlacement placement = device.contains("cuda") || device.contains("gpu")
                ? ExecutionPlacement.LOCAL_GPU : device.contains("cpu")
                        ? ExecutionPlacement.LOCAL_CPU : ExecutionPlacement.UNKNOWN;
        boolean gpuBudget = placement.usesLocalGpu() || device.equals("auto");
        long measured = runtime == null ? 0 : Math.max(runtime.cudaAllocatedMiB(),
                runtime.cudaReservedMiB());
        long resident = gpuBudget ? Math.max(runtime == null || !runtime.modelResident()
                ? 3_072 : 2_848, measured) : 0;
        // R055 target-host warm measurements: allocated peak grew by 113-118 MiB and
        // reserved memory by 58-92 MiB across representative short first phrases.
        // Keep these separate from the already-accounted resident model footprint.
        // R055 measured synthesis growth was 113-118 MiB allocated and 58-92 MiB
        // reserved. Keep explicit high-water allowance without double-counting residency.
        long incremental = gpuBudget ? 160 : 0;
        long temporary = gpuBudget ? 64 : 0;
        return new AiResourceRequirements(placement, backendDescription(), 3072,
                gpuBudget ? resident + incremental + temporary : 0,
                1, false, true, 1200,
                resident, incremental, temporary);
    }
    @Override public void close() {
        TurboVoiceWorker current = worker;
        if (current != null) current.close();
    }

    private TurboVoiceWorker worker() {
        TurboVoiceWorker current = worker;
        if (current != null) return current;
        synchronized (this) {
            if (worker == null) worker = new TurboVoiceWorker(
                    config, presets, log, TurboVoiceWorker.WorkerRole.TTS);
            return worker;
        }
    }

    private static String rootMessage(Throwable value) {
        Throwable current = value;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? "unknown" : current.getClass().getSimpleName() + ": "
                + current.getMessage();
    }
}
