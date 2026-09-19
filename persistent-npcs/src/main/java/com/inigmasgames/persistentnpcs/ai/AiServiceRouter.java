package com.inigmasgames.persistentnpcs.ai;

import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.ConversationModelRoutingProvider;
import com.inigmasgames.persistentnpcs.llm.ModelTier;
import com.inigmasgames.persistentnpcs.llm.LlmAttributionSource;
import com.inigmasgames.persistentnpcs.llm.LlmInferenceAttribution;
import com.inigmasgames.persistentnpcs.llm.SelectableLlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmRuntimeDiagnosticSource;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.voice.OpusClip;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;
import com.inigmasgames.persistentnpcs.voice.TextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.VoiceRenderPlan;
import com.inigmasgames.persistentnpcs.voice.LocalWorkerSpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.LocalWorkerTextToSpeechProvider;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.nio.file.Path;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.orbis.ResourceWorkload;
import com.inigmasgames.persistentnpcs.orbis.ResourceReclaimResult;

/** Central non-authoritative inference router. Game cognition never leaves the server. */
public final class AiServiceRouter implements AutoCloseable {
    private final RoutedStt stt;
    private final RoutedLlm llm;
    private final RoutedTts tts;
    private final Consumer<String> log;
    private final EnumMap<AiServiceKind, AtomicReference<AiProviderHealth>> health =
            new EnumMap<>(AiServiceKind.class);
    private final ScheduledExecutorService healthMonitor =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "immersive-ai-provider-health");
                thread.setDaemon(true);
                return thread;
            });
    private final RuntimeResourceMonitor runtimeResources;
    private final ConcurrentHashMap<UUID, LlmTurnRuntime> latestLlmRuntime =
            new ConcurrentHashMap<>();
    private volatile Supplier<JsonObject> startupTelemetry = JsonObject::new;
    private volatile Supplier<String> startupSummary = () -> "ORBIS INITIALIZING";

    public AiServiceRouter(
            SpeechToTextProvider sttPrimary, SpeechToTextProvider sttFallback,
            LlmProvider llmPrimary, LlmProvider llmFallback,
            TextToSpeechProvider ttsPrimary, TextToSpeechProvider ttsFallback,
            Consumer<String> log) {
        this.log = log == null ? ignored -> { } : log;
        stt = new RoutedStt(require(sttPrimary, "STT"), sttFallback);
        llm = new RoutedLlm(require(llmPrimary, "LLM"), llmFallback);
        tts = new RoutedTts(require(ttsPrimary, "TTS"), ttsFallback);
        runtimeResources = new RuntimeResourceMonitor(this::providerExpectations);
        for (AiServiceKind kind : AiServiceKind.values()) {
            health.put(kind, new AtomicReference<>(new AiProviderHealth(
                    AiProviderHealth.Status.UNKNOWN, "startup probe pending", Instant.now())));
        }
        healthMonitor.scheduleWithFixedDelay(this::probeAvailability,
                30, 30, TimeUnit.SECONDS);
    }

    public SpeechToTextProvider speechToText() { return stt; }
    /**
     * Orbis fail-closed adapter selection. It pins the configured primary STT backend
     * so one turn can never be silently retried on the legacy fallback provider.
     * Provider lifetime remains owned by this router.
     */
    public SpeechToTextProvider authoritativeSpeechToText() { return stt.primary; }

    public CompletableFuture<com.inigmasgames.persistentnpcs.voice.VoiceDraftAudio>
            decodeVoiceDraft(UUID requestId, List<byte[]> frames, int waveformBuckets) {
        if (stt.primary instanceof LocalWorkerSpeechToTextProvider local) {
            return local.decodeRecording(requestId, frames, waveformBuckets);
        }
        return CompletableFuture.failedFuture(new IllegalStateException(
                "The configured STT provider does not expose the local Opus decoder."));
    }

    public CompletableFuture<List<byte[]>> encodeSavedVoice(Path path) {
        if (stt.primary instanceof LocalWorkerSpeechToTextProvider local) {
            return local.encodeSavedWave(path);
        }
        return CompletableFuture.failedFuture(new IllegalStateException(
                "The configured STT provider does not expose the local Opus encoder."));
    }

    public CompletableFuture<com.inigmasgames.persistentnpcs.voice.VoiceDraftAudio>
            analyzeSavedVoice(Path path, int waveformBuckets) {
        if (stt.primary instanceof LocalWorkerSpeechToTextProvider local) {
            return local.analyzeSavedWave(path, waveformBuckets);
        }
        return CompletableFuture.failedFuture(new IllegalStateException(
                "The configured STT provider does not expose saved-WAV analysis."));
    }

    public CompletableFuture<Integer> invalidateVoiceConditioning(Path changedSample) {
        if (tts.primary instanceof LocalWorkerTextToSpeechProvider local) {
            return local.invalidateConditioningCache();
        }
        return CompletableFuture.completedFuture(0);
    }
    public LlmProvider languageModel() { return llm; }
    public TextToSpeechProvider textToSpeech() { return tts; }
    /** Orbis fail-closed TTS selection; no legacy/provider fallback is traversed. */
    public TextToSpeechProvider authoritativeTextToSpeech() { return tts.primary; }

    public void setStartupTelemetry(Supplier<JsonObject> telemetry,
            Supplier<String> summary) {
        startupTelemetry = telemetry == null ? JsonObject::new : telemetry;
        startupSummary = summary == null ? () -> "ORBIS INITIALIZING" : summary;
    }

    /** Ordered startup operations. Callers coordinate them asynchronously. */
    public CompletableFuture<JsonObject> bootstrapMoonshine() {
        if (stt.primary instanceof LocalWorkerSpeechToTextProvider local) {
            return local.bootstrapMoonshine();
        }
        return stt.primary.health().thenApply(value -> new JsonObject());
    }

    public CompletableFuture<JsonObject> warmMoonshine() {
        if (stt.primary instanceof LocalWorkerSpeechToTextProvider local) {
            return local.warmMoonshine();
        }
        return stt.primary.health().thenApply(value -> new JsonObject());
    }

    public CompletableFuture<Integer> unloadInactiveLanguageModels() {
        return llm.primary instanceof SelectableLlmProvider selectable
                ? selectable.unloadInactiveResidentModels()
                : CompletableFuture.completedFuture(0);
    }

    public CompletableFuture<Void> prepareAndWarmActiveLanguageModel() {
        return prepareActiveLanguageModelResidency()
                .thenCompose(ignored -> warmActiveLanguageModel());
    }

    public CompletableFuture<Void> prepareActiveLanguageModelResidency() {
        return llm.primary instanceof SelectableLlmProvider selectable
                ? selectable.ensureActiveResident() : CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> warmActiveLanguageModel() {
        return llm.primary.warmUp();
    }

    public String activeLanguageModelName() {
        return llm.primary instanceof SelectableLlmProvider selectable
                ? selectable.activeProviderName() : llm.primary.providerId();
    }

    public String activeLanguageModel() {
        return llm.primary instanceof SelectableLlmProvider selectable
                ? selectable.activeModel() : llm.primary.providerId();
    }

    public CompletableFuture<JsonObject> bootstrapChatterbox() {
        if (tts.primary instanceof LocalWorkerTextToSpeechProvider local) {
            return local.bootstrap();
        }
        return tts.primary.health().thenApply(value -> new JsonObject());
    }

    public CompletableFuture<JsonObject> warmChatterbox(List<Path> references) {
        if (tts.primary instanceof LocalWorkerTextToSpeechProvider local) {
            return bootstrapChatterbox()
                    .thenCompose(ignored -> local.ensureResident(references));
        }
        return tts.primary.health().thenApply(value -> new JsonObject());
    }

    /**
     * Proves that the post-warmup cached host state can admit both ordinary foreground
     * providers while preserving the configured Hytale reserve. Sampling is delayed and
     * asynchronous; no Hytale lifecycle or simulation thread is ever blocked.
     */
    public CompletableFuture<JsonObject> awaitSteadyStateHeadroom(long hytaleReserveMiB) {
        long started = System.nanoTime();
        AtomicInteger samples = new AtomicInteger();
        AtomicInteger consecutiveSafe = new AtomicInteger();
        AtomicInteger consecutiveUnsafe = new AtomicInteger();
        AtomicLong lastSampleEpochMillis = new AtomicLong(-1);
        AtomicLong minimumFree = new AtomicLong(Long.MAX_VALUE);
        AtomicInteger remediationAttempts = new AtomicInteger();
        AtomicReference<ResourceReclaimResult> remediationResult = new AtomicReference<>();
        return steadyStateSample(Math.max(0, hytaleReserveMiB), started, started, samples,
                consecutiveSafe, consecutiveUnsafe, lastSampleEpochMillis, minimumFree,
                remediationAttempts, remediationResult);
    }

    private CompletableFuture<JsonObject> steadyStateSample(long reserveMiB,
            long overallStarted, long samplingWindowStarted,
            AtomicInteger samples, AtomicInteger consecutiveSafe,
            AtomicInteger consecutiveUnsafe,
            AtomicLong lastSampleEpochMillis, AtomicLong minimumFree,
            AtomicInteger remediationAttempts,
            AtomicReference<ResourceReclaimResult> remediationResult) {
        return CompletableFuture.supplyAsync(() -> resourceSnapshot(),
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)).thenCompose(host -> {
            AiResourceRequirements llmRequirements = llm.primary.resourceRequirements();
            AiResourceRequirements ttsRequirements = tts.primary.resourceRequirements();
            long llmHeadroom = foregroundHeadroom(llmRequirements, reserveMiB);
            long ttsHeadroom = foregroundHeadroom(ttsRequirements, reserveMiB);
            // Connected Hytale allocation drifts after the warmup sample. Require a small
            // calibration margin at READY without weakening the scheduler's 512 MiB reserve.
            long readinessDriftMargin = requiredReadinessDriftMargin(
                    llmHeadroom, ttsHeadroom);
            long required = Math.max(llmHeadroom, ttsHeadroom) + readinessDriftMargin;
            long free = host == null ? -1 : host.vramFreeMiB();
            long sampledAt = host == null || host.at() == null ? -1 : host.at().toEpochMilli();
            boolean fresh = sampledAt > lastSampleEpochMillis.getAndSet(sampledAt);
            boolean measurable = required == 0 || free >= 0;
            boolean safe = measurable && (required == 0 || free >= required);
            samples.incrementAndGet();
            if (free >= 0) minimumFree.accumulateAndGet(free, Math::min);
            if (fresh && safe) {
                consecutiveSafe.incrementAndGet();
                consecutiveUnsafe.set(0);
            } else if (fresh) {
                consecutiveSafe.set(0);
                if (measurable) consecutiveUnsafe.incrementAndGet();
            }
            long now = System.nanoTime();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now - overallStarted);
            long samplingWindowElapsed = TimeUnit.NANOSECONDS.toMillis(
                    now - samplingWindowStarted);
            log.accept("ORBIS_STEADY_STATE sample=" + samples.get() + " fresh=" + fresh
                    + " freeVramMiB=" + free + " requiredVramMiB=" + required
                    + " llmRequiredMiB=" + llmHeadroom + " ttsRequiredMiB="
                    + ttsHeadroom + " hytaleReserveMiB=" + reserveMiB
                    + " readinessDriftMarginMiB=" + readinessDriftMargin
                    + " consecutiveSafe=" + consecutiveSafe.get() + " elapsedMs=" + elapsed);
            if (consecutiveSafe.get() >= 2 || required == 0) {
                return CompletableFuture.completedFuture(steadyStateReport(true, samples.get(),
                        minimumFree.get(), free, required, llmHeadroom, ttsHeadroom,
                        reserveMiB, elapsed, remediationAttempts.get() > 0
                                ? "MEASURED_SAFE_HEADROOM_AFTER_BOUNDED_REMEDIATION"
                                : "MEASURED_SAFE_HEADROOM",
                        remediationAttempts.get(), remediationResult.get()));
            }
            // The connected R072 failure left all three providers healthy but only 609 MiB
            // free after Chatterbox warmup. Use the existing one-way, measured Nemotron
            // pressure profile once before declaring startup degraded. This preserves both
            // Chatterbox residency and the 512 MiB Hytale reserve; it never invents headroom.
            if (fresh && measurable && !safe && consecutiveUnsafe.get() >= 2
                    && remediationAttempts.get() < 1) {
                int remediationAttempt = remediationAttempts.incrementAndGet();
                log.accept("ORBIS_STEADY_STATE_REMEDIATION requestedWorkload=LLM"
                        + " attempt=" + remediationAttempt + "/1"
                        + " reason=sustained-operating-envelope-pressure"
                        + " freeVramMiB=" + free + " requiredVramMiB=" + required
                        + " hytaleReserveMiB=" + reserveMiB);
                return reclaimResources(ResourceWorkload.LLM,
                        "sustained-operating-envelope-pressure").thenCompose(result -> {
                    remediationResult.set(result);
                    log.accept("ORBIS_STEADY_STATE_REMEDIATION action=" + result.action()
                            + " outcome=" + result.outcome()
                            + " resourcesChanged=" + result.resourcesChanged());
                    long nextWindow = samplingWindowStarted;
                    if (result.resourcesChanged()) {
                        // Provider unload/reload consumes part of the original settle window.
                        // Give the changed residency one bounded fresh window to stabilize.
                        nextWindow = System.nanoTime();
                        consecutiveSafe.set(0);
                        consecutiveUnsafe.set(0);
                        lastSampleEpochMillis.set(-1);
                    }
                    return steadyStateSample(reserveMiB, overallStarted, nextWindow,
                            samples, consecutiveSafe, consecutiveUnsafe,
                            lastSampleEpochMillis, minimumFree,
                            remediationAttempts, remediationResult);
                });
            }
            if (samplingWindowElapsed >= 12_000) {
                ResourceReclaimResult remediation = remediationResult.get();
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Post-warmup steady state cannot admit foreground providers: freeVramMiB="
                                + free + " requiredVramMiB=" + required
                                + " hytaleReserveMiB=" + reserveMiB
                                + " remediation=" + (remediation == null ? "NOT_AVAILABLE"
                                        : remediation.action() + "/" + remediation.outcome())));
            }
            return steadyStateSample(reserveMiB, overallStarted, samplingWindowStarted,
                    samples, consecutiveSafe, consecutiveUnsafe,
                    lastSampleEpochMillis, minimumFree,
                    remediationAttempts, remediationResult);
        });
    }

    private static long foregroundHeadroom(AiResourceRequirements requirements,
            long reserveMiB) {
        if (requirements == null || !requirements.placement().usesLocalGpu()) return 0;
        return requirements.incrementalVramMiB() + requirements.temporaryVramMiB()
                + reserveMiB;
    }

    static long requiredReadinessDriftMargin(long llmHeadroom, long ttsHeadroom) {
        return Math.max(llmHeadroom, ttsHeadroom) > 0 ? 64 : 0;
    }

    private static JsonObject steadyStateReport(boolean ready, int samples, long minimumFree,
            long lastFree, long required, long llmRequired, long ttsRequired, long reserve,
            long elapsed, String outcome, int remediationAttempts,
            ResourceReclaimResult remediation) {
        JsonObject value = new JsonObject();
        value.addProperty("steadyStateReady", ready);
        value.addProperty("samples", samples);
        value.addProperty("minimumFreeVramMiB",
                minimumFree == Long.MAX_VALUE ? -1 : minimumFree);
        value.addProperty("lastFreeVramMiB", lastFree);
        value.addProperty("requiredVramMiB", required);
        value.addProperty("nemotronForegroundHeadroomMiB", llmRequired);
        value.addProperty("chatterboxSynthesisHeadroomMiB", ttsRequired);
        value.addProperty("hytaleSafetyReserveMiB", reserve);
        value.addProperty("settleElapsedMs", elapsed);
        value.addProperty("outcome", outcome);
        value.addProperty("remediationAttempted", remediationAttempts > 0);
        value.addProperty("remediationAttempts", remediationAttempts);
        value.addProperty("remediationAction",
                remediation == null ? "NONE" : remediation.action());
        value.addProperty("remediationOutcome",
                remediation == null ? "NONE" : remediation.outcome());
        value.addProperty("remediationChangedResources",
                remediation != null && remediation.resourcesChanged());
        return value;
    }

    /**
     * Orbis fail-closed branch selection. The returned delegate is the exact
     * configured provider and never traverses RoutedLlm's optional fallback.
     */
    public PinnedLlmProvider pinLanguageModel() {
        if (llm.primary instanceof SelectableLlmProvider selectable) {
            return selectable.pinActive();
        }
        LlmProvider exact = llm.primary;
        return new PinnedLlmProvider(exact.providerId(), exact.providerId(),
                exact.backendDescription(), exact);
    }

    public CompletableFuture<SelectableLlmProvider.Selection> selectLlmProvider(String name) {
        if (!(llm.primary instanceof SelectableLlmProvider selectable)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Runtime LLM selection is not configured"));
        }
        return selectable.select(name).thenApply(selection -> {
            health.get(AiServiceKind.LANGUAGE_MODEL).set(AiProviderHealth.healthy(
                    "Selected exact model " + selection.model()));
            return selection;
        });
    }

    public Optional<SelectableLlmProvider.Selection> activeLlmSelection() {
        if (!(llm.primary instanceof SelectableLlmProvider selectable)) return Optional.empty();
        return Optional.of(new SelectableLlmProvider.Selection(
                selectable.activeProviderName(), selectable.activeModel(),
                selectable.activeEndpoint()));
    }

    public Optional<LlmInferenceAttribution> latestLlmAttribution(UUID npcId) {
        return llm.latestForNpc(npcId);
    }

    public Optional<LlmTurnRuntime> latestLlmRuntime(UUID npcId) {
        return Optional.ofNullable(latestLlmRuntime.get(npcId));
    }

    public String runtimeDiagnosticsText(UUID npcId) {
        String model = activeLlmSelection().map(SelectableLlmProvider.Selection::model)
                .orElse(llm.providerId());
        String turn = latestLlmRuntime(npcId).map(LlmTurnRuntime::display)
                .orElse("stage=IDLE\nNo LLM turn recorded for this NPC in this runtime.");
        return turn + "\n\n" + runtimeResources.latest().display(model);
    }

    /** Shared low-frequency sample used by Orbis; does not trigger a hardware poll. */
    public RuntimeResourceMonitor.Snapshot resourceSnapshot() {
        return runtimeResources.latest();
    }

    public com.inigmasgames.persistentnpcs.orbis.ConversationOperatingEnvelope
            conversationOperatingEnvelope(long hytaleReserveMiB) {
        String profile = llm.primary instanceof SelectableLlmProvider selectable
                ? selectable.activeHardwareProfile() : "UNMANAGED";
        return com.inigmasgames.persistentnpcs.orbis.ConversationOperatingEnvelope.measured(
                activeLanguageModel(), profile, hytaleReserveMiB,
                llm.primary.resourceRequirements(), tts.primary.resourceRequirements());
    }

    /** Explicit provider lifecycle reclaim. It never terminates an arbitrary process. */
    public CompletableFuture<ResourceReclaimResult> reclaimResources(
            ResourceWorkload requestedWorkload, String reason) {
        if (!(llm.primary instanceof SelectableLlmProvider selectable)) {
            return CompletableFuture.completedFuture(new ResourceReclaimResult(
                    "NO_SUPPORTED_PROVIDER_LIFECYCLE", "UNSUPPORTED", false));
        }
        if (requestedWorkload == ResourceWorkload.LLM) {
            if ("sustained-operating-envelope-pressure".equals(reason)) {
                return selectable.activateStartupSteadyStateProfile().thenApply(changed ->
                        new ResourceReclaimResult(
                                "NEMOTRON_PRESSURE_EPOCH_LOWER_MEMORY_PROFILE_"
                                        + "CHATTERBOX_PRESERVED",
                                "PROFILE=" + selectable.activeHardwareProfile()
                                        + ";CHATTERBOX_RESIDENCY=PRESERVED",
                                changed));
            }
            // Chatterbox reload cost is measured in seconds and unloading it here forces the
            // reciprocal Nemotron eviction before speech. Reclaim only inactive Ollama runners;
            // if the stable resident pair cannot fit, fail bounded instead of residency ping-pong.
            return selectable.unloadOwnedInactiveResidentModels().thenApply(inactive ->
                    new ResourceReclaimResult(
                            "OLLAMA_INACTIVE_MODELS_KEEP_ALIVE_ZERO_CHATTERBOX_PRESERVED",
                            "INACTIVE_MODELS_UNLOADED=" + inactive
                                    + ";CHATTERBOX_RESIDENCY=PRESERVED",
                            inactive > 0));
        }
        if (requestedWorkload == ResourceWorkload.TTS) {
            // Early phrase streaming may request TTS while the selected LLM is still actively
            // generating. A GPU gate/utilization/frame-pressure deferral is not permission to
            // evict that runner: doing so wedges the turn and forces a costly reload next turn.
            // Only measured VRAM headroom pressure after normal admission may reclaim residency.
            if (!ttsMayReclaimActiveLlm(reason)) {
                return CompletableFuture.completedFuture(new ResourceReclaimResult(
                        "DEFER_WITHOUT_ACTIVE_LLM_EVICTION",
                        "PRESSURE=" + reason + ";ACTIVE_MODEL_PRESERVED", false));
            }
            return selectable.unloadOwnedInactiveResidentModels().thenCompose(inactive ->
                    selectable.unloadActiveResidentModel().thenApply(active ->
                            new ResourceReclaimResult(
                                    "OLLAMA_INACTIVE_THEN_ACTIVE_KEEP_ALIVE_ZERO",
                                    "INACTIVE_MODELS_UNLOADED=" + inactive
                                            + ";ACTIVE_MODEL_UNLOADED=" + active,
                                    inactive > 0 || active)));
        }
        return CompletableFuture.completedFuture(new ResourceReclaimResult(
                "NO_SAFE_RECLAIM_FOR_" + requestedWorkload,
                "REQUEST_REMAINS_QUEUED", false));
    }

    static boolean ttsMayReclaimActiveLlm(String reason) {
        return "vram-headroom-pressure".equals(reason);
    }

    public void observeServerFrame(float deltaSeconds) {
        runtimeResources.observeServerFrame(deltaSeconds);
    }

    /** Cached, secret-free resource state for operator-gated traces. */
    public JsonObject runtimeResourceDiagnostics() {
        JsonObject value = new JsonObject();
        addResourceDiagnostics(value, runtimeResources.latest());
        JsonObject startup = safeStartupTelemetry();
        value.add("orbisStartup", startup);
        return value;
    }

    public JsonObject runtimeDiagnostics(UUID npcId) {
        JsonObject value = new JsonObject();
        latestLlmRuntime(npcId).ifPresent(turn -> {
            value.addProperty("at", turn.at().toString());
            value.addProperty("conversationId", String.valueOf(turn.conversationId()));
            value.addProperty("provider", turn.provider());
            value.addProperty("model", turn.model());
            value.addProperty("endpoint", turn.endpoint());
            value.addProperty("stage", turn.stage());
            value.addProperty("executionThread", turn.executionThread());
            value.addProperty("queueDepth", turn.queueDepth());
            value.addProperty("ttftMillis", turn.ttftMillis());
            value.addProperty("generationMillis", turn.totalGenerationMillis());
            value.addProperty("promptTokens", turn.promptTokens());
            value.addProperty("completionTokens", turn.completionTokens());
            value.addProperty("tokensPerSecond", turn.tokensPerSecond());
            value.addProperty("reasoningPolicy", turn.reasoningPolicy());
            value.addProperty("requestedReasoningMode", turn.requestedReasoningMode());
            value.addProperty("actualReasoningMode", turn.actualReasoningMode());
            value.addProperty("thinkingEnabled", turn.thinkingEnabled());
            value.addProperty("reasoningTokens", turn.reasoningTokens());
            value.addProperty("finalAnswerTokens", turn.finalAnswerTokens());
            value.addProperty("outputTokenBudget", turn.outputTokenBudget());
            value.addProperty("lastFailure", turn.lastFailure());
        });
        RuntimeResourceMonitor.Snapshot host = runtimeResources.latest();
        addResourceDiagnostics(value, host);
        value.addProperty("ollamaFlashAttention", environmentSetting(
                "OLLAMA_FLASH_ATTENTION", "DEFAULT_OFF_OR_SERVER_MANAGED"));
        value.addProperty("ollamaKvCacheType", environmentSetting(
                "OLLAMA_KV_CACHE_TYPE", "DEFAULT_F16_OR_SERVER_MANAGED"));
        value.addProperty("llmBackend", llm.backendDescription());
        return value;
    }

    private static String environmentSetting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private void addResourceDiagnostics(JsonObject value,
            RuntimeResourceMonitor.Snapshot host) {
        String model = activeLlmSelection().map(SelectableLlmProvider.Selection::model)
                .orElse(llm.providerId());
        value.addProperty("resourceSnapshotId",
                com.inigmasgames.persistentnpcs.diagnostics.ResourceSnapshotIdentity.id(host));
        value.addProperty("resourceSampleAt", host.at().toString());
        value.addProperty("systemCpuPercent", host.systemCpuPercent());
        value.addProperty("ramUsedMiB", host.ramUsedMiB());
        value.addProperty("ramTotalMiB", host.ramTotalMiB());
        value.addProperty("hytaleHeapUsedMiB", host.hytaleHeapUsedMiB());
        value.addProperty("hytaleHeapMaxMiB", host.hytaleHeapMaxMiB());
        value.addProperty("hytaleProcessCpuMillis", host.hytaleCpuMillis());
        value.addProperty("gpuUtilizationPercent", host.gpuUtilizationPercent());
        value.addProperty("vramUsedMiB", host.vramUsedMiB());
        value.addProperty("vramFreeMiB", host.vramFreeMiB());
        value.addProperty("vramTotalMiB", host.vramTotalMiB());
        value.addProperty("hytaleClientPresent", host.hytaleClientPresent());
        value.addProperty("chatterboxTtsPresent", host.chatterboxTtsPresent());
        value.add("gpuProcesses", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(host.gpuProcesses()));
        value.add("modelResidencies", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(host.modelResidencies()));
        java.util.LinkedHashMap<String, Long> providerEstimates = new java.util.LinkedHashMap<>();
        host.modelResidencies().forEach(residency -> providerEstimates.put(
                residency.provider(), residency.estimatedVramMiB()));
        value.add("orbisEstimatedVramByProvider",
                com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                        .toJsonTree(providerEstimates));
        value.add("residencyTransitions", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(host.residencyTransitions()));
        value.add("framePressure", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(host.framePressure()));
        value.addProperty("hytaleClientGpuVramMiB",
                host.allocationFor("HYTALE_CLIENT"));
        value.addProperty("hytaleServerGpuVramMiB",
                host.allocationFor("HYTALE_SERVER"));
        value.addProperty("perProcessGpuProbeStatus", host.perProcessGpuProbeStatus());
        value.addProperty("residency", host.display(model));
        value.addProperty("resourceProbeFailure", host.failure());
    }

    private List<RuntimeResourceMonitor.ProviderExpectation> providerExpectations() {
        java.util.ArrayList<RuntimeResourceMonitor.ProviderExpectation> values =
                new java.util.ArrayList<>();
        if (llm.primary instanceof SelectableLlmProvider selectable) {
            selectable.configuredProviders().forEach(value -> values.add(
                    new RuntimeResourceMonitor.ProviderExpectation(value.provider(),
                            value.model(), value.expectedResident(),
                            value.estimatedVramMiB())));
        } else {
            values.add(new RuntimeResourceMonitor.ProviderExpectation(llm.primary.providerId(),
                    llm.primary.providerId(), true,
                    llm.primary.resourceRequirements().estimatedVramMiB()));
        }
        AiResourceRequirements ttsRequirements = tts.primary.resourceRequirements();
        values.add(new RuntimeResourceMonitor.ProviderExpectation("CHATTERBOX",
                "chatterbox-turbo", true, ttsRequirements.estimatedVramMiB()));
        return List.copyOf(values);
    }

    /** Startup probes are asynchronous and can never stall the simulation thread. */
    public CompletableFuture<Void> probeAvailability() {
        return CompletableFuture.allOf(
                probe(AiServiceKind.SPEECH_TO_TEXT, stt.primary),
                probe(AiServiceKind.LANGUAGE_MODEL, llm.primary),
                probe(AiServiceKind.TEXT_TO_SPEECH, tts.primary));
    }

    public ProviderDiagnostic diagnostic(AiServiceKind kind) {
        AiProvider provider = switch (kind) {
            case SPEECH_TO_TEXT -> stt.activeProvider();
            case LANGUAGE_MODEL -> llm.activeProvider();
            case TEXT_TO_SPEECH -> tts.activeProvider();
        };
        AiProviderMetrics metrics = switch (kind) {
            case SPEECH_TO_TEXT -> stt.stats.snapshot();
            case LANGUAGE_MODEL -> llm.stats.snapshot();
            case TEXT_TO_SPEECH -> tts.stats.snapshot();
        };
        return new ProviderDiagnostic(kind, provider.providerId(), provider.executionMode(),
                provider.backendDescription(), health.get(kind).get(), metrics,
                switch (kind) {
                    case SPEECH_TO_TEXT -> stt.fallback == null ? "disabled" : "configured";
                    case LANGUAGE_MODEL -> llm.fallback == null ? "disabled" : "configured";
                    case TEXT_TO_SPEECH -> tts.fallback == null ? "disabled" : "configured";
                });
    }

    public List<ProviderDiagnostic> diagnostics() {
        return java.util.Arrays.stream(AiServiceKind.values()).map(this::diagnostic).toList();
    }

    public String diagnosticsText() {
        StringBuilder text = new StringBuilder(startupSummary.get());
        for (ProviderDiagnostic value : diagnostics()) {
            text.append("\n\n");
            AiProviderMetrics metrics = value.metrics();
            text.append(value.service()).append(": ").append(value.providerId())
                    .append(" [").append(value.mode()).append("]")
                    .append("\nbackend=").append(value.backend())
                    .append("\nhealth=").append(value.health().status()).append(" - ")
                    .append(value.health().detail())
                    .append("\nqueueDepth=").append(metrics.queueDepth())
                    .append(" active=").append(metrics.activeRequests())
                    .append(" concurrency=").append(metrics.concurrencyLimit())
                    .append("\nlatestTotalMs=").append(metrics.latestTotalLatencyMillis())
                    .append(" networkMs=").append(metrics.latestNetworkLatencyMillis())
                    .append(" inferenceMs=").append(metrics.latestInferenceLatencyMillis())
                    .append("\nfallback=").append(value.fallbackStatus())
                    .append(metrics.fallbackActive() ? " (ACTIVE)" : "")
                    .append(metrics.latestFailure().isBlank() ? ""
                            : "\nlatestFailure=" + metrics.latestFailure());
        }
        return text.toString();
    }

    private JsonObject safeStartupTelemetry() {
        try {
            JsonObject value = startupTelemetry.get();
            return value == null ? new JsonObject() : value.deepCopy();
        } catch (RuntimeException failure) {
            JsonObject value = new JsonObject();
            value.addProperty("state", "DEGRADED");
            value.addProperty("failure", root(failure));
            return value;
        }
    }

    private CompletableFuture<Void> probe(AiServiceKind kind, AiProvider provider) {
        return provider.health().orTimeout(5, TimeUnit.SECONDS).handle((value, failure) -> {
            AiProviderHealth result = failure == null ? value
                    : AiProviderHealth.unavailable(root(failure));
            health.get(kind).set(result);
            log.accept("AI_PROVIDER_PROBE service=" + kind + " provider="
                    + provider.providerId() + " mode=" + provider.executionMode()
                    + " health=" + result.status() + " detail=" + result.detail());
            return null;
        });
    }

    private void fallback(AiServiceKind kind, AiProvider primary, AiProvider backup,
            Throwable failure) {
        log.accept("AI_PROVIDER_FALLBACK service=" + kind + " primary="
                + primary.providerId() + " fallback=" + backup.providerId()
                + " reason=" + root(failure));
    }

    @Override public void close() {
        healthMonitor.shutdownNow();
        runtimeResources.close();
        stt.close();
        llm.close();
        tts.close();
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " primary provider required");
        return value;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String root(Throwable failure) {
        Throwable current = unwrap(failure);
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    public record ProviderDiagnostic(
            AiServiceKind service,
            String providerId,
            ProviderExecutionMode mode,
            String backend,
            AiProviderHealth health,
            AiProviderMetrics metrics,
            String fallbackStatus) { }

    /** Latest observable inference facts; never contains hidden model reasoning. */
    public record LlmTurnRuntime(Instant at, UUID conversationId, String provider, String model,
            String endpoint, String stage, String executionThread, int queueDepth,
            long ttftMillis, long totalGenerationMillis, int promptTokens,
            int completionTokens, double tokensPerSecond, String reasoningPolicy,
            String requestedReasoningMode, String actualReasoningMode,
            boolean thinkingEnabled, int reasoningTokens, int finalAnswerTokens,
            int outputTokenBudget, String lastFailure) {
        public String display() {
            return "at=" + at + "\nconversationId=" + conversationId
                    + "\nprovider=" + provider
                    + "\nmodel=" + model + "\nendpoint=" + endpoint + "\nstage=" + stage
                    + " thread=" + executionThread + "\nqueueDepth=" + queueDepth
                    + "\nTTFT=" + ttftMillis + "ms generation="
                    + totalGenerationMillis + "ms"
                    + "\npromptTokens=" + promptTokens + " completionTokens="
                    + completionTokens + " tokensPerSecond="
                    + "%.2f".formatted(tokensPerSecond)
                    + "\nreasoningPolicy=" + reasoningPolicy + " requested="
                    + requestedReasoningMode + " actual=" + actualReasoningMode
                    + " thinkingEnabled=" + thinkingEnabled
                    + "\nreasoningTokens=" + reasoningTokens
                    + " finalAnswerTokens=" + finalAnswerTokens
                    + " outputBudget=" + outputTokenBudget
                    + (lastFailure == null || lastFailure.isBlank() ? ""
                            : "\nlastFailure=" + lastFailure);
        }
    }

    private final class RoutedStt implements SpeechToTextProvider {
        private final SpeechToTextProvider primary;
        private final SpeechToTextProvider fallback;
        private final Stats stats;
        private final ConcurrentHashMap<UUID, SpeechToTextProvider> streams =
                new ConcurrentHashMap<>();
        private final AtomicReference<SpeechToTextProvider> latest = new AtomicReference<>();

        RoutedStt(SpeechToTextProvider primary, SpeechToTextProvider fallback) {
            this.primary = primary;
            this.fallback = fallback;
            stats = new Stats(primary.concurrencyLimit());
            latest.set(primary);
        }
        SpeechToTextProvider activeProvider() { return latest.get(); }
        @Override public CompletableFuture<SpeechTranscript> transcribe(
                UUID requestId, List<byte[]> frames) {
            long started = stats.begin(primary.providerId(), false);
            CompletableFuture<SpeechTranscript> first = primary.transcribe(requestId, frames);
            return first.handle((value, failure) -> {
                if (failure == null) return CompletableFuture.completedFuture(value);
                if (fallback == null) return CompletableFuture.<SpeechTranscript>failedFuture(
                        unwrap(failure));
                stats.failed(failure);
                fallback(AiServiceKind.SPEECH_TO_TEXT, primary, fallback, failure);
                latest.set(fallback);
                stats.fallback.set(true);
                return fallback.transcribe(requestId, frames);
            }).thenCompose(value -> value).whenComplete((value, failure) ->
                    stats.end(started, failure, value == null ? 0 : value.whisperMillis(),
                            latest.get().executionMode()));
        }
        @Override public boolean streamingTranscriptionEnabled() {
            return primary.streamingTranscriptionEnabled()
                    || fallback != null && fallback.streamingTranscriptionEnabled();
        }
        @Override public CompletableFuture<Void> startStream(UUID sessionId) {
            long started = stats.begin(primary.providerId(), false);
            return primary.startStream(sessionId).handle((unused, failure) -> {
                if (failure == null) {
                    streams.put(sessionId, primary);
                    return CompletableFuture.<Void>completedFuture(null);
                }
                if (fallback == null) return CompletableFuture.<Void>failedFuture(unwrap(failure));
                stats.failed(failure);
                fallback(AiServiceKind.SPEECH_TO_TEXT, primary, fallback, failure);
                latest.set(fallback);
                stats.fallback.set(true);
                return fallback.startStream(sessionId).thenRun(() -> streams.put(sessionId, fallback));
            }).thenCompose(value -> value).whenComplete((value, failure) ->
                    stats.end(started, failure, 0, latest.get().executionMode()));
        }
        @Override public CompletableFuture<String> appendStream(
                UUID sessionId, List<byte[]> frames) {
            SpeechToTextProvider selected = streams.getOrDefault(sessionId, primary);
            return selected.appendStream(sessionId, frames);
        }
        @Override public CompletableFuture<SpeechTranscript> finishStream(UUID sessionId) {
            SpeechToTextProvider selected = streams.remove(sessionId);
            return (selected == null ? primary : selected).finishStream(sessionId);
        }
        @Override public void cancel(UUID id) {
            primary.cancel(id);
            if (fallback != null) fallback.cancel(id);
            streams.remove(id);
        }
        @Override public String providerId() { return latest.get().providerId(); }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
        @Override public ProviderExecutionMode executionMode() {
            return latest.get().executionMode();
        }
        @Override public AiProviderCapabilities capabilities() {
            return primary.capabilities();
        }
        @Override public CompletableFuture<AiProviderHealth> health() { return primary.health(); }
        @Override public AiProviderMetrics metrics() { return stats.snapshot(); }
        @Override public int concurrencyLimit() { return primary.concurrencyLimit(); }
        @Override public String backendDescription() { return latest.get().backendDescription(); }
        @Override public boolean available() { return primary.available() || fallback != null && fallback.available(); }
        @Override public void close() {
            primary.close();
            if (fallback != null) fallback.close();
        }
    }

    private final class RoutedLlm implements LlmProvider, ConversationModelRoutingProvider,
            LlmAttributionSource, LlmRuntimeDiagnosticSource {
        private final LlmProvider primary;
        private final LlmProvider fallback;
        private final Stats stats;
        private final AtomicReference<LlmProvider> latest = new AtomicReference<>();
        RoutedLlm(LlmProvider primary, LlmProvider fallback) {
            this.primary = primary;
            this.fallback = fallback;
            stats = new Stats(primary.concurrencyLimit());
            latest.set(primary);
        }
        LlmProvider activeProvider() { return latest.get(); }
        @Override public ModelTier selectTier(
                ConversationSession session, NpcProfile profile, String playerMessage) {
            return primary instanceof ConversationModelRoutingProvider router
                    ? router.selectTier(session, profile, playerMessage) : ModelTier.GENERIC;
        }
        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return generateResponse(request, ignored -> { });
        }
        @Override public CompletableFuture<LlmResult> generateResponse(
                LlmRequest request, Consumer<String> consumer) {
            long started = stats.begin(primary.providerId(), false);
            SelectableLlmProvider.Selection selected = activeLlmSelection().orElse(
                    new SelectableLlmProvider.Selection(primary.providerId(),
                            primary.providerId(), primary.backendDescription()));
            latestLlmRuntime.put(request.npcId(), new LlmTurnRuntime(Instant.now(),
                    request.providerRequestId(), selected.provider(), selected.model(),
                    selected.endpoint(), "DISPATCHED", Thread.currentThread().getName(),
                    stats.snapshot().queueDepth(), 0, 0, 0, 0, 0,
                    request.executionPolicy().reasoningPolicy(),
                    request.executionPolicy().requestedReasoningMode().name(), "PENDING",
                    request.executionPolicy().thinkingEnabled(), -1, 0,
                    request.executionPolicy().finalAnswerTokenBudget(), ""));
            AtomicBoolean emitted = new AtomicBoolean();
            Consumer<String> tracking = value -> {
                if (value != null && !value.isEmpty()) emitted.set(true);
                consumer.accept(value);
            };
            return primary.generateResponse(request, tracking).handle((value, failure) -> {
                if (failure == null) return CompletableFuture.completedFuture(value);
                if (fallback == null || emitted.get()) {
                    return CompletableFuture.<LlmResult>failedFuture(unwrap(failure));
                }
                stats.failed(failure);
                fallback(AiServiceKind.LANGUAGE_MODEL, primary, fallback, failure);
                latest.set(fallback);
                stats.fallback.set(true);
                return fallback.generateResponse(request, consumer);
            }).thenCompose(value -> value).whenComplete((value, failure) -> {
                long inference = value == null ? 0 : value.latency().completionMillis();
                stats.end(started, failure, inference, latest.get().executionMode());
                LlmInferenceAttribution attribution = attribution(request.providerRequestId())
                        .orElse(null);
                String providerName = attribution == null ? selected.provider()
                        : attribution.provider();
                String modelName = attribution == null ? selected.model() : attribution.model();
                String endpoint = attribution == null ? selected.endpoint()
                        : attribution.endpoint();
                com.inigmasgames.persistentnpcs.llm.LlmUsage usage = value == null
                        || value.usage() == null
                                ? com.inigmasgames.persistentnpcs.llm.LlmUsage.unknown()
                                : value.usage();
                long total = value == null || value.latency() == null ? 0
                        : value.latency().completionMillis();
                long ttft = value == null || value.latency() == null ? 0
                        : value.latency().timeToFirstTokenMillis();
                String failureText = failure == null ? "" : root(failure);
                com.inigmasgames.persistentnpcs.llm.LlmReasoningTelemetry reasoning =
                        value == null ? com.inigmasgames.persistentnpcs.llm
                                .LlmReasoningTelemetry.unknown() : value.reasoningTelemetry();
                latestLlmRuntime.put(request.npcId(), new LlmTurnRuntime(Instant.now(),
                        request.providerRequestId(), providerName, modelName, endpoint,
                        failure == null ? "COMPLETED" : "FAILED",
                        Thread.currentThread().getName(), stats.snapshot().queueDepth(), ttft,
                        total, usage.promptTokens(), usage.completionTokens(),
                        usage.tokensPerSecond(Math.max(1, total - ttft)),
                        request.executionPolicy().reasoningPolicy(),
                        reasoning.requestedMode(), reasoning.actualMode(),
                        reasoning.thinkingEnabled(), reasoning.reasoningTokenCount(),
                        reasoning.finalAnswerTokenCount(),
                        request.executionPolicy().finalAnswerTokenBudget(), failureText));
            });
        }
        @Override public boolean streamingEnabled() { return primary.streamingEnabled(); }
        @Override public CompletableFuture<Void> warmUp() { return primary.warmUp(); }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return primary.checkStatus();
        }
        @Override public String description() { return "AI router: " + backendDescription(); }
        @Override public void cancel(UUID id) {
            primary.cancel(id);
            if (fallback != null) fallback.cancel(id);
        }
        @Override public void endSession(UUID sessionId) {
            primary.endSession(sessionId);
            if (fallback != null) fallback.endSession(sessionId);
        }
        @Override public String providerId() { return latest.get().providerId(); }
        @Override public ProviderExecutionMode executionMode() { return latest.get().executionMode(); }
        @Override public AiProviderCapabilities capabilities() { return primary.capabilities(); }
        @Override public CompletableFuture<AiProviderHealth> health() { return primary.health(); }
        @Override public AiProviderMetrics metrics() { return stats.snapshot(); }
        @Override public int concurrencyLimit() { return primary.concurrencyLimit(); }
        @Override public String backendDescription() { return latest.get().backendDescription(); }
        @Override public Optional<LlmInferenceAttribution> attribution(UUID conversationId) {
            return primary instanceof LlmAttributionSource source
                    ? source.attribution(conversationId) : Optional.empty();
        }
        @Override public Optional<LlmInferenceAttribution> latestForNpc(UUID npcId) {
            return primary instanceof LlmAttributionSource source
                    ? source.latestForNpc(npcId) : Optional.empty();
        }
        @Override public JsonObject runtimeDiagnostics(UUID npcId) {
            return AiServiceRouter.this.runtimeDiagnostics(npcId);
        }
        @Override public void close() {
            primary.close();
            if (fallback != null) fallback.close();
        }
    }

    private final class RoutedTts implements TextToSpeechProvider {
        private final TextToSpeechProvider primary;
        private final TextToSpeechProvider fallback;
        private final Stats stats;
        private final AtomicReference<TextToSpeechProvider> latest = new AtomicReference<>();
        private final ConcurrentHashMap<UUID, java.util.Set<UUID>> responseRequests =
                new ConcurrentHashMap<>();
        RoutedTts(TextToSpeechProvider primary, TextToSpeechProvider fallback) {
            this.primary = primary;
            this.fallback = fallback;
            stats = new Stats(primary.concurrencyLimit());
            latest.set(primary);
        }
        TextToSpeechProvider activeProvider() { return latest.get(); }
        @Override public CompletableFuture<OpusClip> synthesize(UUID requestId, UUID responseId,
                VoiceRenderPlan plan, String text) {
            responseRequests.computeIfAbsent(responseId,
                    ignored -> ConcurrentHashMap.newKeySet()).add(requestId);
            long started = stats.begin(primary.providerId(), false);
            return primary.synthesize(requestId, responseId, plan, text)
                    .handle((value, failure) -> {
                        if (failure == null) return CompletableFuture.completedFuture(value);
                        if (fallback == null) return CompletableFuture.<OpusClip>failedFuture(
                                unwrap(failure));
                        stats.failed(failure);
                        fallback(AiServiceKind.TEXT_TO_SPEECH, primary, fallback, failure);
                        latest.set(fallback);
                        stats.fallback.set(true);
                        return fallback.synthesize(requestId, responseId, plan, text);
                    }).thenCompose(value -> value).whenComplete((value, failure) -> {
                        java.util.Set<UUID> requests = responseRequests.get(responseId);
                        if (requests != null) {
                            requests.remove(requestId);
                            if (requests.isEmpty()) responseRequests.remove(responseId, requests);
                        }
                        stats.end(started, failure, value == null ? 0 : value.ttsMillis(),
                                latest.get().executionMode());
                    });
        }
        @Override public void cancel(UUID responseId) {
            java.util.Set<UUID> requests = responseRequests.remove(responseId);
            primary.cancel(responseId);
            if (fallback != null) fallback.cancel(responseId);
            if (requests != null) requests.forEach(id -> {
                primary.cancel(id);
                if (fallback != null) fallback.cancel(id);
            });
        }
        @Override public String providerId() { return latest.get().providerId(); }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
        @Override public ProviderExecutionMode executionMode() { return latest.get().executionMode(); }
        @Override public AiProviderCapabilities capabilities() { return primary.capabilities(); }
        @Override public CompletableFuture<AiProviderHealth> health() { return primary.health(); }
        @Override public AiProviderMetrics metrics() { return stats.snapshot(); }
        @Override public int concurrencyLimit() { return primary.concurrencyLimit(); }
        @Override public String backendDescription() { return latest.get().backendDescription(); }
        @Override public boolean available() { return primary.available() || fallback != null && fallback.available(); }
        @Override public void close() {
            primary.close();
            if (fallback != null) fallback.close();
        }
    }

    private static final class Stats {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger queue = new AtomicInteger();
        private final int concurrency;
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong networkMs = new AtomicLong();
        private final AtomicLong inferenceMs = new AtomicLong();
        private final AtomicReference<String> failure = new AtomicReference<>("");
        private final AtomicReference<String> provider = new AtomicReference<>("");
        private final AtomicBoolean fallback = new AtomicBoolean();
        Stats(int concurrency) { this.concurrency = Math.max(1, concurrency); }
        long begin(String providerId, boolean fallbackUsed) {
            requests.incrementAndGet();
            int running = active.incrementAndGet();
            queue.set(Math.max(0, running - concurrency));
            provider.set(providerId);
            fallback.set(fallbackUsed);
            return System.nanoTime();
        }
        void failed(Throwable value) {
            failures.incrementAndGet();
            failure.set(root(value));
        }
        void end(long started, Throwable value, long inference,
                ProviderExecutionMode mode) {
            long total = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            totalMs.set(total);
            inferenceMs.set(Math.max(0, inference));
            networkMs.set(mode == ProviderExecutionMode.REMOTE
                    ? Math.max(0, total - Math.max(0, inference)) : 0);
            if (value != null) failed(value);
            int running = Math.max(0, active.decrementAndGet());
            queue.set(Math.max(0, running - concurrency));
        }
        AiProviderMetrics snapshot() {
            return new AiProviderMetrics(requests.get(), failures.get(), active.get(),
                    queue.get(), concurrency, totalMs.get(), networkMs.get(),
                    inferenceMs.get(), failure.get(), provider.get(), fallback.get());
        }
    }
}
