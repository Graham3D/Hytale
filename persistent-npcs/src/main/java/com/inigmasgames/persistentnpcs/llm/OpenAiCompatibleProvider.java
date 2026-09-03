package com.inigmasgames.persistentnpcs.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenAiCompatibleProvider implements LlmProvider, ManagedLlmResidency {
    /** Bound hidden reasoning before preserving the same request with reasoning disabled. */
    /** Calibrated on local Nemotron: 48 hidden-only events preserve bounded deliberation while
     * leaving enough of the 12 s foreground budget for one recovery and strict finalization. */
    public static final int MAX_REASONING_ONLY_EVENTS = 48;
    private static final ScheduledExecutorService STREAM_TIMEOUTS =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "persistent-npcs-llm-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    private final FrameworkConfig config;
    private final HttpClient client;
    private final Consumer<String> diagnosticLog;
    private final ToolChoicePolicy toolChoicePolicy;
    private final Integer ollamaGpuLayers;
    private final java.util.concurrent.atomic.AtomicInteger activeOllamaGpuLayers;
    private final String ollamaKeepAlive;
    private final AtomicBoolean preferredResidencyPrepared = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> residencyPreparation =
            new AtomicReference<>();
    private final AtomicLong lastWarmupNanos = new AtomicLong();
    private final ConcurrentHashMap<UUID, java.util.Set<RequestControl>> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicReference<CompletableFuture<Void>> providerDrain =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    public OpenAiCompatibleProvider(FrameworkConfig config) {
        this(config, ignored -> { });
    }

    public OpenAiCompatibleProvider(FrameworkConfig config, Consumer<String> diagnosticLog) {
        this(config, ToolChoicePolicy.NAMED_SINGLE, diagnosticLog);
    }

    public OpenAiCompatibleProvider(FrameworkConfig config,
            ToolChoicePolicy toolChoicePolicy, Consumer<String> diagnosticLog) {
        this(config, toolChoicePolicy, diagnosticLog, null, null);
    }

    public OpenAiCompatibleProvider(FrameworkConfig config,
            ToolChoicePolicy toolChoicePolicy, Consumer<String> diagnosticLog,
            Integer ollamaGpuLayers, String ollamaKeepAlive) {
        this.config = config;
        this.toolChoicePolicy = toolChoicePolicy == null
                ? ToolChoicePolicy.NAMED_SINGLE : toolChoicePolicy;
        this.diagnosticLog = diagnosticLog;
        this.ollamaGpuLayers = ollamaGpuLayers == null
                ? null : Math.max(0, ollamaGpuLayers);
        this.activeOllamaGpuLayers = new java.util.concurrent.atomic.AtomicInteger(
                this.ollamaGpuLayers == null ? -1 : this.ollamaGpuLayers);
        this.ollamaKeepAlive = ollamaKeepAlive == null || ollamaKeepAlive.isBlank()
                ? "10m" : ollamaKeepAlive.strip();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .build();
    }

    @Override
    public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        return generateResponse(request, ignored -> { });
    }

    @Override
    public CompletableFuture<LlmResult> generateResponse(
            LlmRequest request, Consumer<String> tokenConsumer) {
        if (!hasConfiguredModel()) {
            return CompletableFuture.failedFuture(new LlmSetupRequiredException(
                    "Local AI setup is required: set model in config.json to the exact "
                            + "model ID loaded in LM Studio or Ollama."));
        }
        if (usesManagedOllamaResidency() && !preferredResidencyPrepared.get()) {
            return ensurePreferredResidency().thenCompose(ignored ->
                    generateResponse(request, tokenConsumer));
        }
        CompletableFuture<Void> drain = providerDrain.get();
        if (!drain.isDone()) {
            long started = System.nanoTime();
            log("LLM_PROVIDER_DRAIN_WAIT request=" + shortId(request.providerRequestId()));
            return boundedDrain(drain, request.providerRequestId()).thenCompose(ignored -> {
                log("LLM_PROVIDER_DRAIN_COMPLETE request="
                        + shortId(request.providerRequestId()) + " waitMs="
                        + elapsedMillis(started, System.nanoTime()));
                return generateResponse(request, tokenConsumer);
            });
        }
        Instant requestStartedAt = Instant.now();
        long requestStartedNanos = System.nanoTime();
        String requestId = shortId(request.providerRequestId());
        String probe = diagnosticProbe(request);
        if (probe != null) {
            String system = request.messages().stream()
                    .filter(message -> "system".equals(message.role()))
                    .map(ChatMessage::content).collect(Collectors.joining("\n"));
            log("DIALOGUE_DIAG stage=1 request=" + requestId
                    + " finalSystemContext=\n" + system);
            log("DIALOGUE_DIAG stage=2 request=" + requestId
                    + " userMessage=" + probe);
            log("DIALOGUE_DIAG request=" + requestId
                    + " eligibleTools=" + request.tools().stream()
                            .map(tool -> tool.function().name()).toList());
        }
        boolean streamThisRequest = shouldStream(request);
        log("LLM HTTP dispatch request=" + requestId
                + " mode=" + (streamThisRequest ? "SSE" : "JSON")
                + " endpoint=" + config.endpoint() + " model=" + config.model()
                + " reasoningPolicy=" + request.executionPolicy().reasoningPolicy()
                + " reasoningMode=" + request.executionPolicy().requestedReasoningMode()
                + " reasoningEffort=" + valueOrOmitted(wireReasoningEffort(request))
                + " maxTokens=" + (request.maxTokensOverride() == null
                        ? config.maxTokens() : request.maxTokensOverride())
                + " responseStartTimeoutMs=" + config.effectiveResponseStartTimeoutMillis()
                + " streamIdleTimeoutMs=" + config.effectiveStreamIdleTimeoutMillis());

        RequestControl control = new RequestControl(request.providerRequestId());
        inFlight.computeIfAbsent(request.providerRequestId(),
                ignored -> ConcurrentHashMap.newKeySet()).add(control);
        CompletableFuture<LlmResult> future;
        if (!streamThisRequest) {
            future = sendNonStreaming(request, tokenConsumer,
                    requestStartedAt, requestStartedNanos, requestId, control);
        } else {
            future = sendStreaming(request, tokenConsumer,
                            requestStartedAt, requestStartedNanos, requestId, control)
                    .exceptionallyCompose(failure -> {
                        Throwable cause = unwrap(failure);
                        if (control.cancelled()) {
                            return CompletableFuture.failedFuture(
                                    new java.util.concurrent.CancellationException(
                                            "Provider request cancelled: " + requestId));
                        }
                        if (cause instanceof StreamingUnsupportedException) {
                            log("LLM SSE unsupported request=" + requestId
                                    + "; retrying once as JSON: " + cause.getMessage());
                            return sendNonStreaming(request, tokenConsumer,
                                    requestStartedAt, requestStartedNanos, requestId, control);
                        }
                        if (cause instanceof ReasoningOnlyBudgetExceeded exceeded) {
                            if (request.turnExecutionPlan() != null
                                    && !com.inigmasgames.persistentnpcs.conversation.contract
                                            .RecoverySupervisor.tryAcquire(
                                                    request.turnExecutionPlan(),
                                                    "REASONING_ONLY")) {
                                return CompletableFuture.failedFuture(cause);
                            }
                            LlmRequest recovery = reasoningDisabledRecovery(request);
                            log("LLM_REASONING_RECOVERY request=" + requestId
                                    + " reasoningEvents=" + exceeded.reasoningEvents
                                    + " action=RETRY_SAME_MODEL_REASONING_DISABLED"
                                    + " maxTokens=" + recovery.maxTokensOverride());
                            return sendStreaming(recovery, tokenConsumer, requestStartedAt,
                                            requestStartedNanos, requestId + "r", control)
                                    .thenApply(result -> recoveredReasoningResult(
                                            request, result, exceeded.reasoningEvents));
                        }
                        return CompletableFuture.failedFuture(cause);
                    });
        }
        CompletableFuture<LlmResult> tracked = future.whenComplete((result, failure) -> {
            if (failure == null) {
                log("LLM HTTP complete request=" + requestId
                        + " streaming=" + result.latency().streaming()
                        + " ttftMs=" + result.latency().timeToFirstTokenMillis()
                        + " completionMs=" + result.latency().completionMillis());
                return;
            }
            Throwable cause = unwrap(failure);
            log("LLM HTTP failed request=" + requestId
                    + " elapsedMs=" + elapsedMillis(requestStartedNanos, System.nanoTime())
                    + " type=" + cause.getClass().getSimpleName()
                    + " reason=" + compact(cause.getMessage(), 400));
        });
        control.attach(tracked);
        completeControl(control, tracked);
        return control.outward();
    }

    private void completeControl(RequestControl control, CompletableFuture<LlmResult> tracked) {
        tracked.whenComplete((result, failure) -> {
            if (failure == null) control.outward().complete(result);
            else control.outward().completeExceptionally(unwrap(failure));
            control.drained().complete(null);
            java.util.Set<RequestControl> requests = inFlight.get(control.requestId());
            if (requests != null) {
                requests.remove(control);
                if (requests.isEmpty()) inFlight.remove(control.requestId(), requests);
            }
        });
    }

    /**
     * Natural speech benefits from token streaming. Strict JSON-schema decisions do not:
     * buffering them avoids exposing partial structured output and gives Ollama one bounded,
     * deterministic response contract. Deliberative memo calls have no response format and
     * therefore retain streaming; their strict final decision is buffered here.
     */
    static boolean shouldStream(LlmRequest request, boolean providerStreamingEnabled) {
        return providerStreamingEnabled && request != null && request.responseFormat() == null;
    }

    private boolean shouldStream(LlmRequest request) {
        return shouldStream(request, streamingEnabled());
    }

    @Override
    public void cancel(UUID requestOrSessionId) {
        java.util.Set<RequestControl> requests = inFlight.get(requestOrSessionId);
        if (requests == null || requests.isEmpty()) return;
        List<RequestControl> snapshot = List.copyOf(requests);
        snapshot.forEach(RequestControl::cancel);
        CompletableFuture<Void> drain = CompletableFuture.allOf(snapshot.stream()
                .map(RequestControl::drained).toArray(CompletableFuture[]::new));
        providerDrain.set(drain);
        log("LLM_PROVIDER_CANCEL request=" + shortId(requestOrSessionId)
                + " activeTransports=" + snapshot.size() + " state=DRAINING");
    }

    @Override public String providerId() { return "openai-compatible:" + config.model(); }

    @Override public ProviderExecutionMode executionMode() {
        try {
            String host = URI.create(config.endpoint()).getHost();
            return host == null || host.equalsIgnoreCase("localhost")
                    || host.equals("127.0.0.1") || host.equals("::1")
                            ? ProviderExecutionMode.LOCAL : ProviderExecutionMode.REMOTE;
        } catch (RuntimeException ignored) {
            return ProviderExecutionMode.REMOTE;
        }
    }

    @Override public AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(streamingEnabled(), true, true, Set.of("json", "sse"));
    }

    @Override public CompletableFuture<AiProviderHealth> health() {
        return checkStatus().thenApply(status -> status.reachable()
                ? AiProviderHealth.healthy(status.reason())
                : AiProviderHealth.unavailable(status.reason()));
    }

    @Override public int concurrencyLimit() {
        return config.effectiveMaxConcurrentLlmRequests();
    }

    @Override public String backendDescription() {
        return "OpenAI-compatible endpoint=" + config.endpoint() + " model=" + config.model()
                + (usesManagedOllamaResidency() ? "; ollamaGpuLayers="
                        + activeOllamaGpuLayers.get()
                        + "; keepAlive=" + ollamaKeepAlive
                        + "; preferredResidencyPrepared="
                        + preferredResidencyPrepared.get() : "");
    }

    @Override public AiResourceRequirements resourceRequirements() {
        ExecutionPlacement placement = executionMode() == ProviderExecutionMode.REMOTE
                ? remotePlacement(config.endpoint()) : usesManagedOllamaResidency()
                        ? ExecutionPlacement.LOCAL_PARTIAL_GPU : ExecutionPlacement.UNKNOWN;
        long estimatedVram = executionMode() == ProviderExecutionMode.LOCAL
                ? estimatedLocalVramMiB(config.model()) : 0;
        long resident = usesManagedOllamaResidency()
                ? partialOffloadResidentVramMiB() : estimatedVram;
        // R067 connected 4-layer evidence: a successful foreground turn moved cached free
        // VRAM only 926 -> 924 MiB. Retain ample unobserved-peak allowance without carrying
        // the stale 256/128 contract from higher-offload profiles.
        boolean calibratedPartial = usesManagedOllamaResidency()
                && activeOllamaGpuLayers.get() <= 4;
        long incremental = executionMode() == ProviderExecutionMode.LOCAL
                ? calibratedPartial ? 128 : 256 : 0;
        long temporary = executionMode() == ProviderExecutionMode.LOCAL
                ? calibratedPartial ? 64 : 128 : 0;
        return new AiResourceRequirements(placement, backendDescription(), 6144, estimatedVram,
                concurrencyLimit(), streamingEnabled(), true, 1200,
                resident, incremental, temporary);
    }

    private long partialOffloadResidentVramMiB() {
        // Target-host measurements: 12 layers ~= 1,281 MiB and 6 layers ~= 987 MiB.
        // Preserve a conservative floor and interpolate only the managed Ollama profile.
        long full = estimatedLocalVramMiB(config.model());
        int activeLayers = activeOllamaGpuLayers.get();
        long measured = activeLayers < 0 ? Math.round(full * 0.36)
                : 680L + 50L * activeLayers;
        return Math.max(768, Math.min(full, measured));
    }

    private static long estimatedLocalVramMiB(String model) {
        String value = model == null ? "" : model.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher parameters = java.util.regex.Pattern.compile(
                "(?:^|[^0-9])(\\d+(?:\\.\\d+)?)b(?:[^a-z]|$)").matcher(value);
        if (parameters.find()) {
            double billions = Double.parseDouble(parameters.group(1));
            // Q4 weights plus KV/cache/runtime headroom; explicitly an Orbis estimate.
            return Math.max(2_048L, Math.round(billions * 640.0 + 1_024.0));
        }
        return 4_096L;
    }

    private static ExecutionPlacement remotePlacement(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            if (host == null) return ExecutionPlacement.REMOTE_CLOUD;
            if (host.startsWith("10.") || host.startsWith("192.168.")
                    || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
                return ExecutionPlacement.REMOTE_LAN;
            }
        } catch (RuntimeException ignored) { }
        return ExecutionPlacement.REMOTE_CLOUD;
    }

    @Override
    public boolean streamingEnabled() {
        return config.streamingEnabled();
    }

    @Override
    public CompletableFuture<Void> warmUp() {
        long now = System.nanoTime();
        long previous = lastWarmupNanos.get();
        if (previous != 0 && now - previous < TimeUnit.MINUTES.toNanos(4)) {
            return CompletableFuture.completedFuture(null);
        }
        if (!lastWarmupNanos.compareAndSet(previous, now)) {
            return CompletableFuture.completedFuture(null);
        }
        LlmRequest warmup = new LlmRequest(UUID.randomUUID(), new UUID(0, 1),
                new UUID(0, 2), List.of(
                        new ChatMessage("system", "Local inference warmup. Reply only: OK"),
                        new ChatMessage("user", "OK")), List.of());
        long started = System.nanoTime();
        log("LLM warmup start model=" + config.model());
        return generateResponse(warmup).handle((result, failure) -> {
            if (failure == null) {
                log("LLM warmup complete wallMs="
                        + elapsedMillis(started, System.nanoTime()));
            } else {
                log("LLM warmup unavailable reason=" + compact(
                        unwrap(failure).getMessage(), 240));
                lastWarmupNanos.set(0);
            }
            return null;
        });
    }

    /** Loads the Ollama runner with the configured partial-offload policy before OpenAI calls. */
    public CompletableFuture<Void> ensurePreferredResidency() {
        if (!usesManagedOllamaResidency() || preferredResidencyPrepared.get()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> prior = residencyPreparation.get();
        if (prior != null) return prior;
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("prompt", "");
        body.addProperty("stream", false);
        body.addProperty("keep_alive", ollamaKeepAlive);
        JsonObject options = new JsonObject();
        options.addProperty("num_gpu", activeOllamaGpuLayers.get());
        // Ollama interprets zero as its default generation budget, not "load only".
        // An empty prompt plus one-token ceiling performs a bounded residency load.
        options.addProperty("num_predict", 1);
        body.add("options", options);
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(ollamaApi("/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(JsonFiles.GSON.toJson(body),
                        StandardCharsets.UTF_8)).build();
        CompletableFuture<Void> created = client.sendAsync(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new LlmProviderException("Ollama residency preparation returned HTTP "
                                + response.statusCode() + ": "
                                + compact(response.body(), 300));
                    }
                    preferredResidencyPrepared.set(true);
                    log("OLLAMA_LIFECYCLE action=LOAD_WARM model=" + config.model()
                            + " numGpuLayers=" + activeOllamaGpuLayers.get()
                            + " wallMs=" + elapsedMillis(started, System.nanoTime()));
                    return null;
                });
        if (!residencyPreparation.compareAndSet(null, created)) {
            return residencyPreparation.get();
        }
        return created.whenComplete((ignored, failure) -> {
            residencyPreparation.compareAndSet(created, null);
            if (failure != null) preferredResidencyPrepared.set(false);
        });
    }

    /** Supported Ollama keep_alive=0 lifecycle action; never kills an arbitrary process. */
    public CompletableFuture<Boolean> unloadResidentModel() {
        if (!isLocalOllamaEndpoint()) return CompletableFuture.completedFuture(false);
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("keep_alive", 0);
        body.addProperty("stream", false);
        HttpRequest request = HttpRequest.newBuilder(ollamaApi("/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(JsonFiles.GSON.toJson(body),
                        StandardCharsets.UTF_8)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8)).thenApply(response -> {
                    boolean success = response.statusCode() >= 200
                            && response.statusCode() < 300;
                    if (success) preferredResidencyPrepared.set(false);
                    log("OLLAMA_LIFECYCLE action=UNLOAD model=" + config.model()
                            + " success=" + success + " http=" + response.statusCode());
                    return success;
                });
    }

    /** True only when this provider instance established the managed Ollama residency. */
    public boolean preferredResidencyPrepared() {
        return preferredResidencyPrepared.get();
    }

    /** Two-stage, bounded pressure fallback; restoration occurs only on restart. */
    public CompletableFuture<Boolean> activateLowerMemoryProfile() {
        if (!usesManagedOllamaResidency()) return CompletableFuture.completedFuture(false);
        int current = activeOllamaGpuLayers.get();
        int lower = nextLowerMemoryGpuLayers(current);
        if (current <= lower) return CompletableFuture.completedFuture(false);
        return unloadResidentModel().thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.completedFuture(false);
            activeOllamaGpuLayers.set(lower);
            preferredResidencyPrepared.set(false);
            lastWarmupNanos.set(0);
            return ensurePreferredResidency().thenApply(ignored -> {
                log("OLLAMA_HARDWARE_PROFILE transition=BALANCED_" + current
                        + "_LAYER->DEGRADED_" + lower + "_LAYER");
                return true;
            });
        });
    }

    /**
     * Startup-only convergence for a resident Chatterbox pair. Once measured pressure proves the
     * balanced profile unsafe, choose the already-approved zero-layer profile in one lifecycle
     * transition instead of reloading at every intermediate profile.
     */
    public CompletableFuture<Boolean> activateStartupSteadyStateProfile() {
        if (!usesManagedOllamaResidency()) return CompletableFuture.completedFuture(false);
        int current = activeOllamaGpuLayers.get();
        if (current <= 0) return CompletableFuture.completedFuture(false);
        return unloadResidentModel().thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.completedFuture(false);
            activeOllamaGpuLayers.set(0);
            preferredResidencyPrepared.set(false);
            lastWarmupNanos.set(0);
            return ensurePreferredResidency().thenApply(ignored -> {
                log("OLLAMA_HARDWARE_PROFILE transition=BALANCED_" + current
                        + "_LAYER->DEGRADED_0_LAYER reason=STARTUP_STEADY_STATE");
                return true;
            });
        });
    }

    static int nextLowerMemoryGpuLayers(int current) {
        if (current <= 0) return 0;
        return current > 2 ? 2 : 0;
    }

    public String activeHardwareProfile() {
        int layers = activeOllamaGpuLayers.get();
        return layers < 0 ? "UNMANAGED" : layers <= 2
                ? "DEGRADED_" + layers + "_LAYER" : "BALANCED_" + layers + "_LAYER";
    }

    /** Releases only residency established by this server process; Ollama itself is not killed. */
    @Override public void close() {
        if (!preferredResidencyPrepared.get() || !isLocalOllamaEndpoint()) return;
        try {
            unloadResidentModel().orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
        } catch (RuntimeException failure) {
            log("OLLAMA_LIFECYCLE action=UNLOAD_ON_SERVER_SHUTDOWN success=false reason="
                    + String.valueOf(failure.getMessage()));
        }
    }

    @Override public CompletableFuture<Void> ensureResident() {
        return ensurePreferredResidency();
    }

    @Override public CompletableFuture<Boolean> unloadResident() {
        return unloadResidentModel();
    }

    @Override public boolean residencyPrepared() {
        return preferredResidencyPrepared();
    }

    private boolean usesManagedOllamaResidency() {
        return ollamaGpuLayers != null && isLocalOllamaEndpoint();
    }

    private boolean isLocalOllamaEndpoint() {
        try {
            URI endpoint = URI.create(config.endpoint());
            String host = endpoint.getHost();
            return host != null && (host.equals("127.0.0.1")
                    || host.equalsIgnoreCase("localhost") || host.equals("::1"))
                    && endpoint.getPort() == 11434;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private URI ollamaApi(String path) {
        URI endpoint = URI.create(config.endpoint());
        try {
            return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(),
                    endpoint.getPort(), path, null, null);
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException(failure);
        }
    }

    private CompletableFuture<LlmResult> sendStreaming(
            LlmRequest request,
            Consumer<String> tokenConsumer,
            Instant requestStartedAt,
            long requestStartedNanos,
            String requestId,
            RequestControl control) {
        AtomicLong firstTokenNanos = new AtomicLong();
        CompletableFuture<HttpResponse<InputStream>> responseFuture = client.sendAsync(
                request(request, true, "text/event-stream", false),
                HttpResponse.BodyHandlers.ofInputStream());
        control.transport(responseFuture);
        return responseStarted(responseFuture, requestId)
                .thenApply(response -> {
                    control.body(response.body());
                    if (control.cancelled()) throw new java.util.concurrent.CancellationException(
                            "Provider request cancelled before SSE consumption: " + requestId);
                    return consumeStreamingResponse(request, response, tokenConsumer,
                            requestStartedAt, requestStartedNanos, firstTokenNanos,
                            requestId, control);
                });
    }

    private CompletableFuture<HttpResponse<InputStream>> responseStarted(
            CompletableFuture<HttpResponse<InputStream>> responseFuture, String requestId) {
        int timeoutMillis = config.effectiveResponseStartTimeoutMillis();
        return responseFuture.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((response, failure) -> {
                    if (failure == null) {
                        return response;
                    }
                    Throwable cause = unwrap(failure);
                    if (cause instanceof TimeoutException) {
                        throw new CompletionException(new LlmTimeoutException(
                                LlmTimeoutException.Phase.RESPONSE_START,
                                "No HTTP response or first stream bytes arrived within "
                                        + timeoutMillis + " ms for request " + requestId,
                                cause));
                    }
                    throw new CompletionException(cause);
                });
    }

    private LlmResult consumeStreamingResponse(
            LlmRequest request,
            HttpResponse<InputStream> response,
            Consumer<String> tokenConsumer,
            Instant requestStartedAt,
            long requestStartedNanos,
            AtomicLong firstTokenNanos,
            String requestId,
            RequestControl control) {
        String contentType = response.headers().firstValue("Content-Type").orElse("unknown");
        log("LLM HTTP headers request=" + requestId + " status=" + response.statusCode()
                + " contentType=" + compact(contentType, 120)
                + " elapsedMs=" + elapsedMillis(requestStartedNanos, System.nanoTime()));

        InputStream body = response.body();
        StreamIdleGuard idleGuard = new StreamIdleGuard(body,
                config.effectiveStreamIdleTimeoutMillis());
        StringBuilder text = new StringBuilder();
        StringBuilder nonSseBody = new StringBuilder();
        Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();
        int eventCount = 0;
        int emptyOrRoleEvents = 0;
        int reasoningEvents = 0;
        int reasoningCharacters = 0;
        int contentEvents = 0;
        boolean sawSse = false;
        boolean sawDone = false;
        String finishReason = null;
        LlmUsage usage = null;

        try (body;
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        body, StandardCharsets.UTF_8));
                idleGuard) {
            idleGuard.activity();
            String line;
            while ((line = reader.readLine()) != null) {
                if (control.cancelled()) throw new java.util.concurrent.CancellationException(
                        "Provider request cancelled during SSE consumption: " + requestId);
                idleGuard.activity();
                String normalizedLine = line.stripLeading();
                if (!normalizedLine.startsWith("data:")) {
                    if (!line.isBlank()) {
                        if (!nonSseBody.isEmpty()) {
                            nonSseBody.append('\n');
                        }
                        nonSseBody.append(line);
                    }
                    continue;
                }
                sawSse = true;
                String payload = normalizedLine.substring("data:".length()).stripLeading();
                if (diagnosticProbe(request) != null) {
                    log("DIALOGUE_DIAG stage=3 request=" + requestId
                            + " rawSse=" + payload);
                }
                if (payload.isBlank()) {
                    continue;
                }
                if (payload.equals("[DONE]")) {
                    sawDone = true;
                    break;
                }
                eventCount++;
                SseEvent event = extractEvent(payload);
                if (event.usage() != null) usage = event.usage();
                if (event.reasoning()) {
                    reasoningEvents++;
                    reasoningCharacters += event.reasoningCharacters();
                    if (text.isEmpty() && toolCalls.isEmpty()
                            && request.executionPolicy().requestedReasoningMode()
                                    == LlmExecutionPolicy.ReasoningMode.ENABLED
                            && reasoningEvents >= MAX_REASONING_ONLY_EVENTS) {
                        throw new ReasoningOnlyBudgetExceeded(reasoningEvents);
                    }
                }
                if (event.content().isEmpty()) {
                    emptyOrRoleEvents++;
                } else {
                    contentEvents++;
                    if (firstTokenNanos.compareAndSet(0, System.nanoTime())) {
                        log("LLM first dialogue token request=" + requestId
                                + " ttftMs=" + elapsedMillis(
                                        requestStartedNanos, firstTokenNanos.get()));
                    }
                    text.append(event.content());
                    tokenConsumer.accept(event.content());
                }
                for (ToolCallFragment fragment : event.toolCalls()) {
                    ToolCallAccumulator accumulator = toolCalls.computeIfAbsent(
                            fragment.index(), ignored -> new ToolCallAccumulator());
                    accumulator.append(fragment);
                    if (firstTokenNanos.compareAndSet(0, System.nanoTime())) {
                        log("LLM first tool-call token request=" + requestId
                                + " ttftMs=" + elapsedMillis(
                                        requestStartedNanos, firstTokenNanos.get()));
                    }
                }
                if (event.finishReason() != null) {
                    finishReason = event.finishReason();
                }
            }
        } catch (IOException exception) {
            if (control.cancelled()) throw new java.util.concurrent.CancellationException(
                    "Provider request cancelled during SSE read: " + requestId);
            if (idleGuard.timedOut()) {
                throw streamIdleTimeout(requestId, idleGuard, exception);
            }
            throw new LlmProviderException("I/O failure while reading SSE for request "
                    + requestId + ": " + compact(exception.getMessage(), 300), exception);
        } catch (RuntimeException exception) {
            if (idleGuard.timedOut()) {
                throw streamIdleTimeout(requestId, idleGuard, exception);
            }
            throw exception;
        }
        if (idleGuard.timedOut()) {
            throw streamIdleTimeout(requestId, idleGuard, null);
        }

        long completedNanos = System.nanoTime();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = nonSseBody.toString();
            if (supportsAutomaticFallback(response.statusCode())) {
                throw new StreamingUnsupportedException("Streaming request returned HTTP "
                        + response.statusCode() + ": " + compact(errorBody, 400));
            }
            throw new LlmProviderException("LLM endpoint returned HTTP "
                    + response.statusCode() + ": " + compact(errorBody, 400));
        }
        List<LlmToolCall> completedToolCalls = toolCalls.values().stream()
                .map(ToolCallAccumulator::build)
                .toList();
        if (!text.isEmpty() || !completedToolCalls.isEmpty()) {
            if (diagnosticProbe(request) != null) {
                log("DIALOGUE_DIAG stage=4 request=" + requestId
                        + " assembledRawModelResponse=" + text);
                log("DIALOGUE_DIAG stage=5 request=" + requestId
                        + " toolCalls=" + completedToolCalls);
            }
            log("LLM SSE summary request=" + requestId + " events=" + eventCount
                    + " contentEvents=" + contentEvents
                    + " emptyOrRoleEvents=" + emptyOrRoleEvents
                    + " reasoningEvents=" + reasoningEvents
                    + " finishReason=" + valueOrOmitted(finishReason)
                    + " toolCalls=" + completedToolCalls.size()
                    + " done=" + sawDone);
            LlmReasoningTelemetry reasoning = reasoningTelemetry(request, reasoningEvents,
                    reasoningCharacters, text.toString(), usage);
            return result(text.toString(), requestStartedAt, requestStartedNanos,
                    firstTokenNanos.get(), completedNanos, true,
                    completedToolCalls, finishReason,
                    usage == null ? estimateUsage(request, text.toString()) : usage,
                    reasoning);
        }
        if (!sawSse && !nonSseBody.isEmpty()) {
            ParsedBody parsed = extractBody(nonSseBody.toString());
            if (!parsed.text().isBlank()) {
                tokenConsumer.accept(parsed.text());
            }
            return result(parsed.text(), requestStartedAt, requestStartedNanos,
                    completedNanos, completedNanos, false,
                    parsed.toolCalls(), parsed.finishReason(), parsed.usage(),
                    withRequestedMode(request, parsed.reasoningTelemetry()));
        }
        if (reasoningEvents > 0) {
            throw new LlmProviderException("SSE request " + requestId
                    + " completed without dialogue content after " + reasoningEvents
                    + " reasoning event(s); configure reasoningEffort=none or raise maxTokens. "
                    + "finishReason=" + valueOrOmitted(finishReason)
                    + " done=" + sawDone);
        }
        throw new LlmProviderException("SSE request " + requestId
                + " completed without dialogue tokens; events=" + eventCount
                + " finishReason=" + valueOrOmitted(finishReason)
                + " done=" + sawDone);
    }

    private LlmTimeoutException streamIdleTimeout(
            String requestId, StreamIdleGuard idleGuard, Throwable cause) {
        return new LlmTimeoutException(LlmTimeoutException.Phase.STREAM_IDLE,
                "SSE stream for request " + requestId + " was idle for "
                        + idleGuard.timeoutMillis() + " ms after HTTP response start",
                cause);
    }

    private CompletableFuture<LlmResult> sendNonStreaming(
            LlmRequest request,
            Consumer<String> tokenConsumer,
            Instant requestStartedAt,
            long requestStartedNanos,
            String requestId,
            RequestControl control) {
        int timeoutMillis = config.effectiveResponseStartTimeoutMillis();
        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(
                request(request, false, "application/json", true),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        control.transport(responseFuture);
        return responseFuture
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .handle((response, failure) -> {
                    if (control.cancelled()) throw new java.util.concurrent.CancellationException(
                            "Provider request cancelled before JSON completion: " + requestId);
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        if (cause instanceof TimeoutException
                                || (cause instanceof HttpTimeoutException
                                && !(cause instanceof HttpConnectTimeoutException))) {
                            throw new CompletionException(new LlmTimeoutException(
                                    LlmTimeoutException.Phase.NON_STREAMING_COMPLETION,
                                    "Non-streaming LLM response did not complete within "
                                            + timeoutMillis + " ms for request " + requestId,
                                    cause));
                        }
                        throw new CompletionException(cause);
                    }
                    long completedNanos = System.nanoTime();
                    log("LLM HTTP headers/body request=" + requestId
                            + " status=" + response.statusCode()
                            + " elapsedMs=" + elapsedMillis(
                                    requestStartedNanos, completedNanos));
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new LlmProviderException("LLM endpoint returned HTTP "
                                + response.statusCode() + ": "
                                + compact(response.body(), 400));
                    }
                    ParsedBody parsed = extractBody(response.body());
                    if (!parsed.text().isBlank()) {
                        tokenConsumer.accept(parsed.text());
                    }
                    return result(parsed.text(), requestStartedAt, requestStartedNanos,
                            completedNanos, completedNanos, false,
                            parsed.toolCalls(), parsed.finishReason(), parsed.usage(),
                            withRequestedMode(request, parsed.reasoningTelemetry()));
                });
    }

    private HttpRequest request(
            LlmRequest request, boolean stream, String accept, boolean applyCompletionTimeout) {
        OpenAiRequest body = new OpenAiRequest(config.model(), request.canonicalMessages(),
                request.temperatureOverride() == null ? config.temperature()
                        : request.temperatureOverride(),
                request.maxTokensOverride() == null ? config.maxTokens()
                        : request.maxTokensOverride(), stream,
                wireReasoningEffort(request),
                request.tools().isEmpty() ? null : request.tools(),
                toolChoice(request.tools()), stream ? streamOptions() : null,
                request.responseFormat());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.endpoint()))
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(JsonFiles.GSON.toJson(body),
                        StandardCharsets.UTF_8));
        if (applyCompletionTimeout) {
            builder.timeout(Duration.ofMillis(config.effectiveResponseStartTimeoutMillis()));
        }
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }
        return builder.build();
    }

    private JsonElement toolChoice(List<LlmToolDefinition> tools) {
        if (tools.isEmpty()) {
            return null;
        }
        if (toolChoicePolicy == ToolChoicePolicy.REQUIRED) {
            return new com.google.gson.JsonPrimitive("required");
        }
        if (toolChoicePolicy == ToolChoicePolicy.AUTO || tools.size() != 1) {
            return new com.google.gson.JsonPrimitive("auto");
        }
        JsonObject function = new JsonObject();
        function.addProperty("name", tools.getFirst().function().name());
        JsonObject choice = new JsonObject();
        choice.addProperty("type", "function");
        choice.add("function", function);
        return choice;
    }

    /** OpenAI-compatible wire policy; cognition/tool eligibility is identical across modes. */
    public enum ToolChoicePolicy {
        NAMED_SINGLE, REQUIRED, AUTO;
        public static ToolChoicePolicy parse(String value) {
            try {
                return valueOf(value == null ? "NAMED_SINGLE"
                        : value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Unsupported LLM toolChoiceMode: " + value);
            }
        }
    }

    private static LlmResult result(
            String text,
            Instant requestStartedAt,
            long requestStartedNanos,
            long firstTokenNanos,
            long completedNanos,
            boolean streaming,
            List<LlmToolCall> toolCalls,
            String finishReason,
            LlmUsage usage) {
        return result(text, requestStartedAt, requestStartedNanos, firstTokenNanos,
                completedNanos, streaming, toolCalls, finishReason, usage,
                LlmReasoningTelemetry.unknown());
    }

    private static LlmResult result(
            String text,
            Instant requestStartedAt,
            long requestStartedNanos,
            long firstTokenNanos,
            long completedNanos,
            boolean streaming,
            List<LlmToolCall> toolCalls,
            String finishReason,
            LlmUsage usage,
            LlmReasoningTelemetry reasoningTelemetry) {
        long firstToken = firstTokenNanos == 0 ? completedNanos : firstTokenNanos;
        return new LlmResult(text, new LlmLatency(requestStartedAt,
                elapsedMillis(requestStartedNanos, firstToken),
                elapsedMillis(requestStartedNanos, completedNanos), streaming),
                toolCalls, finishReason, usage == null ? LlmUsage.unknown() : usage,
                reasoningTelemetry);
    }

    private static JsonObject streamOptions() {
        JsonObject options = new JsonObject();
        options.addProperty("include_usage", true);
        return options;
    }

    private static SseEvent extractEvent(String payload) {
        try {
            JsonObject root = JsonFiles.GSON.fromJson(payload, JsonObject.class);
            JsonElement error = root == null ? null : root.get("error");
            if (error != null && !error.isJsonNull()) {
                throw new LlmProviderException("LLM streaming error: "
                        + compact(error.toString(), 400));
            }
            JsonElement choices = root == null ? null : root.get("choices");
            LlmUsage usage = extractUsage(root);
            if (choices == null || !choices.isJsonArray()
                    || choices.getAsJsonArray().isEmpty()) {
                return new SseEvent("", false, 0, null, List.of(), usage);
            }
            JsonObject choice = choices.getAsJsonArray().get(0).getAsJsonObject();
            String finishReason = optionalString(choice.get("finish_reason"));
            JsonObject delta = choice.getAsJsonObject("delta");
            if (delta == null) {
                return new SseEvent("", false, 0, finishReason, List.of(), usage);
            }
            String content = contentText(delta.get("content"));
            int reasoningCharacters = contentText(delta.get("reasoning")).length()
                    + contentText(delta.get("reasoning_content")).length();
            boolean reasoning = reasoningCharacters > 0;
            return new SseEvent(content, reasoning, reasoningCharacters, finishReason,
                    extractToolFragments(delta.get("tool_calls")), usage);
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LlmProviderException("Could not parse LLM SSE event: "
                    + compact(payload, 240), exception);
        }
    }

    private static List<ToolCallFragment> extractToolFragments(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<ToolCallFragment> fragments = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject call = value.getAsJsonObject();
            int index = call.has("index") ? call.get("index").getAsInt() : 0;
            JsonObject function = call.getAsJsonObject("function");
            fragments.add(new ToolCallFragment(index, optionalString(call.get("id")),
                    function == null ? null : optionalString(function.get("name")),
                    function == null ? null : optionalString(function.get("arguments"))));
        }
        return fragments;
    }

    private static String contentText(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return "";
        }
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        if (!content.isJsonArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray()) {
            if (part.isJsonPrimitive()) {
                text.append(part.getAsString());
            } else if (part.isJsonObject()) {
                JsonElement value = part.getAsJsonObject().get("text");
                if (value != null && !value.isJsonNull()) {
                    text.append(value.getAsString());
                }
            }
        }
        return text.toString();
    }

    private static boolean hasText(JsonElement element) {
        return element != null && !element.isJsonNull()
                && element.isJsonPrimitive() && !element.getAsString().isEmpty();
    }

    private static String optionalString(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    @Override
    public CompletableFuture<LlmProviderStatus> checkStatus() {
        URI modelsEndpoint;
        try {
            modelsEndpoint = modelsEndpoint(URI.create(config.endpoint()));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    config.endpoint(), config.model(), false, false, streamingEnabled(),
                    "Setup required: invalid endpoint URL: "
                            + compact(exception.getMessage(), 240)));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(modelsEndpoint)
                .timeout(Duration.ofMillis(config.requestTimeoutMillis()))
                .header("Accept", "application/json")
                .GET();
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + config.apiKey());
        }

        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8))
                .orTimeout(config.requestTimeoutMillis(), TimeUnit.MILLISECONDS)
                .handle((response, failure) -> {
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        return new LlmProviderStatus(config.endpoint(), config.model(),
                                hasConfiguredModel(), false, streamingEnabled(),
                                connectionReason(cause));
                    }
                    return statusFromResponse(modelsEndpoint, response);
                });
    }

    @Override
    public String description() {
        return "OpenAI-compatible " + config.endpoint() + " (model " + config.model()
                + ", streaming " + (streamingEnabled() ? "enabled" : "disabled")
                + ", reasoningEffort "
                + valueOrOmitted(config.configuredReasoningEffort()) + ")";
    }

    private LlmProviderStatus statusFromResponse(
            URI modelsEndpoint, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        boolean configured = hasConfiguredModel();
        if (statusCode < 200 || statusCode >= 300) {
            String explanation = switch (statusCode) {
                case 401, 403 -> "Authorization failed; check apiKey in config.json.";
                case 404 -> "The server answered, but it does not expose the OpenAI-compatible "
                        + "/v1/models route used by the health check.";
                default -> "Health check returned HTTP " + statusCode + ": "
                        + compact(response.body(), 240);
            };
            return new LlmProviderStatus(config.endpoint(), config.model(), configured, true,
                    streamingEnabled(), setupPrefix(configured) + explanation
                            + " Checked " + modelsEndpoint + ".");
        }

        try {
            JsonObject root = JsonFiles.GSON.fromJson(response.body(), JsonObject.class);
            JsonElement data = root == null ? null : root.get("data");
            if (data == null || !data.isJsonArray()) {
                return new LlmProviderStatus(config.endpoint(), config.model(), configured, true,
                        streamingEnabled(), setupPrefix(configured) + "Server answered HTTP "
                                + statusCode + ", but /v1/models returned no data array.");
            }
            List<String> models = data.getAsJsonArray().asList().stream()
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .map(object -> object.get("id"))
                    .filter(element -> element != null && !element.isJsonNull())
                    .map(JsonElement::getAsString)
                    .toList();
            String available = models.isEmpty() ? "none" : String.join(", ", models);
            if (!configured) {
                return new LlmProviderStatus(config.endpoint(), config.model(), false, true,
                        streamingEnabled(), "Server is reachable, but setup is required: replace "
                                + "the placeholder model in config.json with an exact model ID. "
                                + "Available: " + compact(available, 300));
            }
            if (models.contains(config.model())) {
                return new LlmProviderStatus(config.endpoint(), config.model(), true, true,
                        streamingEnabled(),
                        "Connected successfully; the configured model is available.");
            }
            return new LlmProviderStatus(config.endpoint(), config.model(), true, true,
                    streamingEnabled(),
                    "Server is reachable, but the configured model is not listed. Available: "
                            + compact(available, 300));
        } catch (RuntimeException exception) {
            return new LlmProviderStatus(config.endpoint(), config.model(), configured, true,
                    streamingEnabled(), setupPrefix(configured) + "Server answered HTTP "
                            + statusCode
                            + ", but its /v1/models response was not valid OpenAI-compatible JSON: "
                            + compact(exception.getMessage(), 200));
        }
    }

    private boolean hasConfiguredModel() {
        String model = config.model() == null ? "" : config.model().strip();
        String normalized = model.toLowerCase(Locale.ROOT);
        return !model.isBlank()
                && !normalized.equals("local-model")
                && !normalized.startsWith("change_me");
    }

    private static boolean supportsAutomaticFallback(int statusCode) {
        return statusCode == 400 || statusCode == 404 || statusCode == 405
                || statusCode == 415 || statusCode == 422 || statusCode == 501;
    }

    private static String setupPrefix(boolean configured) {
        return configured ? "" : "Setup required: replace the placeholder model in config.json. ";
    }

    private static URI modelsEndpoint(URI chatEndpoint) {
        String scheme = chatEndpoint.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || chatEndpoint.getHost() == null) {
            throw new IllegalArgumentException(
                    "endpoint must be an absolute http:// or https:// URL with a host");
        }
        String path = chatEndpoint.getPath();
        if (path == null) {
            path = "";
        }
        int versionSegment = path.lastIndexOf("/v1/");
        String modelsPath;
        if (versionSegment >= 0) {
            modelsPath = path.substring(0, versionSegment) + "/v1/models";
        } else if (path.endsWith("/v1")) {
            modelsPath = path + "/models";
        } else {
            modelsPath = path.endsWith("/") ? path + "v1/models" : path + "/v1/models";
        }
        try {
            return new URI(scheme, chatEndpoint.getUserInfo(),
                    chatEndpoint.getHost(), chatEndpoint.getPort(), modelsPath, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static String connectionReason(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return "No configured OpenAI-compatible endpoint answered. Verify ImmersiveNPCs "
                + "ai-providers.json/config.json and the local or remote inference service. "
                + "Connection error: "
                + compact(message, 240);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ParsedBody extractBody(String responseBody) {
        try {
            JsonObject root = JsonFiles.GSON.fromJson(responseBody, JsonObject.class);
            JsonElement error = root == null ? null : root.get("error");
            if (error != null && !error.isJsonNull()) {
                throw new LlmProviderException("LLM error: " + compact(error.toString(), 400));
            }
            JsonElement choices = root == null ? null : root.get("choices");
            if (choices == null || !choices.isJsonArray()
                    || choices.getAsJsonArray().isEmpty()) {
                throw new LlmProviderException("LLM response did not contain choices[0]");
            }
            JsonObject choice = choices.getAsJsonArray().get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null) {
                throw new LlmProviderException("LLM response did not contain choices[0].message");
            }
            String content = contentText(message.get("content"));
            List<LlmToolCall> toolCalls = extractToolCalls(message.get("tool_calls"));
            int reasoningCharacters = contentText(message.get("reasoning")).length()
                    + contentText(message.get("reasoning_content")).length();
            boolean reasoning = reasoningCharacters > 0;
            if (content.isBlank() && toolCalls.isEmpty()) {
                throw new LlmProviderException(reasoning
                        ? "LLM returned reasoning but no dialogue content; configure "
                                + "reasoningEffort=none or raise maxTokens"
                        : "LLM response contained empty message.content");
            }
            LlmUsage usage = extractUsage(root);
            LlmReasoningTelemetry telemetry = new LlmReasoningTelemetry(
                    "UNKNOWN", reasoning ? "ENABLED" : "DISABLED", reasoning,
                    reasoning ? 1 : 0, -1, estimatedTokens(content), false, -1);
            return new ParsedBody(content, toolCalls,
                    optionalString(choice.get("finish_reason")), usage, telemetry);
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LlmProviderException("Could not parse LLM JSON response", exception);
        }
    }

    private static List<LlmToolCall> extractToolCalls(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject object = value.getAsJsonObject();
            JsonObject function = object.getAsJsonObject("function");
            if (function == null) {
                continue;
            }
            calls.add(new LlmToolCall(optionalString(object.get("id")),
                    optionalString(function.get("name")),
                    optionalString(function.get("arguments"))));
        }
        return calls;
    }

    private static LlmUsage extractUsage(JsonObject root) {
        JsonObject usage = root == null ? null : root.getAsJsonObject("usage");
        if (usage == null) return null;
        int prompt = intValue(usage, "prompt_tokens");
        int completion = intValue(usage, "completion_tokens");
        int total = intValue(usage, "total_tokens");
        if (total <= 0) total = Math.max(0, prompt + completion);
        return new LlmUsage(prompt, completion, total, true);
    }

    private static int intValue(JsonObject object, String name) {
        try {
            JsonElement value = object.get(name);
            return value == null || value.isJsonNull() ? 0 : Math.max(0, value.getAsInt());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static LlmUsage estimateUsage(LlmRequest request, String completion) {
        int promptCharacters = request.messages().stream().mapToInt(
                value -> value.content() == null ? 0 : value.content().length()).sum();
        int prompt = Math.max(1, (promptCharacters + 3) / 4);
        int generated = Math.max(1, ((completion == null ? 0 : completion.length()) + 3) / 4);
        return new LlmUsage(prompt, generated, prompt + generated, false);
    }

    private String wireReasoningEffort(LlmRequest request) {
        return switch (request.executionPolicy().requestedReasoningMode()) {
            case DISABLED -> "none";
            // Foreground NPC decisions need bounded synthesis, not benchmark-depth thought.
            case ENABLED -> "low";
            case DEFAULT -> config.configuredReasoningEffort();
        };
    }

    private LlmRequest reasoningDisabledRecovery(LlmRequest request) {
        ArrayList<String> reasons = new ArrayList<>(
                request.executionPolicy().routeReasonCodes());
        reasons.add("REASONING_ONLY_STREAM_BUDGET_EXCEEDED");
        LlmExecutionPolicy recovery = new LlmExecutionPolicy(
                request.executionPolicy().reasoningPolicy() + "_RECOVERY",
                LlmExecutionPolicy.ReasoningMode.DISABLED, reasons,
                request.executionPolicy().finalAnswerTokenBudget());
        int finalBudget = Math.max(96,
                request.executionPolicy().finalAnswerTokenBudget());
        double temperature = request.temperatureOverride() == null
                ? config.temperature() : request.temperatureOverride();
        return request.withExecutionPolicy(recovery)
                .withGenerationParameters(temperature, finalBudget);
    }

    private static LlmResult recoveredReasoningResult(LlmRequest original,
            LlmResult result, int reasoningEvents) {
        LlmReasoningTelemetry finalTelemetry = result.reasoningTelemetry();
        LlmReasoningTelemetry recovered = new LlmReasoningTelemetry(
                original.executionPolicy().requestedReasoningMode().name(),
                "RECOVERED_WITH_REASONING_DISABLED", true, reasoningEvents, -1,
                finalTelemetry.finalAnswerTokenCount(),
                finalTelemetry.finalAnswerTokenCountExact(),
                finalTelemetry.promptEvaluationMillis());
        return new LlmResult(result.text(), result.latency(), result.toolCalls(),
                result.finishReason(), result.usage(), recovered);
    }

    private static LlmReasoningTelemetry reasoningTelemetry(LlmRequest request,
            int reasoningEvents, int reasoningCharacters, String finalText, LlmUsage usage) {
        boolean enabled = reasoningEvents > 0 || reasoningCharacters > 0;
        int finalTokens = !enabled && usage != null && usage.exact()
                ? usage.completionTokens() : estimatedTokens(finalText);
        return new LlmReasoningTelemetry(
                request.executionPolicy().requestedReasoningMode().name(),
                enabled ? "ENABLED" : "DISABLED", enabled, reasoningEvents,
                -1, finalTokens, !enabled && usage != null && usage.exact(), -1);
    }

    private static LlmReasoningTelemetry withRequestedMode(LlmRequest request,
            LlmReasoningTelemetry actual) {
        return new LlmReasoningTelemetry(
                request.executionPolicy().requestedReasoningMode().name(),
                actual.actualMode(), actual.thinkingEnabled(), actual.reasoningEventCount(),
                actual.reasoningTokenCount(), actual.finalAnswerTokenCount(),
                actual.finalAnswerTokenCountExact(), actual.promptEvaluationMillis());
    }

    private static int estimatedTokens(String text) {
        return Math.max(0, ((text == null ? 0 : text.length()) + 3) / 4);
    }

    private void log(String message) {
        try {
            diagnosticLog.accept(message);
        } catch (RuntimeException ignored) {
            // Diagnostic logging must never fail an LLM request.
        }
    }

    private CompletableFuture<Void> boundedDrain(CompletableFuture<Void> drain,
            UUID requestId) {
        CompletableFuture<Void> timeout = new CompletableFuture<>();
        ScheduledFuture<?> timer = STREAM_TIMEOUTS.schedule(() ->
                timeout.completeExceptionally(new LlmProviderException(
                        "Local provider remained DRAINING for 2500 ms before request "
                                + shortId(requestId))), 2_500, TimeUnit.MILLISECONDS);
        return drain.applyToEither(timeout, ignored -> (Void) null)
                .whenComplete((ignored, failure) -> timer.cancel(false));
    }

    private static long elapsedMillis(long start, long end) {
        return Duration.ofNanos(Math.max(0, end - start)).toMillis();
    }

    private static String compact(String text, int maximum) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum
                ? normalized : normalized.substring(0, maximum) + "...";
    }

    private static String shortId(java.util.UUID value) {
        String text = value.toString();
        return text.substring(0, 8);
    }

    private static String valueOrOmitted(String value) {
        return value == null || value.isBlank() ? "omitted" : value;
    }

    private static String diagnosticProbe(LlmRequest request) {
        if (request == null || request.messages() == null) {
            return null;
        }
        String user = request.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .reduce((first, second) -> second)
                .map(ChatMessage::content).orElse("").strip();
        String normalized = user.toLowerCase(Locale.ROOT)
                .replaceAll("[.!?]+$", "").strip();
        return normalized.equals("greetings") || normalized.equals("how are you")
                ? user : null;
    }

    private record OpenAiRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            int max_tokens,
            boolean stream,
            String reasoning_effort,
            List<LlmToolDefinition> tools,
            JsonElement tool_choice,
            JsonObject stream_options,
            JsonObject response_format) {
    }

    private record SseEvent(
            String content,
            boolean reasoning,
            int reasoningCharacters,
            String finishReason,
            List<ToolCallFragment> toolCalls,
            LlmUsage usage) {
    }

    private record ToolCallFragment(int index, String id, String name, String arguments) {
    }

    private record ParsedBody(
            String text, List<LlmToolCall> toolCalls, String finishReason, LlmUsage usage,
            LlmReasoningTelemetry reasoningTelemetry) {
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void append(ToolCallFragment fragment) {
            if (fragment.id() != null && !fragment.id().isBlank()) {
                id = fragment.id();
            }
            if (fragment.name() != null && !fragment.name().isBlank()) {
                name = name == null ? fragment.name() : name + fragment.name();
            }
            if (fragment.arguments() != null) {
                arguments.append(fragment.arguments());
            }
        }

        private LlmToolCall build() {
            return new LlmToolCall(id, name, arguments.toString());
        }
    }

    /** Owns the real HTTP transport independently from the caller-facing future. */
    private static final class RequestControl {
        private final UUID requestId;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<?>> transport = new AtomicReference<>();
        private final AtomicReference<InputStream> body = new AtomicReference<>();
        private final CompletableFuture<LlmResult> outward = new CompletableFuture<>();
        private final CompletableFuture<Void> drained = new CompletableFuture<>();

        private RequestControl(UUID requestId) { this.requestId = requestId; }
        private UUID requestId() { return requestId; }
        private boolean cancelled() { return cancelled.get(); }
        private CompletableFuture<LlmResult> outward() { return outward; }
        private CompletableFuture<Void> drained() { return drained; }

        private void attach(CompletableFuture<?> actual) {
            if (cancelled()) actual.cancel(true);
        }

        private void transport(CompletableFuture<?> value) {
            transport.set(value);
            if (cancelled()) value.cancel(true);
        }

        private void body(InputStream value) {
            body.set(value);
            if (cancelled()) closeBody(value);
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            outward.cancel(false);
            CompletableFuture<?> pending = transport.get();
            if (pending != null) pending.cancel(true);
            InputStream stream = body.get();
            if (stream != null) STREAM_TIMEOUTS.execute(() -> closeBody(stream));
        }

        private static void closeBody(InputStream value) {
            try { value.close(); }
            catch (IOException ignored) { }
        }
    }

    private static final class StreamIdleGuard implements AutoCloseable {
        private final InputStream body;
        private final int timeoutMillis;
        private final AtomicBoolean timedOut = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();

        private StreamIdleGuard(InputStream body, int timeoutMillis) {
            this.body = body;
            this.timeoutMillis = timeoutMillis;
        }

        private void activity() {
            ScheduledFuture<?> previous = scheduled.getAndSet(STREAM_TIMEOUTS.schedule(() -> {
                timedOut.set(true);
                try {
                    body.close();
                } catch (IOException ignored) {
                    // The reader observes either EOF or the close exception.
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS));
            if (previous != null) {
                previous.cancel(false);
            }
        }

        private boolean timedOut() {
            return timedOut.get();
        }

        private int timeoutMillis() {
            return timeoutMillis;
        }

        @Override
        public void close() {
            ScheduledFuture<?> pending = scheduled.getAndSet(null);
            if (pending != null) {
                pending.cancel(false);
            }
        }
    }

    private static final class StreamingUnsupportedException extends RuntimeException {
        private StreamingUnsupportedException(String message) {
            super(message);
        }
    }

    private static final class ReasoningOnlyBudgetExceeded extends RuntimeException {
        private final int reasoningEvents;

        private ReasoningOnlyBudgetExceeded(int reasoningEvents) {
            super("Reasoning-only stream exceeded " + reasoningEvents + " event(s)");
            this.reasoningEvents = reasoningEvents;
        }
    }
}
