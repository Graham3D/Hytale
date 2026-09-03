package com.inigmasgames.persistentnpcs.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Long-lived stdio bridge. Turbo and Whisper are loaded once by the child process. */
public final class TurboVoiceWorker implements AutoCloseable {
    private static final Map<Long, WorkerResidency> RUNTIME_RESIDENCY =
            new ConcurrentHashMap<>();
    /** Keeps CPU speech recognition independent from long-running GPU synthesis. */
    public enum WorkerRole {
        COMBINED("combined"), TTS("tts"), STT("stt");

        private final String argument;

        WorkerRole(String argument) { this.argument = argument; }
    }

    private final VoiceRuntimeConfig config;
    private final WorkerRole role;
    private final Consumer<String> log;
    private final Map<String, CompletableFuture<JsonObject>> pending =
            new ConcurrentHashMap<>();
    private final CompletableFuture<JsonObject> ready = new CompletableFuture<>();
    private volatile JsonObject readyState;
    private final AtomicBoolean moonshineStreaming = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object writeLock = new Object();
    private final Process process;
    private final BufferedWriter writer;

    public TurboVoiceWorker(
            VoiceRuntimeConfig config,
            VoicePresetRepository presets,
            Consumer<String> log) {
        this(config, presets, log, WorkerRole.COMBINED);
    }

