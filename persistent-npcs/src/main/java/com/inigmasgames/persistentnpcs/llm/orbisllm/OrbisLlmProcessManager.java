package com.inigmasgames.persistentnpcs.llm.orbisllm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Orbis-owned lifecycle, request, cancellation, crash recovery, and telemetry authority. */
public final class OrbisLlmProcessManager implements AutoCloseable {
    public enum State { STOPPED, VERIFYING, STARTING, LOADING, READY,
        GENERATING, CANCELLING, CRASHED, DEGRADED, CLOSED }

    private final Path dataDirectory;
    private final Path manifestPath;
    private final Consumer<String> log;
    private final ExecutorService tasks = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            task -> Thread.ofPlatform().daemon().name("orbisllm-watchdog").unstarted(task));
    private final Semaphore decode = new Semaphore(1, true);
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
    private final AtomicReference<CompletableFuture<Void>> readiness = new AtomicReference<>();
    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Generation> generations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Generation> aliases = new ConcurrentHashMap<>();
    private final AtomicLong processEpoch = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile OrbisLlmRuntimeManifest.Loaded runtime;
    private volatile OrbisLlmIpcClient ipc;
    private volatile Process process;
    private volatile JsonObject latestStatus = new JsonObject();
    private volatile JsonObject latestResources = new JsonObject();
    private volatile JsonObject latestTiming = new JsonObject();
    private volatile String processGeneration = "UNKNOWN";
    private volatile long lastStartedAtMillis;
    private final java.util.ArrayDeque<Long> crashes = new java.util.ArrayDeque<>();

    public OrbisLlmProcessManager(Path dataDirectory, Path manifestPath,
            Consumer<String> log) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.manifestPath = manifestPath.toAbsolutePath().normalize();
        this.log = log == null ? ignored -> { } : log;
    }

    public CompletableFuture<Void> ensureReady() {
        if (closed.get()) return CompletableFuture.failedFuture(
                new IllegalStateException("OrbisLLM runtime is closed"));
        if (state.get() == State.READY) return CompletableFuture.completedFuture(null);
        while (true) {
            CompletableFuture<Void> existing = readiness.get();
            if (existing != null && !existing.isDone()
                    && !existing.isCompletedExceptionally()
                    && !existing.isCancelled()) return existing;
            CompletableFuture<Void> created = CompletableFuture.runAsync(this::startAndWarm, tasks);
            if (readiness.compareAndSet(existing, created)) return created;
        }
    }

    public CompletableFuture<GenerationResult> generate(LlmRequest request,
            Consumer<String> finalTokens) {
        UUID requestId = request.providerRequestId();
        Generation generation = new Generation(requestId, request.conversationId(),
                request.npcId(), finalTokens == null ? ignored -> { } : finalTokens,
                Instant.now());
        if (generations.putIfAbsent(requestId, generation) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Duplicate OrbisLLM provider request " + requestId));
        }
        aliases.put(requestId, generation);
        aliases.put(request.conversationId(), generation);
        return ensureReady().thenCompose(ignored -> CompletableFuture.supplyAsync(() -> {
            boolean acquired = false;
            try {
                decode.acquire();
                acquired = true;
                if (generation.cancelled.get()) throw new java.util.concurrent.CancellationException();
                state.set(State.GENERATING);
                generation.dispatchedAtNanos = System.nanoTime();
                send(OrbisLlmProtocol.Type.GENERATE, requestId, generationPayload(request));
                long hardDeadline = request.turnExecutionPlan() == null ? 60_000
                        : request.turnExecutionPlan().deadlines().providerHardMillis();
                watchdog.schedule(() -> {
                    if (!generation.terminal.get()) cancel(requestId, "PROVIDER_HARD_TIMEOUT");
                }, Math.max(100, hardDeadline), TimeUnit.MILLISECONDS);
                try {
                    return generation.result.join();
                } catch (java.util.concurrent.CancellationException cancelled) {
                    // Outward cancellation is immediate, but the single native decode permit
                    // is not released until the sidecar proves it has drained and cleared KV.
                    generation.drained.join();
                    throw cancelled;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(interrupted);
            } finally {
                if (acquired) decode.release();
                aliases.remove(requestId, generation);
                aliases.remove(request.conversationId(), generation);
                generations.remove(requestId, generation);
                if (!closed.get() && state.get() != State.CRASHED) state.set(State.READY);
            }
        }, tasks));
    }

    public void cancel(UUID id, String reason) {
        Generation generation = aliases.get(id);
        if (generation == null || generation.terminal.get()) return;
        if (!generation.cancelled.compareAndSet(false, true)) return;
        state.set(State.CANCELLING);
        generation.result.cancel(false);
        JsonObject body = new JsonObject();
        body.addProperty("requestId", generation.requestId.toString());
        body.addProperty("reason", reason == null ? "ORBIS_CANCEL" : reason);
        try { send(OrbisLlmProtocol.Type.CANCEL, generation.requestId, body); }
        catch (RuntimeException failure) { crash(failure); return; }
        log.accept("ORBIS_LLAMA_CANCEL_REQUESTED request=" + generation.requestId);
        watchdog.schedule(() -> {
            if (!generation.drained.isDone()) {
                log.accept("ORBIS_LLAMA_CANCEL_HARD_TIMEOUT request=" + generation.requestId);
                crash(new IllegalStateException("OrbisLLM cancellation did not drain within 2s"));
            }
        }, 2, TimeUnit.SECONDS);
    }

    public CompletableFuture<Boolean> unload() {
        if (state.get() == State.STOPPED || state.get() == State.CLOSED) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            decode.acquireUninterruptibly();
            try {
                command(OrbisLlmProtocol.Type.UNLOAD_MODEL, OrbisLlmProtocol.Type.READY,
                        new JsonObject(), Duration.ofSeconds(10)).join();
                state.set(State.STARTING);
                readiness.set(null);
                return true;
            } finally { decode.release(); }
        }, tasks);
    }

    public CompletableFuture<JsonObject> status() {
        if (state.get() == State.STOPPED || state.get() == State.CRASHED) {
            return CompletableFuture.completedFuture(latestStatus.deepCopy());
        }
        return command(OrbisLlmProtocol.Type.GET_STATUS, OrbisLlmProtocol.Type.STATUS,
                new JsonObject(), Duration.ofSeconds(2));
    }

    public JsonObject diagnostics() {
        JsonObject value = latestStatus.deepCopy();
        value.addProperty("orbisLlmState", state.get().name());
        value.addProperty("processGeneration", processGeneration);
        value.addProperty("processEpoch", processEpoch.get());
        value.addProperty("pid", process == null ? -1 : process.pid());
        value.addProperty("activeRequests", generations.size());
        value.addProperty("manifestPath", manifestPath.toString());
        value.addProperty("lastStartedAtMillis", lastStartedAtMillis);
        value.add("resources", latestResources.deepCopy());
        value.add("latestTiming", latestTiming.deepCopy());
        return value;
    }

    public State state() { return state.get(); }
    public boolean manifestVerified() { return runtime != null; }
    public boolean resident() { return state.get() == State.READY
            || state.get() == State.GENERATING || state.get() == State.CANCELLING; }

    private void startAndWarm() {
        enforceCircuitBreaker();
        cleanupProcess();
        try {
            state.set(State.VERIFYING);
            runtime = OrbisLlmRuntimeManifest.loadVerified(manifestPath);
            OrbisLlmRuntimeManifest value = runtime.manifest();
            String pipeName = "orbisllm-" + UUID.randomUUID().toString().replace("-", "");
            String nonce = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            String instance = Integer.toHexString(dataDirectory.toString().toLowerCase().hashCode());
            Path logs = dataDirectory.resolve("logs");
            Files.createDirectories(logs);
            Path runtimeLog = logs.resolve("orbisllm-runtime.log");
            List<String> arguments = new ArrayList<>();
            arguments.add(value.executablePath());
            java.util.Collections.addAll(arguments,
                    "--pipe", pipeName, "--nonce", nonce,
                    "--manifest-hash", runtime.sha256(),
                    "--runtime-dir", value.runtimeDirectory(),
                    "--approved-model-path", value.model().path(),
                    "--approved-model-sha256", value.model().sha256(),
                    "--instance-id", instance,
                    "--parent-pid", Long.toString(ProcessHandle.current().pid()));
            state.set(State.STARTING);
            process = new ProcessBuilder(arguments).redirectOutput(
                    ProcessBuilder.Redirect.appendTo(runtimeLog.toFile())).redirectError(
                    ProcessBuilder.Redirect.appendTo(runtimeLog.toFile())).start();
            lastStartedAtMillis = System.currentTimeMillis();
            long epoch = processEpoch.incrementAndGet();
            process.onExit().thenAcceptAsync(exited -> {
                if (!closed.get() && process == exited && state.get() != State.STOPPED) {
                    crash(new IllegalStateException("OrbisLLM exited code=" + exited.exitValue()
                            + " epoch=" + epoch));
                }
            }, tasks);
            ipc = OrbisLlmIpcClient.connect(pipeName, Duration.ofSeconds(10),
                    this::onFrame, this::crash);
            JsonObject hello = new JsonObject();
            hello.addProperty("nonce", nonce);
            hello.addProperty("runtimeManifestHash", runtime.sha256());
            hello.addProperty("protocolMajor", OrbisLlmProtocol.MAJOR);
            hello.addProperty("protocolMinor", OrbisLlmProtocol.MINOR);
            command(OrbisLlmProtocol.Type.HELLO, OrbisLlmProtocol.Type.HELLO_ACK,
                    hello, Duration.ofSeconds(5)).join();
            state.set(State.LOADING);
            JsonObject load = new JsonObject();
            load.addProperty("modelId", value.model().id());
            load.addProperty("modelPath", value.model().path());
            load.addProperty("modelSha256", value.model().sha256());
            load.addProperty("gpuLayers", value.profiles().get("BALANCED").gpuLayers());
            command(OrbisLlmProtocol.Type.LOAD_MODEL, OrbisLlmProtocol.Type.READY,
                    load, Duration.ofSeconds(90)).join();
            OrbisLlmRuntimeManifest.Profile profile = value.profiles().get("BALANCED");
            JsonObject context = new JsonObject();
            context.addProperty("contextSize", profile.contextSize());
            context.addProperty("batchSize", profile.batchSize());
            context.addProperty("microbatchSize", profile.microbatchSize());
            context.addProperty("threads", profile.threads());
            command(OrbisLlmProtocol.Type.CREATE_CONTEXT, OrbisLlmProtocol.Type.READY,
                    context, Duration.ofSeconds(30)).join();
            state.set(State.READY);
            log.accept("ORBIS_LLAMA_READY pid=" + process.pid() + " epoch=" + epoch
                    + " model=" + value.model().id() + " gpuLayers=" + profile.gpuLayers());
        } catch (Throwable failure) {
            state.set(State.DEGRADED);
            cleanupProcess();
            throw failure instanceof RuntimeException runtimeFailure ? runtimeFailure
                    : new IllegalStateException("OrbisLLM startup failed", failure);
        }
    }

    private JsonObject generationPayload(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", request.providerRequestId().toString());
        body.addProperty("turnId", request.conversationId().toString());
        UUID response = request.turnExecutionPlan() == null ? request.providerRequestId()
                : request.turnExecutionPlan().responseId();
        body.addProperty("responseId", response.toString());
        body.addProperty("branchEpoch", request.turnExecutionPlan() == null ? 0
                : request.turnExecutionPlan().branchEpoch());
        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.canonicalMessages()) {
            JsonObject item = new JsonObject();
            item.addProperty("role", message.role());
            item.addProperty("content", message.content() == null ? "" : message.content());
            messages.add(item);
        }
        body.add("messages", messages);
        boolean structured = request.responseFormat() != null;
        body.addProperty("structured", structured);
        body.addProperty("outputContractId", structured ? "npc-decision-v1" : "dialogue-text-v1");
        body.addProperty("reasoningMode", request.executionPolicy().thinkingEnabled()
                ? "ENABLED" : "DISABLED");
        int budget = request.maxTokensOverride() == null ? 80 : request.maxTokensOverride();
        if (request.executionPolicy().finalAnswerTokenBudget() > 0) {
            budget = Math.min(budget, request.executionPolicy().finalAnswerTokenBudget());
        }
        body.addProperty("maxTokens", Math.max(1, budget));
        body.addProperty("temperature", request.temperatureOverride() == null
                ? 0.3 : request.temperatureOverride());
        body.addProperty("topP", 1.0);
        body.addProperty("topK", 40);
        return body;
    }

    private CompletableFuture<JsonObject> command(OrbisLlmProtocol.Type command,
            OrbisLlmProtocol.Type expected, JsonObject body, Duration timeout) {
        UUID id = UUID.randomUUID();
        Pending waiting = new Pending(expected);
        pending.put(id, waiting);
        try { send(command, id, body); }
        catch (RuntimeException failure) {
            pending.remove(id);
            waiting.result.completeExceptionally(failure);
        }
        watchdog.schedule(() -> {
            if (pending.remove(id, waiting)) waiting.result.completeExceptionally(
                    new IllegalStateException("OrbisLLM " + command + " timed out"));
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
        return waiting.result;
    }

    private void send(OrbisLlmProtocol.Type type, UUID id, JsonObject body) {
        try {
            OrbisLlmIpcClient connection = ipc;
            if (connection == null) throw new IOException("OrbisLLM IPC is unavailable");
            connection.send(type, id, body);
        } catch (IOException failure) {
            throw new IllegalStateException("OrbisLLM IPC write failed", failure);
        }
    }

    private void onFrame(OrbisLlmProtocol.Frame frame) {
        JsonObject body = frame.body();
        if (body.has("processGeneration")) {
            String generation = body.get("processGeneration").getAsString();
            if (!"UNKNOWN".equals(processGeneration) && !processGeneration.equals(generation)) {
                crash(new IllegalStateException("Stale OrbisLLM process generation"));
                return;
            }
            processGeneration = generation;
        }
        if (frame.type() == OrbisLlmProtocol.Type.RESOURCE_SNAPSHOT) {
            latestResources = body.deepCopy();
            return;
        }
        if (frame.type() == OrbisLlmProtocol.Type.STATUS
                || frame.type() == OrbisLlmProtocol.Type.READY
                || frame.type() == OrbisLlmProtocol.Type.HELLO_ACK) {
            latestStatus = body.deepCopy();
        }
        Pending command = pending.get(frame.requestId());
        if (command != null) {
            if (frame.type() == OrbisLlmProtocol.Type.ERROR) {
                pending.remove(frame.requestId(), command);
                command.result.completeExceptionally(remoteFailure(body));
            } else if (frame.type() == command.expected) {
                pending.remove(frame.requestId(), command);
                command.result.complete(body.deepCopy());
            }
            return;
        }
        Generation generation = generations.get(frame.requestId());
        if (generation == null) return;
        switch (frame.type()) {
            case REQUEST_ACCEPTED -> generation.acceptedAtNanos = System.nanoTime();
            case REASONING_DELTA -> generation.reasoningEvents++;
            case FINAL_DELTA -> {
                if (!generation.cancelled.get() && !generation.terminal.get()) {
                    if (generation.firstDeltaAtNanos == 0) {
                        generation.firstDeltaAtNanos = System.nanoTime();
                    }
                    String token = string(body, "text");
                    if (!token.isEmpty()) generation.finalTokens.accept(token);
                }
            }
            case CONTRACT_COMPLETE -> generation.contractText = string(body, "text");
            case REQUEST_COMPLETE -> {
                if (generation.terminal.compareAndSet(false, true)) {
                    long completedAt = System.nanoTime();
                    JsonObject timing = body.deepCopy();
                    timing.addProperty("javaDispatchToAcceptedMillis",
                            nanosBetween(generation.dispatchedAtNanos,
                                    generation.acceptedAtNanos));
                    timing.addProperty("javaObservedTtftMillis",
                            nanosBetween(generation.dispatchedAtNanos,
                                    generation.firstDeltaAtNanos));
                    timing.addProperty("javaObservedCompletionMillis",
                            nanosBetween(generation.dispatchedAtNanos, completedAt));
                    long nativeTtft = number(body, "ttftMillis", -1);
                    long javaTtft = number(timing, "javaObservedTtftMillis", -1);
                    timing.addProperty("ipcFirstTokenReturnMillis",
                            nativeTtft < 0 || javaTtft < 0 ? -1
                                    : Math.max(0, javaTtft - nativeTtft));
                    latestTiming = timing;
                    generation.drained.complete(null);
                    generation.result.complete(GenerationResult.from(body,
                            generation.startedAt, generation.reasoningEvents));
                }
            }
            case CANCEL_ACK -> {
                generation.terminal.set(true);
                generation.drained.complete(null);
                generation.result.cancel(false);
                log.accept("ORBIS_LLAMA_CANCEL_ACK request=" + generation.requestId
                        + " stage=" + string(body, "stage"));
            }
            case ERROR -> {
                if (generation.terminal.compareAndSet(false, true)) {
                    generation.drained.complete(null);
                    generation.result.completeExceptionally(remoteFailure(body));
                }
            }
            default -> { }
        }
    }

    private static RuntimeException remoteFailure(JsonObject body) {
        return new IllegalStateException("OrbisLLM " + string(body, "category")
                + ": " + string(body, "detail"));
    }

    private synchronized void crash(Throwable failure) {
        if (closed.get() || state.get() == State.CRASHED) return;
        state.set(State.CRASHED);
        long now = System.currentTimeMillis();
        crashes.addLast(now);
        while (!crashes.isEmpty() && now - crashes.peekFirst() > 60_000) crashes.removeFirst();
        IllegalStateException explicit = new IllegalStateException(
                "OrbisLLM sidecar crashed; current request failed and no fallback was used",
                failure);
        pending.values().forEach(value -> value.result.completeExceptionally(explicit));
        pending.clear();
        generations.values().forEach(value -> {
            value.terminal.set(true);
            value.drained.complete(null);
            value.result.completeExceptionally(explicit);
        });
        readiness.set(null);
        cleanupProcess();
        log.accept("ORBIS_LLAMA_CRASH reason=" + failure.getMessage());
    }

    private synchronized void enforceCircuitBreaker() {
        long now = System.currentTimeMillis();
        while (!crashes.isEmpty() && now - crashes.peekFirst() > 60_000) crashes.removeFirst();
        if (crashes.size() >= 3) throw new IllegalStateException(
                "OrbisLLM circuit breaker open after 3 crashes in 60 seconds");
    }

    private synchronized void cleanupProcess() {
        OrbisLlmIpcClient connection = ipc;
        ipc = null;
        if (connection != null) connection.close();
        Process running = process;
        process = null;
        if (running != null && running.isAlive()) {
            running.destroy();
            try {
                if (!running.waitFor(500, TimeUnit.MILLISECONDS)) running.destroyForcibly();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.destroyForcibly();
            }
        }
        processGeneration = "UNKNOWN";
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            if (ipc != null && process != null && process.isAlive()) {
                command(OrbisLlmProtocol.Type.SHUTDOWN, OrbisLlmProtocol.Type.SHUTDOWN_ACK,
                        new JsonObject(), Duration.ofSeconds(2)).get(2, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) { }
        java.util.concurrent.CancellationException shutdown =
                new java.util.concurrent.CancellationException("OrbisLLM runtime shutdown");
        pending.values().forEach(value -> value.result.completeExceptionally(shutdown));
        pending.clear();
        generations.values().forEach(value -> {
            value.cancelled.set(true);
            value.terminal.set(true);
            value.drained.complete(null);
            value.result.cancel(false);
        });
        generations.clear();
        aliases.clear();
        cleanupProcess();
        state.set(State.CLOSED);
        tasks.shutdownNow();
        watchdog.shutdownNow();
    }

    private static String string(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : "";
    }
    private static long number(JsonObject value, String key, long fallback) {
        return value.has(key) ? value.get(key).getAsLong() : fallback;
    }
    private static long nanosBetween(long started, long ended) {
        return started <= 0 || ended <= 0 || ended < started ? -1
                : TimeUnit.NANOSECONDS.toMillis(ended - started);
    }

    private record Pending(OrbisLlmProtocol.Type expected,
            CompletableFuture<JsonObject> result) {
        private Pending(OrbisLlmProtocol.Type expected) {
            this(expected, new CompletableFuture<>());
        }
    }

    private static final class Generation {
        private final UUID requestId;
        private final UUID conversationId;
        private final UUID npcId;
        private final Consumer<String> finalTokens;
        private final Instant startedAt;
        private final CompletableFuture<GenerationResult> result = new CompletableFuture<>();
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private volatile long dispatchedAtNanos;
        private volatile long acceptedAtNanos;
        private volatile long firstDeltaAtNanos;
        private volatile int reasoningEvents;
        private volatile String contractText = "";

        private Generation(UUID requestId, UUID conversationId, UUID npcId,
                Consumer<String> finalTokens, Instant startedAt) {
            this.requestId = requestId;
            this.conversationId = conversationId;
            this.npcId = npcId;
            this.finalTokens = finalTokens;
            this.startedAt = startedAt;
        }
    }

    public record GenerationResult(String text, String finishReason, int promptTokens,
            int completionTokens, int reasoningTokens, int reasoningEvents,
            int finalAnswerTokens, long ttftMillis, long completionMillis,
            long promptEvaluationMillis, Instant startedAt) {
        private static GenerationResult from(JsonObject body, Instant startedAt,
                int observedReasoningEvents) {
            return new GenerationResult(string(body, "text"), string(body, "finishReason"),
                    (int) number(body, "promptTokens", 0),
                    (int) number(body, "completionTokens", 0),
                    (int) number(body, "reasoningTokens", 0),
                    Math.max(observedReasoningEvents,
                            (int) number(body, "reasoningEvents", 0)),
                    (int) number(body, "finalAnswerTokens", 0),
                    number(body, "ttftMillis", -1),
                    number(body, "completionMillis", -1),
                    number(body, "promptEvaluationMillis", -1), startedAt);
        }
    }
}
