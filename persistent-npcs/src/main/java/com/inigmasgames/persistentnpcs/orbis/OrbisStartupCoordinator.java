package com.inigmasgames.persistentnpcs.orbis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Asynchronous world-start warmup sequencer. Hytale lifecycle callbacks only signal this class;
 * provider construction, network I/O and model work always execute on completion threads.
 */
public final class OrbisStartupCoordinator implements AutoCloseable {
    public enum State { INITIALIZING, WARMING, FOREGROUND_READY, FULLY_WARM, DEGRADED }

    private final AiServiceRouter services;
    private final List<Path> voiceReferences;
    private final Consumer<String> log;
    private final long hytaleSafetyReserveMiB;
    private final OrbisReadinessService readiness = new OrbisReadinessService();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZING);
    private final AtomicReference<String> currentProvider = new AtomicReference<>("none");
    private final AtomicReference<String> failure = new AtomicReference<>("");
    private final AtomicReference<String> initiatedBy = new AtomicReference<>("not-triggered");
    private final AtomicReference<String> playerReadyBoundary = new AtomicReference<>("not-seen");
    private final AtomicBoolean readyBeforeAddPlayer = new AtomicBoolean();
    private final AtomicBoolean providersReady = new AtomicBoolean();
    private final AtomicBoolean dataReady = new AtomicBoolean();
    private final AtomicBoolean worldReady = new AtomicBoolean();
    private final AtomicBoolean entityBindingReady = new AtomicBoolean();
    private final AtomicBoolean optionalReady = new AtomicBoolean();
    private final CopyOnWriteArrayList<JsonObject> timeline = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> readyCallbacks = new CopyOnWriteArrayList<>();
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile CompletableFuture<Void> pipeline = CompletableFuture.completedFuture(null);
    private final CompletableFuture<Void> foregroundCompletion = new CompletableFuture<>();

    public OrbisStartupCoordinator(AiServiceRouter services, List<Path> voiceReferences,
            OrbisResourceConfig resourceConfig, Consumer<String> log) {
        this.services = java.util.Objects.requireNonNull(services, "services");
        this.voiceReferences = List.copyOf(voiceReferences == null ? List.of()
                : voiceReferences.stream().filter(java.util.Objects::nonNull).distinct().toList());
        this.log = log == null ? ignored -> { } : log;
        this.hytaleSafetyReserveMiB = (resourceConfig == null
                ? OrbisResourceConfig.defaults() : resourceConfig.validated())
                        .hytaleGpuSafetyReserveMiB();
        services.setStartupTelemetry(this::telemetry, this::summary);
    }

    /** Idempotent and non-blocking; safe from synchronous Update 6 lifecycle events. */
    public void trigger(String lifecycleEvent) {
        lifecycle(lifecycleEvent, "TRIGGER");
        if (!started.compareAndSet(false, true)) return;
        startedAt = Instant.now();
        initiatedBy.set(safe(lifecycleEvent));
        state.set(State.WARMING);
        readiness.transition(OrbisReadinessSystem.ORBIS, 10,
                OrbisReadinessStatus.STARTING, "Startup requested by " + safe(lifecycleEvent));
        readiness.transition(OrbisReadinessSystem.MOONSHINE, 10,
                OrbisReadinessStatus.STARTING, "Queued for startup");
        readiness.transition(OrbisReadinessSystem.NEMOTRON,
                activeLanguageModelDisplayName(), 10, OrbisReadinessStatus.STARTING,
                "Queued for startup");
        readiness.transition(OrbisReadinessSystem.CHATTERBOX, 10,
                OrbisReadinessStatus.STARTING, "Queued for startup");
        event("WARMUP_STARTED", "ORBIS", "", 0, 0, "");
        pipeline = phase("MOONSHINE", "moonshine-tiny-streaming",
                        this::warmMoonshine)
                .thenRun(() -> readiness.transition(OrbisReadinessSystem.ORBIS, 40,
                        OrbisReadinessStatus.WARMING, "Moonshine ready"))
                .thenCompose(ignored -> phase("QWEN", "inactive-runners",
                        services::unloadInactiveLanguageModels))
                .thenCompose(ignored -> phase("NEMOTRON", "nemotron-3-nano:4b",
                        this::warmActiveLanguageModel))
                .thenRun(() -> readiness.transition(OrbisReadinessSystem.ORBIS, 65,
                        OrbisReadinessStatus.WARMING, "Language model ready"))
                .thenCompose(ignored -> phase("CHATTERBOX", "chatterbox-turbo",
                        this::warmChatterbox))
                .thenRun(() -> readiness.transition(OrbisReadinessSystem.ORBIS, 85,
                        OrbisReadinessStatus.WARMING, "Voice providers ready"))
                .thenCompose(ignored -> phase("STEADY_STATE", "balanced-headroom",
                        () -> services.awaitSteadyStateHeadroom(hytaleSafetyReserveMiB)))
                .thenRun(() -> readiness.transition(OrbisReadinessSystem.ORBIS, 90,
                        OrbisReadinessStatus.WARMING, "Verifying provider health"))
                .thenCompose(ignored -> services.probeAvailability())
                .whenComplete((ignored, problem) -> {
                    currentProvider.set("none");
                    if (problem == null) {
                        providersReady.set(true);
                        event("PROVIDER_PIPELINE_READY", "ORBIS", "", 0, 0, "");
                        maybeCompleteReadiness();
                    } else {
                        completedAt = Instant.now();
                        Throwable root = root(problem);
                        failure.set(root.getClass().getSimpleName() + ": "
                                + String.valueOf(root.getMessage()));
                        state.set(State.DEGRADED);
                        readiness.fail(OrbisReadinessSystem.ORBIS,
                                OrbisReadinessStatus.DEGRADED, failure.get());
                        event("WARMUP_FAILED", currentProvider.get(), "", 0, 0,
                                failure.get());
                    }
                });
    }

    public void lifecycle(String lifecycleEvent, String boundary) {
        String event = safe(lifecycleEvent);
        if ("AddPlayerToWorldEvent".equals(event)) {
            readyBeforeAddPlayer.set(ready());
            playerReadyBoundary.set("AddPlayerToWorldEvent state=" + state.get());
        } else if ("PlayerReadyEvent".equals(event)) {
            playerReadyBoundary.set(playerReadyBoundary.get() + "; PlayerReadyEvent state="
                    + state.get());
        }
        log.accept("ORBIS_STARTUP_LIFECYCLE event=" + event + " boundary=" + safe(boundary)
                + " state=" + state.get() + " provider=" + currentProvider.get());
    }

    public void whenReady(Runnable callback) {
        if (callback == null) return;
        readyCallbacks.add(callback);
        if (ready()) runCallback(callback);
    }

    public boolean ready() { return state.get() == State.FOREGROUND_READY
            || state.get() == State.FULLY_WARM; }
    public boolean fullyWarm() { return state.get() == State.FULLY_WARM; }
    public OrbisReadinessService readiness() { return readiness; }
    public CompletableFuture<Void> completion() { return foregroundCompletion; }

    public void dataReady(String detail, long durationMs, boolean cacheHit) {
        dataReady.set(true);
        stage("SAVE_WORLD_DATA_READY", "profiles,beliefs,memories,relationships,indexes",
                durationMs, cacheHit ? "HIT" : "MISS", detail);
        maybeCompleteReadiness();
    }

    public void worldReady(String detail) {
        worldReady.set(true);
        stage("WORLD_BINDING_READY", "SAVE_WORLD_DATA_READY", 0, "N/A", detail);
        maybeCompleteReadiness();
    }

    public void entityBindingReady(String detail) {
        entityBindingReady.set(true);
        stage("ENTITY_BINDING_READY", "WORLD_BINDING_READY", 0, "N/A", detail);
        maybeCompleteReadiness();
    }

    public void optionalBackgroundReady(String detail) {
        optionalReady.set(true);
        stage("OPTIONAL_BACKGROUND_READY", "FOREGROUND_READY", 0, "N/A", detail);
        maybeCompleteReadiness();
    }

    public void stage(String stage, String dependencies, long durationMs,
            String cache, String detail) {
        JsonObject value = new JsonObject();
        value.addProperty("event", "STARTUP_STAGE");
        value.addProperty("at", Instant.now().toString());
        value.addProperty("stage", safe(stage));
        value.addProperty("dependencies", safe(dependencies));
        value.addProperty("durationMs", Math.max(0, durationMs));
        value.addProperty("cache", safe(cache));
        value.addProperty("detail", safe(detail));
        timeline.add(value);
        while (timeline.size() > 64) timeline.removeFirst();
        log.accept("ORBIS_STARTUP_STAGE " + value);
    }

    private synchronized void maybeCompleteReadiness() {
        if (state.get() == State.DEGRADED) return;
        boolean foreground = providersReady.get() && dataReady.get() && worldReady.get()
                && entityBindingReady.get();
        if (foreground && !ready()) {
            state.set(State.FOREGROUND_READY);
            completedAt = Instant.now();
            readiness.transition(OrbisReadinessSystem.ORBIS, 100,
                    OrbisReadinessStatus.READY, "FOREGROUND_READY");
            event("FOREGROUND_READY", "ORBIS", "", 0, 0, "");
            foregroundCompletion.complete(null);
            readyCallbacks.forEach(this::runCallback);
        }
        if (foreground && optionalReady.get() && state.get() != State.FULLY_WARM) {
            state.set(State.FULLY_WARM);
            readiness.transition(OrbisReadinessSystem.ORBIS, 100,
                    OrbisReadinessStatus.READY, "FULLY_WARM");
            event("FULLY_WARM", "ORBIS", "", 0, 0, "");
        }
    }

    public String summary() {
        return "ORBIS " + state.get() + "\nMoonshine=" + providerState("MOONSHINE")
                + " Nemotron=" + providerState("NEMOTRON")
                + " Chatterbox=" + providerState("CHATTERBOX")
                + "\ncurrentWarmupProvider=" + currentProvider.get()
                + " elapsedMs=" + elapsedMillis()
                + "\nreadyBeforeAddPlayerToWorld=" + readyBeforeAddPlayer.get()
                + " foregroundRequirements=providers:" + providersReady.get()
                + ",data:" + dataReady.get() + ",world:" + worldReady.get()
                + ",entity:" + entityBindingReady.get()
                + " optionalBackground=" + optionalReady.get()
                + (failure.get().isBlank() ? "" : "\nfailure=" + failure.get());
    }

    public JsonObject telemetry() {
        JsonObject value = new JsonObject();
        value.addProperty("state", state.get().name());
        value.addProperty("initiatedBy", initiatedBy.get());
        value.addProperty("currentProvider", currentProvider.get());
        value.addProperty("elapsedStartupMs", elapsedMillis());
        value.addProperty("startedAt", startedAt == null ? "" : startedAt.toString());
        value.addProperty("completedAt", completedAt == null ? "" : completedAt.toString());
        value.addProperty("readyBeforeAddPlayerToWorld", readyBeforeAddPlayer.get());
        value.addProperty("foregroundReady", ready());
        value.addProperty("fullyWarm", fullyWarm());
        value.addProperty("providersReady", providersReady.get());
        value.addProperty("dataReady", dataReady.get());
        value.addProperty("worldReady", worldReady.get());
        value.addProperty("entityBindingReady", entityBindingReady.get());
        value.addProperty("optionalBackgroundReady", optionalReady.get());
        value.addProperty("playerReadyBoundary", playerReadyBoundary.get());
        value.addProperty("failure", failure.get());
        JsonObject providers = new JsonObject();
        for (String provider : List.of("MOONSHINE", "QWEN", "NEMOTRON", "CHATTERBOX",
                "STEADY_STATE")) {
            providers.addProperty(provider, providerState(provider));
        }
        value.add("providers", providers);
        JsonArray events = new JsonArray();
        timeline.forEach(item -> events.add(item.deepCopy()));
        value.add("warmupTimeline", events);
        return value;
    }

    private <T> CompletableFuture<T> phase(String provider, String model,
            java.util.function.Supplier<CompletableFuture<T>> action) {
        currentProvider.set(provider);
        long startedNanos = System.nanoTime();
        RuntimeResourceMonitor.Snapshot before = services.resourceSnapshot();
        event("PROVIDER_WARMUP_STARTED", provider, model, 0, 0, "");
        CompletableFuture<T> future = CompletableFuture.supplyAsync(action)
                .thenCompose(java.util.function.Function.identity());
        return future.whenComplete((result, problem) -> {
            long elapsed = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            RuntimeResourceMonitor.Snapshot after = services.resourceSnapshot();
            event(problem == null ? "PROVIDER_WARMUP_COMPLETED" : "PROVIDER_WARMUP_FAILED",
                    provider, model, elapsed, inferWarmupMillis(result),
                    problem == null ? "" : String.valueOf(root(problem).getMessage()),
                    before, after);
            if (problem != null) {
                readinessSystem(provider).ifPresent(system -> readiness.fail(system,
                        OrbisReadinessStatus.ERROR,
                        root(problem).getClass().getSimpleName() + ": "
                                + String.valueOf(root(problem).getMessage())));
            }
        });
    }

    private CompletableFuture<JsonObject> warmMoonshine() {
        readiness.transition(OrbisReadinessSystem.MOONSHINE, 30,
                OrbisReadinessStatus.LOADING, "Starting local speech worker");
        return services.bootstrapMoonshine().thenCompose(ignored -> {
            readiness.transition(OrbisReadinessSystem.MOONSHINE, 70,
                    OrbisReadinessStatus.WARMING, "Worker ready; warming Moonshine");
            return services.warmMoonshine();
        }).thenApply(result -> {
            readiness.transition(OrbisReadinessSystem.MOONSHINE, 100,
                    OrbisReadinessStatus.READY, "Moonshine warm and ready");
            return result;
        });
    }

    private CompletableFuture<Void> warmActiveLanguageModel() {
        String displayName = activeLanguageModelDisplayName();
        readiness.transition(OrbisReadinessSystem.NEMOTRON, displayName, 30,
                OrbisReadinessStatus.LOADING,
                "Preparing " + services.activeLanguageModel());
        return services.prepareActiveLanguageModelResidency().thenCompose(ignored -> {
            readiness.transition(OrbisReadinessSystem.NEMOTRON, displayName, 80,
                    OrbisReadinessStatus.WARMING, "Model resident; running warm inference");
            return services.warmActiveLanguageModel();
        }).thenRun(() -> readiness.transition(OrbisReadinessSystem.NEMOTRON,
                displayName, 100, OrbisReadinessStatus.READY,
                services.activeLanguageModel() + " ready"));
    }

    private CompletableFuture<JsonObject> warmChatterbox() {
        readiness.transition(OrbisReadinessSystem.CHATTERBOX, 30,
                OrbisReadinessStatus.LOADING, "Starting local voice worker");
        return services.bootstrapChatterbox().thenCompose(ignored -> {
            readiness.transition(OrbisReadinessSystem.CHATTERBOX, 60,
                    OrbisReadinessStatus.LOADING, "Worker ready; loading Chatterbox Turbo");
            readiness.transition(OrbisReadinessSystem.CHATTERBOX, 80,
                    OrbisReadinessStatus.WARMING, "Caching voice conditioning");
            return services.warmChatterbox(voiceReferences);
        }).thenApply(result -> {
            readiness.transition(OrbisReadinessSystem.CHATTERBOX, 100,
                    OrbisReadinessStatus.READY, "Chatterbox and voice conditioning ready");
            return result;
        });
    }

    private String activeLanguageModelDisplayName() {
        String provider = services.activeLanguageModelName();
        if (provider.equalsIgnoreCase("NEMOTRON")) return "Nemotron";
        if (provider.equalsIgnoreCase("QWEN")) return "Qwen";
        return provider.isBlank() ? "Language Model" : provider;
    }

    private static java.util.Optional<OrbisReadinessSystem> readinessSystem(String provider) {
        return switch (safe(provider).toUpperCase(java.util.Locale.ROOT)) {
            case "MOONSHINE" -> java.util.Optional.of(OrbisReadinessSystem.MOONSHINE);
            case "NEMOTRON" -> java.util.Optional.of(OrbisReadinessSystem.NEMOTRON);
            case "CHATTERBOX" -> java.util.Optional.of(OrbisReadinessSystem.CHATTERBOX);
            default -> java.util.Optional.empty();
        };
    }

    private void event(String type, String provider, String model, long loadMs,
            long inferenceMs, String reason) {
        RuntimeResourceMonitor.Snapshot snapshot = services.resourceSnapshot();
        event(type, provider, model, loadMs, inferenceMs, reason, snapshot, snapshot);
    }

    private void event(String type, String provider, String model, long loadMs,
            long inferenceMs, String reason, RuntimeResourceMonitor.Snapshot before,
            RuntimeResourceMonitor.Snapshot after) {
        JsonObject value = new JsonObject();
        value.addProperty("event", type);
        value.addProperty("at", Instant.now().toString());
        value.addProperty("lifecycleInitiator", initiatedBy.get());
        value.addProperty("provider", provider);
        value.addProperty("model", model);
        value.addProperty("loadTimeMs", loadMs);
        value.addProperty("warmupInferenceMs", inferenceMs);
        value.addProperty("cpuBeforePercent", before.systemCpuPercent());
        value.addProperty("cpuAfterPercent", after.systemCpuPercent());
        value.addProperty("gpuBeforePercent", before.gpuUtilizationPercent());
        value.addProperty("gpuAfterPercent", after.gpuUtilizationPercent());
        value.addProperty("freeVramBeforeMiB", before.vramFreeMiB());
        value.addProperty("freeVramAfterMiB", after.vramFreeMiB());
        value.addProperty("usedVramBeforeMiB", before.vramUsedMiB());
        value.addProperty("usedVramAfterMiB", after.vramUsedMiB());
        value.addProperty("failureReason", safe(reason));
        timeline.add(value);
        while (timeline.size() > 48) timeline.removeFirst();
        log.accept("ORBIS_STARTUP " + value);
    }

    private String providerState(String provider) {
        List<JsonObject> copy = new ArrayList<>(timeline);
        for (int index = copy.size() - 1; index >= 0; index--) {
            JsonObject value = copy.get(index);
            if (!value.has("provider")) continue;
            if (!provider.equals(value.get("provider").getAsString())) continue;
            String event = value.get("event").getAsString();
            if (event.endsWith("COMPLETED")) return "READY";
            if (event.endsWith("FAILED")) return "FAILED";
            if (event.endsWith("STARTED")) return "WARMING";
        }
        return "PENDING";
    }

    private long elapsedMillis() {
        if (startedAt == null) return 0;
        return Duration.between(startedAt, completedAt == null ? Instant.now() : completedAt)
                .toMillis();
    }

    private static long inferWarmupMillis(Object result) {
        if (result instanceof JsonObject json) {
            if (json.has("warmupInferenceMs")) return json.get("warmupInferenceMs").getAsLong();
            if (json.has("conditioningMs")) return json.get("conditioningMs").getAsLong();
        }
        return 0;
    }

    private void runCallback(Runnable callback) {
        try { callback.run(); } catch (RuntimeException ignored) { }
    }

    private static Throwable root(Throwable failure) {
        Throwable value = failure;
        while (value != null && value.getCause() != null) value = value.getCause();
        return value == null ? new IllegalStateException("unknown startup failure") : value;
    }

    private static String safe(String value) { return value == null ? "" : value.strip(); }

    @Override public void close() {
        pipeline.cancel(true);
        foregroundCompletion.cancel(true);
        readyCallbacks.clear();
        readiness.close();
    }
}