    public TurboVoiceWorker(
            VoiceRuntimeConfig config,
            VoicePresetRepository presets,
            Consumer<String> log,
            WorkerRole role) {
        this.config = config;
        this.role = role == null ? WorkerRole.COMBINED : role;
        this.log = log == null ? ignored -> { } : log;
        try {
            Path python = resolvePython(config, presets.voicesDirectory());
            Path script = presets.workerScript();
            if (!Files.isRegularFile(script)) {
                throw new IllegalStateException("Voice worker script is missing: " + script);
            }
            List<String> command = new ArrayList<>(List.of(
                    python.toString(), "-u", script.toString(),
                    "--tts-device", config.effectiveTtsDevice(),
                    "--whisper-model", config.effectiveWhisperModel(),
                    "--whisper-device", config.effectiveWhisperDevice(),
                    "--whisper-compute-type", config.effectiveWhisperComputeType(),
                    "--stt-provider", config.effectiveSttProvider(),
                    "--moonshine-model", config.effectiveMoonshineModel(),
                    "--worker-role", this.role.argument,
                    "--opus-bitrate", Integer.toString(config.effectiveOpusBitrate())));
            command.add("--conditioning-cache-directory");
            command.add(presets.voicesDirectory().resolve("cache")
                    .resolve("conditioning-v1").toString());
            if (this.role != WorkerRole.STT) {
                VoicePreset maraPreset = presets.loadMaraPreset();
                presets.referenceAudio(maraPreset).filter(VoicePresetRepository::validWave)
                        .ifPresent(reference -> {
                    command.add("--prewarm-reference");
                    command.add(reference.toString());
                });
            }
            if (config.exportWav()) {
                command.add("--export-debug-wav");
                command.add("--export-directory");
                command.add(presets.voicesDirectory().resolve("debug").toString());
            }
            process = new ProcessBuilder(command).start();
            RUNTIME_RESIDENCY.entrySet().removeIf(entry -> entry.getValue().role() == this.role
                    && !ProcessHandle.of(entry.getKey()).map(ProcessHandle::isAlive)
                            .orElse(false));
            RUNTIME_RESIDENCY.put(process.pid(), new WorkerResidency(process.pid(), this.role,
                    "LOADING", true, Instant.now(), Instant.now(),
                    config.effectiveTtsDevice(), config.effectiveSttProvider()));
            writer = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            Thread.ofVirtual().name("immersive-voice-protocol").start(this::readProtocol);
            Thread.ofVirtual().name("immersive-voice-log").start(this::readDiagnostics);
            process.onExit().thenAccept(exited -> {
                updateResidency("UNLOADED", false);
                failAll(new IllegalStateException(
                        "Voice worker exited code=" + exited.exitValue()));
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Could not start persistent voice worker", failure);
        }
    }

    public CompletableFuture<JsonObject> readiness() {
        return ready.orTimeout(10, TimeUnit.MINUTES);
    }

    public CompletableFuture<OpusClip> synthesize(VoiceRenderPlan plan, String text) {
        return synthesize(UUID.randomUUID(), plan, text);
    }

    public CompletableFuture<Boolean> unloadTtsModel() {
        JsonObject request = baseRequest("unload_tts");
        return submitWhenReady(request).thenApply(response -> {
            updateResidency("UNLOADED", true, response);
            return booleanValue(response, "changed");
        });
    }

    public CompletableFuture<Boolean> warmTtsModel() {
        return warmTtsModel(List.of()).thenApply(response -> booleanValue(response, "changed"));
    }

    public CompletableFuture<JsonObject> warmTtsModel(List<Path> references) {
        JsonObject request = baseRequest("warm_tts");
        JsonArray values = new JsonArray();
        if (references != null) references.stream().filter(java.util.Objects::nonNull)
                .map(Path::toAbsolutePath).map(Path::normalize).distinct()
                .forEach(path -> values.add(path.toString()));
        request.add("references", values);
        return submitWhenReady(request).thenApply(response -> {
            updateResidency("LOADED", true, response);
            return response;
        });
    }

    public CompletableFuture<JsonObject> warmSttModel() {
        return submitWhenReady(baseRequest("warm_stt"));
    }

    public CompletableFuture<OpusClip> synthesize(
            UUID requestId, VoiceRenderPlan plan, String text) {
        JsonObject request = baseRequest(requestId, "synthesize");
        request.addProperty("queuedAtEpochMillis", System.currentTimeMillis());
        request.addProperty("text", text);
        request.addProperty("reference", plan.referenceAudio().orElseThrow(
                () -> new IllegalStateException("No valid voice reference available")).toString());
        request.addProperty("emotion", plan.vocalState().emotion().name());
        request.addProperty("paralinguisticEvent", plan.vocalState().paralinguisticEvent()
                .map(ParalinguisticEvent::tag).orElse(""));
        request.addProperty("npcId", plan.npcId().toString());
        request.addProperty("voicePresetId", plan.voicePresetId());
        request.addProperty("voiceSampleType", plan.resolvedSampleType().name());
        request.addProperty("voiceRevision", plan.voiceRevision());
        request.addProperty("gainDb", plan.outputGainDb());
        request.addProperty("projection", plan.projection().name());
        return submitWhenReady(request).thenApply(response -> {
            updateResidency("LOADED", true, response);
            log.accept("VOICE_PCM_LEVEL npc=" + plan.npcId() + " preset="
                    + plan.voicePresetId() + " sourcePeakDbfs="
                    + response.get("sourcePeakDbfs").getAsDouble() + " sourceRmsDbfs="
                    + response.get("sourceRmsDbfs").getAsDouble() + " gainDb="
                    + response.get("gainDb").getAsDouble() + " limiterReductionDb="
                    + response.get("limiterReductionDb").getAsDouble()
                    + " preOpusPeakDbfs="
                    + response.get("preOpusPeakDbfs").getAsDouble()
                    + " preOpusRmsDbfs="
                    + response.get("preOpusRmsDbfs").getAsDouble());
            log.accept("VOICE_TTS_PROFILE npc=" + plan.npcId()
                    + " conditioningMs=" + response.get("conditioningMs").getAsLong()
                    + " conditionalsCached="
                    + response.get("conditionalsCached").getAsBoolean());
            JsonArray encoded = response.getAsJsonArray("frames");
            List<byte[]> frames = new ArrayList<>(encoded.size());
            for (var value : encoded) {
                byte[] frame = Base64.getDecoder().decode(value.getAsString());
                if (frame.length < 1 || frame.length > 512) {
                    throw new IllegalStateException(
                            "Voice worker returned invalid Opus frame bytes=" + frame.length);
                }
                frames.add(frame);
            }
            return new OpusClip(frames, response.get("sourceRate").getAsInt(),
                    response.get("ttsMs").getAsLong(), response.get("encodeMs").getAsLong(),
                    response.get("conditioningMs").getAsLong(),
                    response.get("conditionalsCached").getAsBoolean(),
                    longValue(response, "workerQueueWaitMs"),
                    longValue(response, "cudaAllocatedMb"),
                    longValue(response, "cudaReservedMb"),
                    longValue(response, "cudaPeakAllocatedMb"),
                    longValue(response, "cudaPeakReservedMb"),
                    response.has("modelLoadCount")
                            ? response.get("modelLoadCount").getAsInt() : 1,
                    response.get("device").getAsString(),
                    Path.of(response.get("reference").getAsString()),
                    longValue(response, "workerPid"),
                    booleanValue(response, "modelResident"),
                    (int) longValue(response, "conditioningCacheEntries"));
        });
    }

    public CompletableFuture<SpeechTranscript> transcribe(List<byte[]> frames) {
        return transcribe(UUID.randomUUID(), frames);
    }

    public CompletableFuture<SpeechTranscript> transcribe(
            UUID requestId, List<byte[]> frames) {
        JsonObject request = baseRequest(requestId, "transcribe");
        JsonArray encoded = new JsonArray();
        for (byte[] frame : frames) {
            encoded.add(Base64.getEncoder().encodeToString(frame));
        }
        request.add("frames", encoded);
        return submitWhenReady(request).thenApply(response -> {
            updateResidency("LOADED", true, response);
            return new SpeechTranscript(
                response.get("text").getAsString(),
                response.get("decodeMs").getAsLong(),
                response.get("whisperMs").getAsLong(),
                text(response, "language"), text(response, "requestedEngine"),
                text(response, "actualEngine"), booleanValue(response, "fallback"),
                text(response, "fallbackReason"), text(response, "device"),
                text(response, "computeMode"), longValue(response, "workerPid"));
        });
    }

    public boolean streamingTranscriptionEnabled() {
        return moonshineStreaming.get();
    }

    public CompletableFuture<Void> startStreamingTranscription(UUID playerId) {
        JsonObject request = baseRequest("stt_stream_start");
        request.addProperty("streamId", playerId.toString());
        return submit(request).thenAccept(response -> { });
    }

    public CompletableFuture<String> appendStreamingTranscription(
            UUID playerId, List<byte[]> frames) {
        JsonObject request = baseRequest("stt_stream_audio");
        request.addProperty("streamId", playerId.toString());
        JsonArray encoded = new JsonArray();
        frames.forEach(frame -> encoded.add(Base64.getEncoder().encodeToString(frame)));
        request.add("frames", encoded);
        return submit(request).thenApply(response -> text(response, "partial"));
    }

    public CompletableFuture<SpeechTranscript> finishStreamingTranscription(UUID playerId) {
        JsonObject request = baseRequest("stt_stream_finish");
        request.addProperty("streamId", playerId.toString());
        return submit(request).thenApply(response -> new SpeechTranscript(
                text(response, "text"), response.get("decodeMs").getAsLong(),
                response.get("whisperMs").getAsLong(), text(response, "language"),
                text(response, "requestedEngine"), text(response, "actualEngine"),
                booleanValue(response, "fallback"), text(response, "fallbackReason"),
                text(response, "device"), text(response, "computeMode"),
                longValue(response, "workerPid")));
    }

    private CompletableFuture<JsonObject> submitWhenReady(JsonObject request) {
        return readiness().thenCompose(ignored -> submit(request));
    }

    private CompletableFuture<JsonObject> submit(JsonObject request) {
        String id = request.get("id").getAsString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            synchronized (writeLock) {
                writer.write(request.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException failure) {
            pending.remove(id);
            future.completeExceptionally(failure);
        }
        return future.orTimeout(5, TimeUnit.MINUTES);
    }

    private static JsonObject baseRequest(String operation) {
        return baseRequest(UUID.randomUUID(), operation);
    }

    private static JsonObject baseRequest(UUID requestId, String operation) {
        JsonObject request = new JsonObject();
        request.addProperty("id", requestId.toString());
        request.addProperty("op", operation);
        return request;
    }

    public void cancel(UUID requestId) {
        if (requestId == null) return;
        CompletableFuture<JsonObject> future = pending.remove(requestId.toString());
        if (future != null) future.cancel(true);
    }

    private static long longValue(JsonObject value, String key) {
        return value.has(key) ? value.get(key).getAsLong() : 0L;
    }

    private static boolean booleanValue(JsonObject value, String key) {
        return value.has(key) && value.get(key).getAsBoolean();
    }

    private void readProtocol() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject response = JsonParser.parseString(line).getAsJsonObject();
                String type = response.has("type") ? response.get("type").getAsString() : "";
                if ("ready".equals(type)) {
                    moonshineStreaming.set("MOONSHINE".equals(text(response, "sttProvider")));
                    readyState = response.deepCopy();
                    updateResidency("LOADED", true, response);
                    ready.complete(response);
                    log.accept("VOICE_WORKER_READY role=" + role.argument
                            + " ttsDevice=" + text(response, "ttsDevice")
                            + " torch=" + text(response, "torch")
                            + " cuda=" + text(response, "cuda")
                            + " gpu=" + text(response, "gpu")
                            + " sttProvider=" + text(response, "sttProvider")
                            + " ttsLoadMs=" + text(response, "ttsLoadMs")
                            + " voiceConditioningMs=" + text(response, "voiceConditioningMs")
                            + " whisperLoadMs=" + text(response, "whisperLoadMs"));
                    continue;
                }
                if ("fatal".equals(type)) {
                    updateResidency("FAILED", process.isAlive());
                    IllegalStateException failure = new IllegalStateException(
                            "Voice worker initialization failed: " + text(response, "error"));
                    ready.completeExceptionally(failure);
                    failAll(failure);
                    continue;
                }
                String id = text(response, "id");
                CompletableFuture<JsonObject> future = pending.remove(id);
                if (future == null) {
                    log.accept("VOICE_WORKER unexpected response id=" + id);
                } else if (response.has("ok") && response.get("ok").getAsBoolean()) {
                    future.complete(response);
                } else {
                    future.completeExceptionally(new IllegalStateException(
                            "Voice worker request failed: " + text(response, "error")));
                }
            }
        } catch (Exception failure) {
            if (process.isAlive()) {
                failAll(failure);
            }
        }
    }

