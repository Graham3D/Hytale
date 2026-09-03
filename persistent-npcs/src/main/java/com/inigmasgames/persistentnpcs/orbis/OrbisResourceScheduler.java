package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.diagnostics.ResourceSnapshotIdentity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sole production admission authority for expensive Orbis inference workloads.
 * All state mutation is serialized; callers receive futures and never block Hytale threads.
 */
public final class OrbisResourceScheduler implements AutoCloseable {
    public enum OperatingState { STARTING, READY, PRESSURE, RECOVERING,
        DEGRADED_READY, ERROR }
    private static final int MAX_RECENT_EVENTS = 24;
    private static final long PRESSURE_REEVALUATION_MILLIS = 200;
    private static final long ENVELOPE_SAMPLE_MILLIS = 1_000;
    private static final int ENVELOPE_HYSTERESIS_SAMPLES = 3;
    private static final long FIRST_PHRASE_OVERLAP_MARGIN_MIB = 128;
    private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(
            task -> Thread.ofPlatform().daemon(true).name("orbis-resource-scheduler")
                    .unstarted(task));
    private final OrbisResourceConfig config;
    private final Supplier<RuntimeResourceMonitor.Snapshot> telemetry;
    private final Consumer<String> log;
    private final Consumer<ResourcePolicy> policyPersistence;
    private final ResourceReclaimer reclaimer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile ResourcePolicy activePolicy;
    private volatile Supplier<ConversationOperatingEnvelope> envelopeSupplier;
    private volatile OrbisReadinessService readiness;
    private volatile com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel
            degradationSentinel;
    private volatile OperatingState operatingState = OperatingState.STARTING;
    private volatile OperatingEnvelopeSnapshot operatingEnvelope =
            OperatingEnvelopeSnapshot.starting();

    // Control-thread state only.
    private final List<Pending> pending = new ArrayList<>();
    private final Map<UUID, Active> active = new LinkedHashMap<>();
    private final ArrayDeque<OrbisResourceEvent> recentEvents = new ArrayDeque<>();
    private long sequence;
    private boolean reevaluationScheduled;
    private boolean envelopeMonitoring;
    private volatile boolean envelopeRemediationInFlight;
    private boolean degradedProfileActive;
    private int unsafeEnvelopeSamples;
    private int safeEnvelopeSamples;
    private int pressureEpoch;
    private int remediationAttemptsThisEpoch;
    private long lastEnvelopeSampleEpochMillis = -1;
    private String latestResourceSnapshotId;
    private RuntimeResourceMonitor.Snapshot latestMaterialSnapshot;
    private RuntimeResourceMonitor.Snapshot identityCachedSnapshot;
    private String identityCachedSnapshotId;
    private volatile Snapshot snapshot;

    public OrbisResourceScheduler(OrbisResourceConfig config,
            Supplier<RuntimeResourceMonitor.Snapshot> telemetry, Consumer<String> log) {
        this(config, telemetry, log, ignored -> { });
    }

    public OrbisResourceScheduler(OrbisResourceConfig config,
            Supplier<RuntimeResourceMonitor.Snapshot> telemetry, Consumer<String> log,
            Consumer<ResourcePolicy> policyPersistence) {
        this(config, telemetry, log, policyPersistence, ResourceReclaimer.unavailable());
    }

    public OrbisResourceScheduler(OrbisResourceConfig config,
            Supplier<RuntimeResourceMonitor.Snapshot> telemetry, Consumer<String> log,
            Consumer<ResourcePolicy> policyPersistence, ResourceReclaimer reclaimer) {
        this.config = (config == null ? OrbisResourceConfig.defaults() : config).validated();
        this.telemetry = telemetry == null ? () -> null : telemetry;
        this.log = log == null ? ignored -> { } : log;
        this.policyPersistence = policyPersistence == null ? ignored -> { } : policyPersistence;
        this.reclaimer = reclaimer == null ? ResourceReclaimer.unavailable() : reclaimer;
        this.activePolicy = this.config.policy();
        snapshot = Snapshot.empty(activePolicy);
    }

    public void configureConversationOperatingEnvelope(
            Supplier<ConversationOperatingEnvelope> supplier,
            OrbisReadinessService readinessService) {
        this.envelopeSupplier = supplier;
        this.readiness = readinessService;
    }

    public void setDegradationSentinel(
            com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel sentinel) {
        this.degradationSentinel = sentinel;
    }

    /** Begins low-frequency checks only after startup has proven its initial steady state. */
    public void startConversationOperatingEnvelope() {
        execute(() -> {
            if (envelopeMonitoring) return;
            envelopeMonitoring = true;
            transitionOperatingState(OperatingState.READY,
                    "Startup steady state proven");
            sampleOperatingEnvelope();
        });
    }

    public boolean conversationServiceable() {
        OperatingState value = operatingState;
        if (value == OperatingState.READY || value == OperatingState.DEGRADED_READY) {
            return true;
        }
        OperatingEnvelopeSnapshot envelope = operatingEnvelope;
        return value == OperatingState.PRESSURE
                && !envelopeRemediationInFlight
                && envelope.freeVramMiB() >= envelope.immediateRequiredMiB();
    }

    public OperatingEnvelopeSnapshot operatingEnvelope() { return operatingEnvelope; }

    public String operatingEnvelopeSummary() {
        OperatingEnvelopeSnapshot value = operatingEnvelope;
        return "conversationOperatingState=" + value.state()
                + " profile=" + value.hardwareProfile()
                + " freeVramMiB=" + value.freeVramMiB()
                + " immediateRequiredMiB=" + value.immediateRequiredMiB()
                + " sustainableRequiredMiB=" + value.sustainableRequiredMiB()
                + " pressureEpoch=" + value.pressureEpoch()
                + " remediationAttempts=" + value.remediationAttempts()
                + " detail=" + value.detail();
    }

