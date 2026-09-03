package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Existing Moonshine/Faster-Whisper worker adapted to the R036 provider contract. */
public final class LocalWorkerSpeechToTextProvider implements SpeechToTextProvider {
    private final VoiceRuntimeConfig config;
    private final VoicePresetRepository presets;
    private final Consumer<String> log;
    private volatile TurboVoiceWorker worker;
    private volatile CompletableFuture<com.google.gson.JsonObject> bootstrap;
    private volatile CompletableFuture<com.google.gson.JsonObject> warmup;

    public LocalWorkerSpeechToTextProvider(VoiceRuntimeConfig config,
            VoicePresetRepository presets, Consumer<String> log) {
        this.config = config;
        this.presets = presets;
        this.log = log;
    }

    /** Starts the external process and waits for its authoritative ready handshake. */
    public synchronized CompletableFuture<com.google.gson.JsonObject> bootstrapMoonshine() {
        if (bootstrap == null) bootstrap = CompletableFuture.supplyAsync(this::worker)
                .thenCompose(TurboVoiceWorker::readiness);
        return bootstrap;
    }

    /** Performs the model's real warm inference after the worker handshake. */
    public synchronized CompletableFuture<com.google.gson.JsonObject> warmMoonshine() {
        if (warmup == null) warmup = bootstrapMoonshine()
                .thenCompose(ignored -> worker().warmSttModel());
        return warmup;
    }

    @Override
    public CompletableFuture<SpeechTranscript> transcribe(
            UUID requestId, List<byte[]> opusFrames) {
        List<byte[]> preserved = immutableFrames(opusFrames);
        // The authoritative bounded capture remains in Java memory until startup completes.
        // A real Moonshine warmup failure is allowed to expose the worker's explicit fallback.
        return warmMoonshine().handle((ignored, failure) -> null)
                .thenCompose(ignored -> worker().transcribe(requestId, preserved));
    }

    @Override
    public boolean streamingTranscriptionEnabled() {
        TurboVoiceWorker current = worker;
        return current != null && current.streamingTranscriptionEnabled();
    }

    @Override public CompletableFuture<Void> startStream(UUID sessionId) {
        return worker().startStreamingTranscription(sessionId);
    }

    @Override public CompletableFuture<String> appendStream(
            UUID sessionId, List<byte[]> opusFrames) {
        return worker().appendStreamingTranscription(sessionId, immutableFrames(opusFrames));
    }

    @Override public CompletableFuture<SpeechTranscript> finishStream(UUID sessionId) {
        return worker().finishStreamingTranscription(sessionId);
    }

    @Override public void cancel(UUID requestOrSessionId) {
        TurboVoiceWorker current = worker;
        if (current != null) current.cancel(requestOrSessionId);
    }
    @Override public String providerId() { return "moonshine-faster-whisper-local-worker"; }
    @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
    @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }
    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(true, true, true, Set.of("opus", "text"));
    }
    @Override public CompletableFuture<AiProviderHealth> health() {
        TurboVoiceWorker current = worker;
        if (current == null) return CompletableFuture.completedFuture(new AiProviderHealth(
                AiProviderHealth.Status.DEGRADED, "Moonshine worker has not started yet",
                Instant.now()));
        TurboVoiceWorker.WorkerResidency runtime = current.runtimeResidency();
        if (runtime != null && "LOADING".equals(runtime.state()) && runtime.alive()) {
            return CompletableFuture.completedFuture(new AiProviderHealth(
                    AiProviderHealth.Status.DEGRADED,
                    "Local speech worker is loading; " + backendDescription(), Instant.now()));
        }
        return current.readiness().handle((ready, failure) -> failure == null
                ? AiProviderHealth.healthy(backendDescription())
                : AiProviderHealth.unavailable(rootMessage(failure)));
    }
    @Override public String backendDescription() {
        TurboVoiceWorker current = worker;
        TurboVoiceWorker.WorkerResidency runtime = current == null ? null
                : current.runtimeResidency();
        if (runtime == null) {
            return "requested=" + config.effectiveSttProvider()
                    + "; actual=LOADING; device=" + config.effectiveWhisperDevice()
                    + "; compute=" + config.effectiveWhisperComputeType();
        }
        return "requested=" + runtime.requestedSttProvider()
                + "; actual=" + runtime.sttProvider()
                + "; fallback=" + runtime.sttFallback()
                + (runtime.sttFallbackReason().isBlank() ? ""
                        : "; fallbackReason=" + runtime.sttFallbackReason())
                + "; device=" + runtime.sttDevice()
                + "; compute=" + runtime.sttComputeMode()
                + "; workerPid=" + runtime.pid();
    }
    @Override public AiResourceRequirements resourceRequirements() {
        TurboVoiceWorker current = worker;
        TurboVoiceWorker.WorkerResidency runtime = current == null ? null
                : current.runtimeResidency();
        String device = (runtime == null ? config.effectiveWhisperDevice()
                : runtime.sttDevice()).toLowerCase(java.util.Locale.ROOT);
        ExecutionPlacement placement = device.contains("cuda") || device.contains("gpu")
                ? ExecutionPlacement.LOCAL_GPU : device.contains("cpu")
                        ? ExecutionPlacement.LOCAL_CPU : ExecutionPlacement.UNKNOWN;
        return new AiResourceRequirements(placement, backendDescription(), 1024,
                placement.usesLocalGpu() ? 1024 : 0, 1, true, true, 500);
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
                    config, presets, log, TurboVoiceWorker.WorkerRole.STT);
            return worker;
        }
    }

    private static List<byte[]> immutableFrames(List<byte[]> frames) {
        return frames == null ? List.of() : frames.stream().map(byte[]::clone).toList();
    }
    private static String rootMessage(Throwable value) {
        Throwable current = value;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? "unknown" : current.getClass().getSimpleName() + ": "
                + current.getMessage();
    }
}