    private void readDiagnostics() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    log.accept("VOICE_WORKER " + line.strip());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void failAll(Throwable failure) {
        ready.completeExceptionally(failure);
        pending.values().forEach(future -> future.completeExceptionally(failure));
        pending.clear();
    }

    private void updateResidency(String state, boolean alive) {
        updateResidency(state, alive, readyState);
    }

    private void updateResidency(String state, boolean alive, JsonObject metrics) {
        long reportedWorkerPid = longMetric(metrics, "workerPid");
        long authoritativeWorkerPid = reportedWorkerPid > 0
                ? reportedWorkerPid : process.pid();
        RUNTIME_RESIDENCY.compute(process.pid(), (ignored, prior) -> new WorkerResidency(
                authoritativeWorkerPid, role, state, alive,
                prior == null ? Instant.now() : prior.startedAt(), Instant.now(),
                metricText(metrics, "ttsDevice",
                        metricText(metrics, "device", config.effectiveTtsDevice())),
                metricText(metrics, "actualSttEngine",
                        metricText(metrics, "actualEngine", config.effectiveSttProvider())),
                metricText(metrics, "requestedSttEngine",
                        metricText(metrics, "requestedEngine", config.effectiveSttProvider())),
                booleanMetric(metrics, "sttFallback")
                        || booleanMetric(metrics, "fallback"),
                metricText(metrics, "sttFallbackReason",
                        metricText(metrics, "fallbackReason", "")),
                metricText(metrics, "sttDevice",
                        metricText(metrics, "device", config.effectiveWhisperDevice())),
                metricText(metrics, "sttComputeMode",
                        metricText(metrics, "computeMode",
                                config.effectiveWhisperComputeType())),
                longMetric(metrics, "cudaAllocatedMb"),
                longMetric(metrics, "cudaReservedMb"),
                longMetric(metrics, "cudaPeakAllocatedMb"),
                longMetric(metrics, "cudaPeakReservedMb"),
                booleanMetric(metrics, "ttsModelResident")
                        || booleanMetric(metrics, "modelResident"),
                (int) longMetric(metrics, "conditioningCacheEntries")));
    }