    /** Changes admission for future requests only; queued/active jobs keep their captured policy. */
    public CompletableFuture<ResourcePolicy> selectPolicy(ResourcePolicy policy) {
        if (policy == null || closed.get()) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Active Orbis resource policy required"));
        CompletableFuture<ResourcePolicy> result = new CompletableFuture<>();
        execute(() -> {
            try {
                policyPersistence.accept(policy);
                activePolicy = policy;
                refreshSnapshot();
                log.accept("ORBIS_RESOURCE_POLICY selected=" + policy
                        + " appliesTo=future-requests");
                result.complete(policy);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public CompletableFuture<Lease> admit(OrbisResourceRequest request,
            Consumer<OrbisResourceEvent> observer) {
        if (request == null || closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Orbis resource scheduler is closed"));
        }
        CompletableFuture<Lease> future = new CompletableFuture<>();
        execute(() -> admitOnControl(request, observer == null ? ignored -> { } : observer,
                future));
        future.whenComplete((lease, failure) -> {
            if (future.isCancelled()) cancel(request.requestId(), "future-cancelled");
        });
        return future;
    }

    public void cancel(UUID requestId, String reason) {
        if (requestId == null || closed.get()) return;
        execute(() -> cancelOnControl(requestId, reason));
    }

    private void admitOnControl(OrbisResourceRequest request,
            Consumer<OrbisResourceEvent> observer, CompletableFuture<Lease> future) {
        if (active.containsKey(request.requestId()) || pending.stream().anyMatch(value ->
                value.request.requestId().equals(request.requestId()))) {
            future.completeExceptionally(new IllegalStateException(
                    "Duplicate Orbis resource request " + request.requestId()));
            return;
        }
        AiResourceRequirements requirements = request.provider().resourceRequirements();
        ExecutionPlacement placement = resolvePlacement(request, requirements);
        Pending item = new Pending(++sequence, request, requirements, placement, activePolicy,
                System.nanoTime(), observer, future);
        LinkedHashMap<String, String> requestedFacts = timelineFacts(item, "QUEUED", 0);
        requestedFacts.put("provider", request.provider().providerId());
        requestedFacts.put("backend", requirements.backend());
        requestedFacts.put("foreground", Boolean.toString(request.foreground()));
        requestedFacts.put("queueDepth", Integer.toString(pending.size()));
        emit(item, OrbisResourceEvent.Type.RESOURCE_REQUESTED, 0,
                Map.copyOf(requestedFacts));
        if (request.foreground() && isForegroundConversationWorkload(request.workload())
                && operatingState == OperatingState.ERROR
                && !conversationServiceable()) {
            emitAdmissionFailed(item, "RESOURCE_STARVED:OPERATING_ENVELOPE_UNAVAILABLE", 0);
            future.completeExceptionally(new ResourceStarvedException(
                    "RESOURCE_STARVED immediately: sustainable operating envelope unavailable"));
            refreshSnapshot();
            return;
        }
        emit(item, OrbisResourceEvent.Type.BACKEND_SELECTED, 0, Map.of(
                "provider", request.provider().providerId(),
                "backend", requirements.backend(),
                "placement", placement.name(),
                "policy", item.policy.name()));
        String incompatible = incompatiblePolicy(request, placement, item.policy);
        if (!incompatible.isBlank()) {
            emit(item, OrbisResourceEvent.Type.RESOURCE_PRESSURE, 0, Map.of(
                    "reason", incompatible, "action", "REJECTED"));
            emitAdmissionFailed(item, incompatible, 0);
            future.completeExceptionally(new IllegalStateException(incompatible));
            refreshSnapshot();
            return;
        }
        if (pending.size() >= config.maximumQueuedRequests()) {
            emit(item, OrbisResourceEvent.Type.PROVIDER_BUSY, 0, Map.of(
                    "reason", "orbis-resource-queue-capacity",
                    "queueLimit", Integer.toString(config.maximumQueuedRequests())));
            emitAdmissionFailed(item, "orbis-resource-queue-capacity", 0);
            future.completeExceptionally(new IllegalStateException(
                    "Orbis resource queue is full"));
            refreshSnapshot();
            return;
        }
        pending.add(item);
        long requestedTimeout = request.timeoutMillis() <= 0
                ? config.defaultAdmissionTimeoutMillis() : request.timeoutMillis();
        long timeout = request.foreground() && isForegroundConversationWorkload(
                request.workload())
                ? Math.min(requestedTimeout, config.defaultAdmissionTimeoutMillis())
                : requestedTimeout;
        control.schedule(() -> timeoutOnControl(request.requestId()), timeout,
                TimeUnit.MILLISECONDS);
        pump();
    }

    private void pump() {
        if (closed.get() || pending.isEmpty()) {
            refreshSnapshot();
            return;
        }
        pending.sort(Comparator.comparingInt((Pending value) -> value.request.priority().rank())
                .thenComparing((Pending value) -> !value.request.foreground())
                .thenComparingLong(value -> value.sequence));
        boolean progressed;
        do {
            progressed = false;
            for (Pending item : List.copyOf(pending)) {
                item.evaluationCount++;
                String reason = deferReason(item);
                if (!reason.isBlank()) {
                    long wait = elapsed(item.queuedNanos);
                    boolean firstPressure = item.firstPressureAt == null;
                    boolean reasonChanged = !reason.equals(item.lastDeferredReason);
                    if (firstPressure) item.firstPressureAt = Instant.now();
                    item.pressureSampleCount++;
                    LinkedHashMap<String, String> pressure = pressureFacts(item, reason);
                    pressure.putAll(timelineFacts(item,
                            firstPressure ? "FIRST_PRESSURE_SAMPLE" : "PRESSURE_RECHECK",
                            wait));
                    pressure.put("queueDepth", Integer.toString(pending.size()));
                    pressure.put("queuePosition", Integer.toString(
                            Math.max(1, pending.indexOf(item) + 1)));
                    pressure.put("deferDurationMs", Long.toString(wait));
                    pressure.put("nextReevaluationMs", Long.toString(
                            PRESSURE_REEVALUATION_MILLIS));
                    if (firstPressure || reasonChanged) {
                        emit(item, OrbisResourceEvent.Type.RESOURCE_DEFERRED, wait,
                                Map.copyOf(pressure));
                        emit(item, reason.startsWith("provider-")
                                        ? OrbisResourceEvent.Type.PROVIDER_BUSY
                                        : OrbisResourceEvent.Type.RESOURCE_PRESSURE,
                                wait, Map.of("reason", reason, "action", "QUEUED",
                                        "deferDurationMs", Long.toString(wait)));
                    }
                    if (!firstPressure) {
                        item.recheckCount++;
                        LinkedHashMap<String, String> recheck = new LinkedHashMap<>();
                        recheck.put("reason", reason);
                        recheck.put("pressureSource",
                                pressure.getOrDefault("pressureSource", "UNKNOWN"));
                        recheck.put("pressureThreshold",
                                pressure.getOrDefault("pressureThreshold", "UNKNOWN"));
                        recheck.put("schedulerDecision", schedulerDecision(reason));
                        recheck.put("recheckCount", Integer.toString(item.recheckCount));
                        recheck.put("queueDepth", Integer.toString(pending.size()));
                        recheck.put("queuePosition", Integer.toString(
                                Math.max(1, pending.indexOf(item) + 1)));
                        recheck.put("totalDeferDurationMs", Long.toString(wait));
                        recheck.put("reclaimStatus", item.reclaimAttemptCount == 0
                                ? "NOT_ATTEMPTED" : item.reclaimInFlight
                                        ? "IN_PROGRESS" : "COMPLETED");
                        recheck.put("reclaimAction", item.lastReclaimAction.isBlank()
                                ? "NONE" : item.lastReclaimAction);
                        recheck.put("reclaimResult", item.lastReclaimOutcome.isBlank()
                                ? "NONE" : item.lastReclaimOutcome);
                        emit(item, OrbisResourceEvent.Type.RESOURCE_RECHECK, wait,
                                Map.copyOf(recheck));
                    }
                    if (firstPressure || reasonChanged) {
                        attemptReclaim(item, reason, wait);
                    }
                    item.lastDeferredReason = reason;
                    continue;
                }
                pending.remove(item);
                long wait = elapsed(item.queuedNanos);
                boolean safeFirstPhraseOverlap = safeFirstPhraseTtsOverlap(item);
                Active admitted = new Active(item.request, item.requirements, item.placement,
                        item.policy,
                        gpuCandidate(item), System.nanoTime(), item.observer);
                active.put(item.request.requestId(), admitted);
                Lease lease = new Lease(this, item.request.requestId(), item.placement, wait);
                LinkedHashMap<String, String> admittedFacts = timelineFacts(item,
                        "ADMISSION_SUCCEEDED", wait);
                admittedFacts.put("admissionOutcome", "SUCCESS");
                admittedFacts.put("admittedAt", Instant.now().toString());
                admittedFacts.put("provider", item.request.provider().providerId());
                admittedFacts.put("backend", item.requirements.backend());
                admittedFacts.put("activeJobs", Integer.toString(active.size()));
                admittedFacts.put("queueDepth", Integer.toString(pending.size()));
                if (safeFirstPhraseOverlap) {
                    admittedFacts.put("admissionMode", "FIRST_PHRASE_SAFE_GPU_OVERLAP");
                    admittedFacts.put("sharedGpuWith", "FOREGROUND_PARTIAL_GPU_LLM");
                    admittedFacts.put("overlapSafetyMarginMiB",
                            Long.toString(FIRST_PHRASE_OVERLAP_MARGIN_MIB));
                    admittedFacts.put("hytaleSafetyReservePreserved", "true");
                }
                emit(item, OrbisResourceEvent.Type.RESOURCE_ADMITTED, wait,
                        Map.copyOf(admittedFacts));
                item.future.complete(lease);
                progressed = true;
                break;
            }
        } while (progressed && !pending.isEmpty());
        refreshSnapshot();
        scheduleReevaluation();
    }

    private void attemptReclaim(Pending item, String reason, long wait) {
        if (item.reclaimInFlight) return;
        item.reclaimInFlight = true;
        item.reclaimAttemptCount++;
        LinkedHashMap<String, String> reclaim = timelineFacts(item,
                "RECLAIM_ATTEMPT", wait);
        reclaim.put("reason", reason);
        reclaim.put("reclaimAttemptIndex", Integer.toString(item.reclaimAttemptCount));
        reclaim.put("reclaimActionAttempted", "PROVIDER_LIFECYCLE_ADAPTER");
        reclaim.put("reclaimOutcome", "IN_PROGRESS");
        emit(item, OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT,
                wait, Map.copyOf(reclaim));
        CompletableFuture<ResourceReclaimResult> action;
        try {
            action = reclaimer.reclaim(item.request.workload(), reason);
        } catch (RuntimeException failure) {
            action = CompletableFuture.failedFuture(failure);
        }
        action.whenComplete((result, failure) -> execute(() -> {
            item.reclaimInFlight = false;
            if (failure != null) {
                item.lastReclaimOutcome = "FAILED:" + failure.getClass().getSimpleName();
            } else if (result == null) {
                item.lastReclaimOutcome = "UNKNOWN";
            } else {
                item.lastReclaimAction = result.action();
                item.lastReclaimOutcome = result.outcome();
            }
            log.accept("ORBIS_RECLAIM request=" + item.request.requestId()
                    + " workload=" + item.request.workload()
                    + " action=" + item.lastReclaimAction
                    + " outcome=" + item.lastReclaimOutcome);
            pump();
        }));
    }

    private void scheduleReevaluation() {
        if (pending.isEmpty() || closed.get() || reevaluationScheduled) return;
        reevaluationScheduled = true;
        control.schedule(() -> {
            reevaluationScheduled = false;
            pump();
        }, PRESSURE_REEVALUATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void sampleOperatingEnvelope() {
        if (!envelopeMonitoring || closed.get()) return;
        ConversationOperatingEnvelope envelope = envelopeSupplier == null
                ? null : envelopeSupplier.get();
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (envelope != null && host != null && host.at() != null
                && host.vramFreeMiB() >= 0 && host.vramTotalMiB() > 0) {
            long sampleAt = host.at().toEpochMilli();
            if (sampleAt > lastEnvelopeSampleEpochMillis) {
                lastEnvelopeSampleEpochMillis = sampleAt;
                long required = degradedProfileActive
                        ? envelope.degradedRequiredMiB() : envelope.preferredRequiredMiB();
                boolean safe = host.vramFreeMiB() >= required;
                safeEnvelopeSamples = safe ? safeEnvelopeSamples + 1 : 0;
                unsafeEnvelopeSamples = safe ? 0 : unsafeEnvelopeSamples + 1;
                operatingEnvelope = new OperatingEnvelopeSnapshot(operatingState,
                        envelope.model(), envelope.hardwareProfile(), host.vramFreeMiB(),
                        envelope.immediateRequiredMiB(), required, safeEnvelopeSamples,
                        unsafeEnvelopeSamples, pressureEpoch, remediationAttemptsThisEpoch,
                        safe ? "SUSTAINABLE" : "SUSTAINED_HEADROOM_CHECK");
                if (operatingState == OperatingState.READY
                        && unsafeEnvelopeSamples >= ENVELOPE_HYSTERESIS_SAMPLES) {
                    beginPressureEpoch(envelope, host);
                } else if (operatingState == OperatingState.RECOVERING
                        && safeEnvelopeSamples >= ENVELOPE_HYSTERESIS_SAMPLES) {
                    transitionOperatingState(OperatingState.DEGRADED_READY,
                            "Lower-memory profile proved across fresh samples");
                } else if (operatingState == OperatingState.ERROR
                        && host.vramFreeMiB() >= envelope.degradedRequiredMiB()
                        && safeEnvelopeSamples >= ENVELOPE_HYSTERESIS_SAMPLES) {
                    transitionOperatingState(OperatingState.DEGRADED_READY,
                            "Recovered sustainable degraded headroom");
                } else if (operatingState == OperatingState.DEGRADED_READY
                        && unsafeEnvelopeSamples >= ENVELOPE_HYSTERESIS_SAMPLES
                        && host.vramFreeMiB() < envelope.immediateRequiredMiB()) {
                    transitionOperatingState(OperatingState.ERROR,
                            "No safe foreground headroom; fail-fast until recovery");
                }
            }
        }
        control.schedule(this::sampleOperatingEnvelope, ENVELOPE_SAMPLE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private void beginPressureEpoch(ConversationOperatingEnvelope envelope,
            RuntimeResourceMonitor.Snapshot host) {
        pressureEpoch++;
        remediationAttemptsThisEpoch = 1;
        envelopeRemediationInFlight = true;
        transitionOperatingState(OperatingState.PRESSURE,
                "Sustained free VRAM " + host.vramFreeMiB() + " < preferred "
                        + envelope.preferredRequiredMiB());
        reclaimer.reclaim(ResourceWorkload.LLM,
                "sustained-operating-envelope-pressure").whenComplete((result, failure) ->
                execute(() -> {
                    envelopeRemediationInFlight = false;
                    boolean changed = failure == null && result != null
                            && result.resourcesChanged();
                    degradedProfileActive = changed;
                    unsafeEnvelopeSamples = 0;
                    safeEnvelopeSamples = 0;
                    if (changed) {
                        transitionOperatingState(OperatingState.RECOVERING,
                                result.action() + ":" + result.outcome());
                    } else if (operatingEnvelope.freeVramMiB()
                            >= envelope.degradedRequiredMiB()) {
                        degradedProfileActive = true;
                        transitionOperatingState(OperatingState.DEGRADED_READY,
                                "Current profile safe under degraded envelope; "
                                        + (failure == null ? "no transition needed"
                                                : failure.getClass().getSimpleName()));
                    } else {
                        transitionOperatingState(OperatingState.ERROR,
                                "No supported remediation established safe headroom");
                    }
                    pump();
                }));
    }

    private void transitionOperatingState(OperatingState next, String detail) {
        operatingState = next;
        ConversationOperatingEnvelope envelope = envelopeSupplier == null
                ? null : envelopeSupplier.get();
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        long free = host == null ? -1 : host.vramFreeMiB();
        operatingEnvelope = new OperatingEnvelopeSnapshot(next,
                envelope == null ? "" : envelope.model(),
                envelope == null ? "UNKNOWN" : envelope.hardwareProfile(), free,
                envelope == null ? -1 : envelope.immediateRequiredMiB(),
                envelope == null ? -1 : (degradedProfileActive
                        ? envelope.degradedRequiredMiB() : envelope.preferredRequiredMiB()),
                safeEnvelopeSamples, unsafeEnvelopeSamples, pressureEpoch,
                remediationAttemptsThisEpoch, detail);
        log.accept("ORBIS_OPERATING_ENVELOPE state=" + next + " freeVramMiB=" + free
                + " profile=" + operatingEnvelope.hardwareProfile()
                + " immediateRequiredMiB=" + operatingEnvelope.immediateRequiredMiB()
                + " sustainableRequiredMiB=" + operatingEnvelope.sustainableRequiredMiB()
                + " pressureEpoch=" + pressureEpoch + " detail=" + detail);
        var sentinel = degradationSentinel;
        if (sentinel != null) {
            boolean declaredReady = next == OperatingState.READY
                    || next == OperatingState.DEGRADED_READY;
            // READY/DEGRADED_READY transitions are emitted only after this authoritative
            // scheduler's startup proof or hysteresis. Transient raw samples remain owned by
            // sampleOperatingEnvelope; Sentinel must not replace that calculation.
            boolean sustainable = !declaredReady || next == OperatingState.READY
                    || next == OperatingState.DEGRADED_READY;
            var enforcement = sentinel.guard(
                    new com.inigmasgames.persistentnpcs.sentinel.SentinelObservation(
                    com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.Boundary
                            .READINESS_SAMPLE,
                    "RESOURCE_PROFILE:" + operatingEnvelope.hardwareProfile(), null,
                    java.util.List.of("pressureEpoch=" + pressureEpoch),
                    java.util.Map.of(
                            "schedulerReady", Boolean.toString(declaredReady),
                            "schedulerSustainableForeground", Boolean.toString(sustainable),
                            "starvationRepeated", "false",
                            "residencyStable", "true",
                            "provider", "NEMOTRON",
                            "configurationHash", activePolicy.name())));
            if (!enforcement.allowed() && declaredReady) {
                // The resource scheduler remains the state owner and applies the Sentinel's
                // fail-fast request without changing the Hytale reserve or inventing a profile.
                next = OperatingState.ERROR;
                operatingState = next;
                operatingEnvelope = new OperatingEnvelopeSnapshot(next,
                        operatingEnvelope.model(), operatingEnvelope.hardwareProfile(), free,
                        operatingEnvelope.immediateRequiredMiB(),
                        operatingEnvelope.sustainableRequiredMiB(), safeEnvelopeSamples,
                        unsafeEnvelopeSamples, pressureEpoch, remediationAttemptsThisEpoch,
                        "Sentinel contained unsustainable READY: "
                                + enforcement.reasonCode());
                detail = operatingEnvelope.detail();
            }
        }
        if (next == OperatingState.ERROR) failPendingForUnavailableEnvelope();
        OrbisReadinessService target = readiness;
        if (target == null) return;
        switch (next) {
            case READY -> {
                target.transition(OrbisReadinessSystem.NEMOTRON, 100,
                        OrbisReadinessStatus.READY, detail);
                target.transition(OrbisReadinessSystem.ORBIS, 100,
                        OrbisReadinessStatus.READY, detail);
            }
            case PRESSURE -> {
                target.transition(OrbisReadinessSystem.NEMOTRON, 90,
                        OrbisReadinessStatus.DEGRADED, detail);
                target.transition(OrbisReadinessSystem.ORBIS, 90,
                        OrbisReadinessStatus.DEGRADED, detail);
            }
            case RECOVERING -> {
                target.transition(OrbisReadinessSystem.NEMOTRON, 90,
                        OrbisReadinessStatus.WARMING, detail);
                target.transition(OrbisReadinessSystem.ORBIS, 90,
                        OrbisReadinessStatus.WARMING, detail);
            }
            case DEGRADED_READY -> {
                target.transition(OrbisReadinessSystem.NEMOTRON, 100,
                        OrbisReadinessStatus.DEGRADED, detail);
                target.transition(OrbisReadinessSystem.ORBIS, 100,
                        OrbisReadinessStatus.DEGRADED, detail);
            }
            case ERROR -> {
                target.fail(OrbisReadinessSystem.NEMOTRON,
                        OrbisReadinessStatus.ERROR, detail);
                target.fail(OrbisReadinessSystem.ORBIS,
                        OrbisReadinessStatus.ERROR, detail);
            }
            case STARTING -> { }
        }
    }

    private void failPendingForUnavailableEnvelope() {
        for (Pending item : List.copyOf(pending)) {
            if (!item.request.foreground()
                    || !isForegroundConversationWorkload(item.request.workload())) continue;
            pending.remove(item);
            long wait = elapsed(item.queuedNanos);
            emitAdmissionFailed(item,
                    "RESOURCE_STARVED:OPERATING_ENVELOPE_UNAVAILABLE", wait);
            item.future.completeExceptionally(new ResourceStarvedException(
                    "RESOURCE_STARVED after " + wait
                            + "ms: sustainable operating envelope unavailable"));
        }
    }

    private String deferReason(Pending item) {
        int serviceLimit = Math.min(configuredLimit(item.request.workload()),
                item.requirements.concurrencyLimit());
        long sameProvider = active.values().stream().filter(value ->
                value.request.provider().providerId().equals(
                        item.request.provider().providerId())).count();
        if (sameProvider >= serviceLimit) return "provider-concurrency-limit";
        long sameWorkload = active.values().stream().filter(value ->
                group(value.request.workload()) == group(item.request.workload())).count();
        if (sameWorkload >= configuredLimit(item.request.workload())) {
            return "provider-workload-limit";
        }
        if (!item.request.foreground() && hasForegroundDemand()) {
            return "foreground-conversation-priority";
        }
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (host != null && host.ramTotalMiB() > 0) {
            long available = Math.max(0, host.ramTotalMiB() - host.ramUsedMiB());
            long required = Math.max(config.minimumFreeRamMiB(),
                    item.requirements.estimatedRamMiB());
            if (available < required) return "ram-pressure";
        }
        if (gpuCandidate(item)) {
            long gpuJobs = active.values().stream().filter(value ->
                    value.gpuCandidate).count();
            int gpuLimit = item.policy == ResourcePolicy.GPU_HEAVY
                    ? config.maximumConcurrentLocalGpu()
                    : Math.min(1, config.maximumConcurrentLocalGpu());
            if (gpuJobs >= gpuLimit && !safeFirstPhraseTtsOverlap(item)) {
                return "local-gpu-gate";
            }
            if (item.request.workload() == ResourceWorkload.LLM
                    && waitingFirstTts()) return "tts-first-audio-reservation";
            String pressure = gpuPressureReason(host, item);
            if (!pressure.isBlank()) return pressure;
        }
        return "";
    }

    private boolean gpuCandidate(Pending item) {
        if (item.placement.usesLocalGpu()) return true;
        if (item.placement != ExecutionPlacement.UNKNOWN
                || item.request.provider().executionMode()
                        == com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode.REMOTE) {
            return false;
        }
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (host == null || host.vramTotalMiB() <= 0) return false;
        return item.request.workload() == ResourceWorkload.LLM
                || item.request.workload() == ResourceWorkload.TTS
                || item.request.workload() == ResourceWorkload.DIRECT_VOICE
                || item.policy == ResourcePolicy.GPU_HEAVY;
    }

    private boolean waitingFirstTts() {
        return pending.stream().anyMatch(value -> value.request.workload()
                == ResourceWorkload.TTS && value.request.priority() == ResourcePriority.HIGH);
    }

    /**
     * Allows only the first foreground phrase to overlap a single partial-GPU foreground
     * LLM. Cached telemetry must prove VRAM headroom (including the Hytale reserve and an
     * extra margin), sub-threshold GPU utilization, and no server-frame pressure. Unknown
     * measurements fail closed. Later TTS chunks and full-GPU LLMs retain the exclusive gate.
     */
    private boolean safeFirstPhraseTtsOverlap(Pending item) {
        if (item.request.workload() != ResourceWorkload.TTS
                || item.request.priority() != ResourcePriority.HIGH
                || !item.request.foreground()
                || item.placement != ExecutionPlacement.LOCAL_GPU) return false;
        List<Active> gpuOwners = active.values().stream().filter(value -> value.gpuCandidate)
                .toList();
        if (gpuOwners.size() != 1) return false;
        Active owner = gpuOwners.getFirst();
        if (owner.request.workload() != ResourceWorkload.LLM
                || !owner.request.foreground()
                || owner.placement != ExecutionPlacement.LOCAL_PARTIAL_GPU) return false;
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (host == null || host.vramFreeMiB() < 0 || host.vramTotalMiB() <= 0
                || host.gpuUtilizationPercent() < 0
                || host.gpuUtilizationPercent() >= config.gpuPressureUtilizationPercent()
                || host.framePressure().serverFramePressure()) return false;
        return host.vramFreeMiB() >= requiredVramHeadroom(item, host)
                + FIRST_PHRASE_OVERLAP_MARGIN_MIB;
    }

    private boolean hasForegroundDemand() {
        return active.values().stream().anyMatch(value -> value.request.foreground())
                || pending.stream().anyMatch(value -> value.request.foreground());
    }

    private String gpuPressureReason(RuntimeResourceMonitor.Snapshot host, Pending item) {
        if (host == null) return "";
        if (host.vramFreeMiB() >= 0 && host.vramTotalMiB() > 0) {
            long required = requiredVramHeadroom(item, host);
            if (host.vramFreeMiB() < required) return "vram-headroom-pressure";
        }
        if (host.framePressure().serverFramePressure()) {
            return "hytale-frame-pressure";
        }
        return "";
    }

    private long requiredVramHeadroom(Pending item,
            RuntimeResourceMonitor.Snapshot host) {
        RuntimeResourceMonitor.ModelResidency residency = matchingResidency(item, host);
        boolean resident = residency != null && residency.loaded();
        long load = resident ? 0 : item.requirements.residentVramMiB();
        return load + item.requirements.incrementalVramMiB()
                + item.requirements.temporaryVramMiB()
                + config.hytaleGpuSafetyReserveMiB();
    }

    private RuntimeResourceMonitor.ModelResidency matchingResidency(Pending item,
            RuntimeResourceMonitor.Snapshot host) {
        return matchingResidency(item.request.provider().providerId(),
                item.requirements.backend(), host);
    }

    private static RuntimeResourceMonitor.ModelResidency matchingResidency(
            String providerId, String backend, RuntimeResourceMonitor.Snapshot host) {
        if (host == null) return null;
        String provider = identityToken(providerId);
        String backendToken = identityToken(backend);
        return host.modelResidencies().stream().filter(value -> {
            String candidateProvider = identityToken(value.provider());
            String candidateModel = identityToken(value.model());
            return provider.equals(candidateProvider)
                    || (!candidateProvider.isBlank()
                            && provider.contains(candidateProvider))
                    || (!provider.isBlank()
                            && candidateProvider.contains(provider))
                    || (!candidateModel.isBlank()
                            && (provider.contains(candidateModel)
                                    || backendToken.contains(candidateModel)));
        }).findFirst().orElse(null);
    }

    private static String identityToken(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static boolean isForegroundConversationWorkload(ResourceWorkload workload) {
        return workload == ResourceWorkload.LLM || workload == ResourceWorkload.TTS
                || workload == ResourceWorkload.DIRECT_VOICE;
    }

    private LinkedHashMap<String, String> pressureFacts(Pending item, String reason) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("reason", reason);
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (host == null) {
            facts.put("pressureSource", "UNKNOWN");
            facts.put("sampledPressure", "telemetry-unavailable");
        } else {
            boolean utilization = host.gpuUtilizationPercent() >= 0
                    && host.gpuUtilizationPercent()
                            >= config.gpuPressureUtilizationPercent();
            long usedPercent = host.vramTotalMiB() <= 0 || host.vramUsedMiB() < 0
                    ? -1 : host.vramUsedMiB() * 100 / host.vramTotalMiB();
            boolean vram = usedPercent >= config.vramPressureUsedPercent();
            facts.put("pressureSource", reason.equals("vram-headroom-pressure") ? "VRAM_HEADROOM"
                    : reason.equals("gpu-utilization-pressure") ? "GPU_UTILIZATION"
                    : reason.equals("hytale-frame-pressure") ? "HYTALE_FRAME_TIME"
                    : utilization && vram ? "GPU_UTILIZATION+VRAM"
                    : utilization ? "GPU_UTILIZATION" : vram ? "VRAM" : "SCHEDULER_GATE");
            facts.put("sampledPressure", "gpu=" + host.gpuUtilizationPercent()
                    + "%;vram=" + host.vramUsedMiB() + "/" + host.vramTotalMiB()
                    + "MiB(" + usedPercent + "%)");
            facts.put("pressureThreshold", "gpu="
                    + config.gpuPressureUtilizationPercent() + "%;vram="
                    + config.vramPressureUsedPercent() + "%;hytaleSafetyReserveMiB="
                    + config.hytaleGpuSafetyReserveMiB());
            RuntimeResourceMonitor.ModelResidency residency = matchingResidency(item, host);
            boolean resident = residency != null && residency.loaded();
            long activeReservation = active.values().stream().mapToLong(value ->
                    value.requirements.incrementalVramMiB()
                            + value.requirements.temporaryVramMiB()).sum();
            facts.put("providerAlreadyResident", Boolean.toString(resident));
            facts.put("providerResidentVramMiB", Long.toString(residency == null
                    ? item.requirements.residentVramMiB() : residency.estimatedVramMiB()));
            facts.put("incrementalInferenceVramMiB",
                    Long.toString(item.requirements.incrementalVramMiB()));
            facts.put("temporaryInferenceVramMiB",
                    Long.toString(item.requirements.temporaryVramMiB()));
            facts.put("activeInferenceReservationMiB", Long.toString(activeReservation));
            facts.put("hytaleGpuSafetyReserveMiB",
                    Long.toString(config.hytaleGpuSafetyReserveMiB()));
            facts.put("availableHeadroomMiB", Long.toString(host.vramFreeMiB()));
            facts.put("requiredHeadroomMiB", Long.toString(requiredVramHeadroom(item, host)));
        }
        facts.put("activeGateOwner", active.values().stream()
                .filter(value -> value.gpuCandidate).findFirst()
                .map(value -> value.request.workload() + ":"
                        + value.request.provider().providerId() + ":"
                        + value.request.requestId()).orElse("none"));
        facts.put("schedulerDecision", schedulerDecision(reason));
        facts.put("reclaimActionAttempted", reclaimAction(reason));
        return facts;
    }

    private static String schedulerDecision(String reason) {
        return switch (reason) {
            case "vram-pressure", "vram-headroom-pressure" -> "BLOCKED_BECAUSE_VRAM";
            case "hytale-frame-pressure" -> "BLOCKED_BECAUSE_HYTALE_FRAME_TIME";
            case "gpu-utilization-pressure" -> "BLOCKED_BECAUSE_GPU_UTILIZATION";
            case "ram-pressure" -> "BLOCKED_BECAUSE_RAM";
            case "local-gpu-gate" -> "BLOCKED_BECAUSE_GPU_OWNER";
            case "tts-first-audio-reservation" -> "BLOCKED_FOR_FIRST_TTS_AUDIO";
            case "foreground-conversation-priority" -> "BLOCKED_FOR_FOREGROUND_TURN";
            case "provider-concurrency-limit", "provider-workload-limit" ->
                    "BLOCKED_BECAUSE_PROVIDER_BUSY";
            default -> "BLOCKED:" + reason.toUpperCase(Locale.ROOT).replace('-', '_');
        };
    }

    private static String reclaimAction(String reason) {
        return switch (reason) {
            case "vram-pressure", "vram-headroom-pressure" ->
                    "DEFERRED_REQUEST;PROVIDER_LIFECYCLE_RECLAIM_REQUESTED";
            case "gpu-utilization-pressure" ->
                    "DEFERRED_REQUEST;PRESERVED_HYTALE_GPU_PRIORITY";
            case "foreground-conversation-priority" ->
                    "DEFERRED_BACKGROUND_WORK";
            default -> "DEFERRED_REQUEST;NO_DESTRUCTIVE_RECLAIM";
        };
    }

    private static String reclaimOutcome(String reason) {
        return switch (reason) {
            case "vram-pressure", "vram-headroom-pressure" -> "RECLAIM_PENDING";
            case "gpu-utilization-pressure" -> "HYTALE_GPU_PRIORITY_PRESERVED";
            case "foreground-conversation-priority" -> "BACKGROUND_WORK_DEFERRED";
            default -> "REQUEST_REMAINS_QUEUED";
        };
    }

    private static LinkedHashMap<String, String> timelineFacts(Pending item,
            String stage, long waitMillis) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("timelineStage", stage);
        facts.put("queuedAt", item.queuedAt.toString());
        facts.put("firstPressureAt", item.firstPressureAt == null
                ? "" : item.firstPressureAt.toString());
        facts.put("evaluationCount", Integer.toString(item.evaluationCount));
        facts.put("pressureSampleCount", Integer.toString(item.pressureSampleCount));
        facts.put("recheckCount", Integer.toString(item.recheckCount));
        facts.put("reclaimAttemptCount", Integer.toString(item.reclaimAttemptCount));
        facts.put("totalDeferDurationMs", Long.toString(Math.max(0, waitMillis)));
        return facts;
    }

    private void emitAdmissionFailed(Pending item, String reason, long wait) {
        LinkedHashMap<String, String> facts = timelineFacts(item,
                "ADMISSION_FAILED", wait);
        facts.put("admissionOutcome", "FAILED");
        facts.put("failureReason", safe(reason));
        facts.put("failedAt", Instant.now().toString());
        emit(item, OrbisResourceEvent.Type.RESOURCE_ADMISSION_FAILED, wait,
                Map.copyOf(facts));
    }

    private ExecutionPlacement resolvePlacement(OrbisResourceRequest request,
            AiResourceRequirements requirements) {
        ExecutionPlacement override = config.backendOverrides().get(
                request.provider().providerId());
        if (override == null) override = config.backendOverrides().get(
                request.workload().name());
        if (override != null) return override;
        if (requirements.placement() != ExecutionPlacement.UNKNOWN) {
            return requirements.placement();
        }
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        if (host != null && request.workload() == ResourceWorkload.LLM) {
            ExecutionPlacement observed = host.inferencePlacement(
                    request.provider().providerId());
            if (observed != ExecutionPlacement.UNKNOWN) return observed;
        }
        return ExecutionPlacement.UNKNOWN;
    }

    private String incompatiblePolicy(OrbisResourceRequest request,
            ExecutionPlacement placement, ResourcePolicy policy) {
        if (policy == ResourcePolicy.CPU_ONLY
                && (placement != ExecutionPlacement.LOCAL_CPU)) {
            return "CPU_ONLY policy is incompatible with configured "
                    + placement + " provider " + request.provider().providerId();
        }
        if (policy == ResourcePolicy.REMOTE_AI && !placement.remote()) {
            return "REMOTE_AI policy requires an explicitly remote provider; configured "
                    + placement + " provider " + request.provider().providerId();
        }
        return "";
    }

    private int configuredLimit(ResourceWorkload workload) {
        return switch (group(workload)) {
            case STT -> config.maximumConcurrentStt();
            case TTS, DIRECT_VOICE -> config.maximumConcurrentTts();
            case BACKGROUND_COGNITION -> config.maximumConcurrentBackground();
            default -> config.maximumConcurrentLlm();
        };
    }

    private static ResourceWorkload group(ResourceWorkload workload) {
        return switch (workload) {
            case DIRECT_VOICE -> ResourceWorkload.TTS;
            case BACKGROUND_COGNITION -> ResourceWorkload.BACKGROUND_COGNITION;
            case PERSISTENCE, DIAGNOSTICS -> workload;
            default -> workload;
        };
    }

    private void timeoutOnControl(UUID requestId) {
        Pending item = pending.stream().filter(value ->
                value.request.requestId().equals(requestId)).findFirst().orElse(null);
        if (item == null) return;
        pending.remove(item);
        long wait = elapsed(item.queuedNanos);
        String reason = item.lastDeferredReason.isBlank()
                ? "admission-timeout" : item.lastDeferredReason;
        LinkedHashMap<String, String> facts = pressureFacts(item, reason);
        facts.put("terminalReason", "RESOURCE_STARVED");
        facts.put("deferDurationMs", Long.toString(wait));
        facts.putAll(timelineFacts(item, "ADMISSION_TIMEOUT", wait));
        emit(item, OrbisResourceEvent.Type.RESOURCE_TIMEOUT, wait, Map.copyOf(facts));
        emitAdmissionFailed(item, "RESOURCE_STARVED:" + reason, wait);
        item.future.completeExceptionally(new ResourceStarvedException(
                "RESOURCE_STARVED after " + wait + "ms: " + reason));
        pump();
    }

    private void cancelOnControl(UUID requestId, String reason) {
        Pending item = pending.stream().filter(value ->
                value.request.requestId().equals(requestId)).findFirst().orElse(null);
        if (item != null) {
            pending.remove(item);
            long wait = elapsed(item.queuedNanos);
            LinkedHashMap<String, String> facts = timelineFacts(item,
                    "CANCELLED_BEFORE_ADMISSION", wait);
            facts.put("reason", safe(reason));
            facts.put("started", "false");
            emit(item, OrbisResourceEvent.Type.RESOURCE_RELEASED, wait,
                    Map.copyOf(facts));
            item.future.cancel(false);
            pump();
            return;
        }
        Active running = active.remove(requestId);
        if (running != null) {
            emit(running, OrbisResourceEvent.Type.RESOURCE_RELEASED,
                    elapsed(running.startedNanos), Map.of("reason", safe(reason),
                            "started", "true", "logicalRelease", "true"));
            pump();
        }
    }

    private void release(UUID requestId) {
        if (requestId == null || closed.get()) return;
        execute(() -> cancelOnControl(requestId, "completed"));
    }

    private void emit(Pending item, OrbisResourceEvent.Type type, long wait,
            Map<String, String> facts) {
        emit(item.request, item.placement, item.policy, item.observer, type, wait, facts);
    }

    private void emit(Active item, OrbisResourceEvent.Type type, long wait,
            Map<String, String> facts) {
        emit(item.request, item.placement, item.policy, item.observer, type, wait, facts);
    }

    private void emit(OrbisResourceRequest request, ExecutionPlacement placement,
            ResourcePolicy policy,
            Consumer<OrbisResourceEvent> observer, OrbisResourceEvent.Type type,
            long wait, Map<String, String> facts) {
        RuntimeResourceMonitor.Snapshot host = telemetry.get();
        String snapshotId = resourceSnapshotId(host);
        String priorSnapshotId = latestResourceSnapshotId;
        RuntimeResourceMonitor.Snapshot priorSnapshot = latestMaterialSnapshot;
        boolean materialChange = priorSnapshotId != null
                && !priorSnapshotId.equals(snapshotId);
        latestResourceSnapshotId = snapshotId;
        latestMaterialSnapshot = host;
        if (materialChange && type != OrbisResourceEvent.Type.RESOURCE_SNAPSHOT) {
            LinkedHashMap<String, String> changed = new LinkedHashMap<>(
                    fullResourceTelemetryFacts(request, host));
            changed.put("resourceSnapshotId", snapshotId);
            changed.put("resourceSnapshotReference", "RESOURCE_SNAPSHOT:" + snapshotId);
            changed.put("resourceSnapshotMode", "FULL");
            changed.put("snapshotReason", materialChangeReason(priorSnapshot, host));
            changed.put("previousResourceSnapshotId", priorSnapshotId);
            dispatch(request, placement, policy, observer,
                    OrbisResourceEvent.Type.RESOURCE_SNAPSHOT, wait,
                    Map.copyOf(changed), true);
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>(facts);
        boolean full = fullSnapshotBoundary(type);
        Map<String, String> telemetryFacts = full
                ? fullResourceTelemetryFacts(request, host)
                : lightweightResourceTelemetryFacts(host);
        telemetryFacts.forEach(values::putIfAbsent);
        values.put("resourceSnapshotId", snapshotId);
        values.put("resourceSnapshotReference", "RESOURCE_SNAPSHOT:" + snapshotId);
        values.put("resourceSnapshotMode", full ? "FULL" : "DELTA");
        OperatingEnvelopeSnapshot envelope = operatingEnvelope;
        values.putIfAbsent("conversationOperatingState", envelope.state().name());
        values.putIfAbsent("conversationHardwareProfile", envelope.hardwareProfile());
        values.putIfAbsent("conversationImmediateRequiredMiB",
                Long.toString(envelope.immediateRequiredMiB()));
        values.putIfAbsent("conversationSustainableRequiredMiB",
                Long.toString(envelope.sustainableRequiredMiB()));
        values.putIfAbsent("conversationPressureEpoch",
                Integer.toString(envelope.pressureEpoch()));
        values.putIfAbsent("conversationRemediationAttempts",
                Integer.toString(envelope.remediationAttempts()));
        if (type != OrbisResourceEvent.Type.RESOURCE_RECHECK) {
            values.putIfAbsent("policy", policy.name());
            values.putIfAbsent("workload", request.workload().name());
            values.putIfAbsent("priority", request.priority().name());
        }
        dispatch(request, placement, policy, observer, type, wait,
                Map.copyOf(values), false);
    }

    /** The monitor publishes immutable cached objects, so unchanged 200 ms rechecks do no hashing. */
    private String resourceSnapshotId(RuntimeResourceMonitor.Snapshot host) {
        if (host == identityCachedSnapshot && identityCachedSnapshotId != null) {
            return identityCachedSnapshotId;
        }
        identityCachedSnapshot = host;
        identityCachedSnapshotId = ResourceSnapshotIdentity.id(host);
        return identityCachedSnapshotId;
    }

    private void dispatch(OrbisResourceRequest request, ExecutionPlacement placement,
            ResourcePolicy policy, Consumer<OrbisResourceEvent> observer,
            OrbisResourceEvent.Type type, long wait, Map<String, String> facts,
            boolean synthesizedSnapshot) {
        OrbisResourceEvent event = new OrbisResourceEvent(type, request.requestId(),
                request.workload(), request.priority(), placement, Instant.now(), wait,
                facts);
        recentEvents.addLast(event);
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.removeFirst();
        try { observer.accept(event); } catch (RuntimeException ignored) { }
        try {
            log.accept("ORBIS_RESOURCE type=" + type + " request=" + request.requestId()
                    + " workload=" + request.workload() + " placement=" + placement
                    + " waitMs=" + wait + " snapshotEvent=" + synthesizedSnapshot
                    + " facts=" + facts);
        } catch (RuntimeException ignored) { }
    }

    private static boolean fullSnapshotBoundary(OrbisResourceEvent.Type type) {
        return type == OrbisResourceEvent.Type.RESOURCE_SNAPSHOT
                || type == OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT
                || type == OrbisResourceEvent.Type.RESOURCE_ADMITTED
                || type == OrbisResourceEvent.Type.RESOURCE_ADMISSION_FAILED;
    }

    private static String materialChangeReason(RuntimeResourceMonitor.Snapshot before,
            RuntimeResourceMonitor.Snapshot after) {
        if (before == null || after == null) return "RESOURCE_AVAILABILITY_CHANGED";
        if (!before.modelResidencies().equals(after.modelResidencies())) {
            return "PROVIDER_RESIDENCY_CHANGED";
        }
        List<String> beforeProcesses = before.gpuProcesses().stream().map(value ->
                value.pid() + ":" + value.processName() + ":" + value.category()).sorted()
                .toList();
        List<String> afterProcesses = after.gpuProcesses().stream().map(value ->
                value.pid() + ":" + value.processName() + ":" + value.category()).sorted()
                .toList();
        if (!beforeProcesses.equals(afterProcesses)) return "GPU_PROCESS_OWNERSHIP_CHANGED";
        if (!before.failure().equals(after.failure())
                || !before.perProcessGpuProbeStatus().equals(
                        after.perProcessGpuProbeStatus())) {
            return "PROVIDER_FAILURE_OR_MEASUREMENT_STATUS_CHANGED";
        }
        return "PROCESS_VRAM_CHANGED_MATERIALLY";
    }

    private Map<String, String> fullResourceTelemetryFacts(OrbisResourceRequest request,
            RuntimeResourceMonitor.Snapshot host) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        AiResourceRequirements requirements = request.provider().resourceRequirements();
        facts.put("orbisProviderEstimatedVramMiB",
                Long.toString(currentProviderEstimate(requirements)));
        facts.put("providerResidentVramMiB",
                Long.toString(requirements.residentVramMiB()));
        facts.put("incrementalInferenceVramMiB",
                Long.toString(requirements.incrementalVramMiB()));
        facts.put("temporaryInferenceVramMiB",
                Long.toString(requirements.temporaryVramMiB()));
        facts.put("hytaleGpuSafetyReserveMiB",
                Long.toString(config.hytaleGpuSafetyReserveMiB()));
        LinkedHashMap<String, Long> estimates = new LinkedHashMap<>();
        active.values().forEach(value -> estimates.merge(value.request.provider().providerId(),
                currentProviderEstimate(value.requirements), Long::sum));
        facts.put("orbisActiveEstimatedVramByProvider", estimates.toString());
        facts.put("orbisActiveEstimatedVramMiB", Long.toString(
                estimates.values().stream().mapToLong(Long::longValue).sum()));
        facts.put("activeInferenceReservationMiB", Long.toString(active.values().stream()
                .mapToLong(value -> value.requirements.incrementalVramMiB()
                        + value.requirements.temporaryVramMiB()).sum()));
        if (host == null) {
            facts.put("providerExpectedResident", "UNKNOWN");
            facts.put("providerResidencyState", "UNKNOWN");
            facts.put("providerWorkerPid", "UNKNOWN");
            facts.put("perProcessGpuVram", "UNKNOWN");
            facts.put("perProcessGpuProbeStatus", "UNKNOWN");
            facts.put("gpuUtilizationPercent", "-1");
            facts.put("gpuUtilizationMeasurementStatus", "UNKNOWN");
            facts.put("vramUsedMiB", "-1");
            facts.put("vramFreeMiB", "-1");
            facts.put("vramTotalMiB", "-1");
            facts.put("vramMeasurementStatus", "UNKNOWN");
            facts.put("hytaleServerFramePressure", "UNKNOWN");
            facts.put("hytaleClientFramePressure", "UNKNOWN");
            return Map.copyOf(facts);
        }
        RuntimeResourceMonitor.ModelResidency residency = matchingResidency(
                request.provider().providerId(), requirements.backend(), host);
        facts.put("providerExpectedResident", residency == null ? "UNKNOWN"
                : Boolean.toString(residency.expectedResident()));
        facts.put("providerResidencyState", residency == null ? "UNKNOWN"
                : residency.state());
        facts.put("providerWorkerPid", residency == null
                ? "UNKNOWN" : Long.toString(residency.workerPid()));
        facts.put("vramFreeMiB", Long.toString(host.vramFreeMiB()));
        facts.put("vramUsedMiB", Long.toString(host.vramUsedMiB()));
        facts.put("vramTotalMiB", Long.toString(host.vramTotalMiB()));
        facts.put("vramMeasurementStatus", host.vramTotalMiB() <= 0
                ? "UNKNOWN" : "MEASURED");
        facts.put("gpuUtilizationPercent",
                Integer.toString(host.gpuUtilizationPercent()));
        facts.put("gpuUtilizationMeasurementStatus",
                host.gpuUtilizationPercent() < 0 ? "UNKNOWN" : "MEASURED");
        facts.put("perProcessGpuVram", host.gpuProcesses().toString());
        facts.put("perProcessGpuProbeStatus", host.perProcessGpuProbeStatus());
        facts.put("hytaleClientGpuVramMiB",
                Long.toString(host.allocationFor("HYTALE_CLIENT")));
        facts.put("hytaleServerGpuVramMiB",
                Long.toString(host.allocationFor("HYTALE_SERVER")));
        facts.put("recentResidencyTransitions", host.residencyTransitions().toString());
        facts.put("hytaleServerFrameTimeMillis",
                Double.toString(host.framePressure().serverFrameTimeMillis()));
        facts.put("hytaleServerFps",
                Double.toString(host.framePressure().serverFps()));
        facts.put("hytaleServerFramePressure",
                Boolean.toString(host.framePressure().serverFramePressure()));
        facts.put("hytaleClientFps", "UNKNOWN");
        facts.put("hytaleClientFramePressure", host.framePressure().clientPressure());
        facts.put("framePressureSource", host.framePressure().source());
        return Map.copyOf(facts);
    }

    private static long currentProviderEstimate(AiResourceRequirements requirements) {
        if (requirements == null) return 0;
        if (!requirements.placement().usesLocalGpu()) return requirements.estimatedVramMiB();
        // Compatibility constructors historically mirrored the one coarse estimate into both
        // resident and incremental fields; do not double count that legacy representation.
        if (requirements.residentVramMiB() == requirements.estimatedVramMiB()
                && requirements.incrementalVramMiB() == requirements.estimatedVramMiB()
                && requirements.temporaryVramMiB() == 0) {
            return requirements.estimatedVramMiB();
        }
        return Math.max(0, requirements.residentVramMiB())
                + Math.max(0, requirements.incrementalVramMiB())
                + Math.max(0, requirements.temporaryVramMiB());
    }

    private static Map<String, String> lightweightResourceTelemetryFacts(
            RuntimeResourceMonitor.Snapshot host) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        if (host == null) {
            facts.put("vramUsedMiB", "-1");
            facts.put("vramFreeMiB", "-1");
            facts.put("vramTotalMiB", "-1");
            facts.put("gpuUtilizationPercent", "-1");
            facts.put("vramMeasurementStatus", "UNKNOWN");
            facts.put("gpuUtilizationMeasurementStatus", "UNKNOWN");
            return Map.copyOf(facts);
        }
        facts.put("vramUsedMiB", Long.toString(host.vramUsedMiB()));
        facts.put("vramFreeMiB", Long.toString(host.vramFreeMiB()));
        facts.put("vramTotalMiB", Long.toString(host.vramTotalMiB()));
        facts.put("gpuUtilizationPercent",
                Integer.toString(host.gpuUtilizationPercent()));
        facts.put("vramMeasurementStatus", host.vramTotalMiB() <= 0
                ? "UNKNOWN" : "MEASURED");
        facts.put("gpuUtilizationMeasurementStatus",
                host.gpuUtilizationPercent() < 0 ? "UNKNOWN" : "MEASURED");
        return Map.copyOf(facts);
    }

    private void refreshSnapshot() {
        EnumMap<ResourceWorkload, Integer> activeCounts = new EnumMap<>(ResourceWorkload.class);
        EnumMap<ResourceWorkload, Integer> queueCounts = new EnumMap<>(ResourceWorkload.class);
        active.values().forEach(value -> activeCounts.merge(
                value.request.workload(), 1, Integer::sum));
        pending.forEach(value -> queueCounts.merge(value.request.workload(), 1, Integer::sum));
        snapshot = new Snapshot(activePolicy, Map.copyOf(activeCounts),
                Map.copyOf(queueCounts), active.size(), pending.size(),
                telemetry.get(), List.copyOf(recentEvents));
    }

    public Snapshot snapshot() { return snapshot; }

    public String inspectorSummary() {
        Snapshot value = snapshot;
        RuntimeResourceMonitor.Snapshot host = value.host();
        return "policy=" + value.policy()
                + "\n" + operatingEnvelopeSummary()
                + "\nCPU=" + (host == null ? "UNKNOWN" : host.cpuDisplay())
                + "\nRAM=" + (host == null ? "UNKNOWN" : host.ramDisplay())
                + "\nGPU=" + (host == null ? "UNKNOWN" : host.gpuDisplay())
                + "\nactiveJobs=" + value.activeJobs() + " " + value.activeByWorkload()
                + "\nqueueDepth=" + value.queueDepth() + " " + value.queuedByWorkload()
                + "\nrecentPressure=" + value.recentEvents().stream()
                        .filter(event -> event.type() == OrbisResourceEvent.Type.RESOURCE_PRESSURE
                                || event.type() == OrbisResourceEvent.Type.RESOURCE_DEFERRED
                                || event.type() == OrbisResourceEvent.Type.RESOURCE_TIMEOUT)
                        .limit(6).map(event -> event.type() + ":" + event.workload()
                                + ":" + event.facts().getOrDefault("reason", ""))
                        .toList();
    }

    private void execute(Runnable task) {
        if (closed.get()) return;
        try { control.execute(task); } catch (RuntimeException ignored) { }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            control.execute(() -> {
                pending.forEach(value -> value.future.completeExceptionally(
                        new IllegalStateException("Orbis resource scheduler shutdown")));
                pending.clear();
                active.clear();
                refreshSnapshot();
            });
        } catch (RuntimeException ignored) { }
        control.shutdown();
    }

    private static long elapsed(long startedNanos) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)).toMillis();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unspecified" : value;
    }

    public record Snapshot(ResourcePolicy policy,
            Map<ResourceWorkload, Integer> activeByWorkload,
            Map<ResourceWorkload, Integer> queuedByWorkload,
            int activeJobs, int queueDepth, RuntimeResourceMonitor.Snapshot host,
            List<OrbisResourceEvent> recentEvents) {
        static Snapshot empty(ResourcePolicy policy) {
            return new Snapshot(policy, Map.of(), Map.of(), 0, 0, null, List.of());
        }
    }

    public record OperatingEnvelopeSnapshot(OperatingState state, String model,
            String hardwareProfile, long freeVramMiB, long immediateRequiredMiB,
            long sustainableRequiredMiB, int consecutiveSafeSamples,
            int consecutiveUnsafeSamples, int pressureEpoch, int remediationAttempts,
            String detail) {
        static OperatingEnvelopeSnapshot starting() {
            return new OperatingEnvelopeSnapshot(OperatingState.STARTING, "", "UNKNOWN",
                    -1, -1, -1, 0, 0, 0, 0, "Monitoring not started");
        }
    }

    public static final class Lease implements AutoCloseable {
        private final OrbisResourceScheduler owner;
        private final UUID requestId;
        private final ExecutionPlacement placement;
        private final long admissionWaitMillis;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(OrbisResourceScheduler owner, UUID requestId,
                ExecutionPlacement placement, long admissionWaitMillis) {
            this.owner = owner;
            this.requestId = requestId;
            this.placement = placement;
            this.admissionWaitMillis = admissionWaitMillis;
        }

        public UUID requestId() { return requestId; }
        public ExecutionPlacement placement() { return placement; }
        public long admissionWaitMillis() { return admissionWaitMillis; }

        @Override public void close() {
            if (released.compareAndSet(false, true)) owner.release(requestId);
        }
    }

    private static final class Pending {
        private final long sequence;
        private final OrbisResourceRequest request;
        private final AiResourceRequirements requirements;
        private final ExecutionPlacement placement;
        private final ResourcePolicy policy;
        private final long queuedNanos;
        private final Instant queuedAt;
        private final Consumer<OrbisResourceEvent> observer;
        private final CompletableFuture<Lease> future;
        private String lastDeferredReason = "";
        private Instant firstPressureAt;
        private int evaluationCount;
        private int pressureSampleCount;
        private int recheckCount;
        private int reclaimAttemptCount;
        private String lastReclaimOutcome = "";
        private String lastReclaimAction = "";
        private boolean reclaimInFlight;

        private Pending(long sequence, OrbisResourceRequest request,
                AiResourceRequirements requirements, ExecutionPlacement placement,
                ResourcePolicy policy,
                long queuedNanos, Consumer<OrbisResourceEvent> observer,
                CompletableFuture<Lease> future) {
            this.sequence = sequence;
            this.request = request;
            this.requirements = requirements;
            this.placement = placement;
            this.policy = policy;
            this.queuedNanos = queuedNanos;
            this.queuedAt = Instant.now();
            this.observer = observer;
            this.future = future;
        }
    }

    private record Active(OrbisResourceRequest request,
            AiResourceRequirements requirements, ExecutionPlacement placement,
            ResourcePolicy policy,
            boolean gpuCandidate, long startedNanos,
            Consumer<OrbisResourceEvent> observer) { }
}