    public WorkerResidency runtimeResidency() {
        return RUNTIME_RESIDENCY.get(process.pid());
    }

    private static String metricText(JsonObject value, String key, String fallback) {
        String result = value == null ? "" : text(value, key);
        return result.isBlank() ? (fallback == null ? "" : fallback) : result;
    }

    private static long longMetric(JsonObject value, String key) {
        return value == null ? 0 : longValue(value, key);
    }

    private static boolean booleanMetric(JsonObject value, String key) {
        return value != null && booleanValue(value, key);
    }

    /** Cached diagnostics only; never starts, stops, or probes a worker. */
    public static List<WorkerResidency> runtimeResidencies() {
        Map<WorkerRole, WorkerResidency> latest = new java.util.EnumMap<>(WorkerRole.class);
        RUNTIME_RESIDENCY.values().forEach(value -> latest.merge(value.role(), value,
                (left, right) -> left.startedAt().isAfter(right.startedAt()) ? left : right));
        return List.copyOf(latest.values());
    }

    private static String text(JsonObject object, String property) {
        return object.has(property) && !object.get(property).isJsonNull()
                ? object.get(property).getAsString() : "";
    }

    private static Path resolvePython(VoiceRuntimeConfig config, Path voices) {
        if (config.pythonExecutable() != null && !config.pythonExecutable().isBlank()) {
            Path configured = Path.of(config.pythonExecutable()).toAbsolutePath().normalize();
            if (Files.isRegularFile(configured)) {
                return configured;
            }
            throw new IllegalStateException("Configured voice Python does not exist: " + configured);
        }
        List<Path> candidates = List.of(
                voices.resolve(".venv-turbo/Scripts/python.exe"),
                voices.resolve(".venv/Scripts/python.exe"));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElseThrow(
                () -> new IllegalStateException("No local voice Python found under " + voices));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (process.isAlive()) {
            try {
                JsonObject request = baseRequest("shutdown");
                submit(request).get(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // The bounded termination sequence below handles all failure modes.
            }
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(2, TimeUnit.SECONDS);
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        updateResidency("UNLOADED", false);
        try {
            writer.close();
        } catch (IOException ignored) {
            // Process termination already closed the pipe in the common case.
        }
        failAll(new IllegalStateException("Voice worker closed"));
    }

    public record WorkerResidency(long pid, WorkerRole role, String state, boolean alive,
            Instant startedAt, Instant changedAt, String ttsDevice, String sttProvider,
            String requestedSttProvider, boolean sttFallback, String sttFallbackReason,
            String sttDevice, String sttComputeMode, long cudaAllocatedMiB,
            long cudaReservedMiB, long cudaPeakAllocatedMiB, long cudaPeakReservedMiB,
            boolean modelResident, int conditioningCacheEntries) {
        public WorkerResidency(long pid, WorkerRole role, String state, boolean alive,
                Instant startedAt, Instant changedAt, String ttsDevice, String sttProvider) {
            this(pid, role, state, alive, startedAt, changedAt, ttsDevice, sttProvider,
                    sttProvider, false, "", "UNKNOWN", "UNKNOWN", 0, 0, 0, 0,
                    false, 0);
        }
    }
}
