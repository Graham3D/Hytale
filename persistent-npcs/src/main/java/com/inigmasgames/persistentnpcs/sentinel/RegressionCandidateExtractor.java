package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.RegressionCandidate.*;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bounded asynchronous S3 candidate extraction, storage, and isolated replay. */
public final class RegressionCandidateExtractor implements AutoCloseable {
    public static final String SANITIZER_VERSION = "S3.1";
    private static final int MAX_QUEUE = 32;
    private static final int MAX_CANDIDATES = 512;
    private static final int MAX_SMOKE = 8;
    private static final Set<String> SAFE_FACTS = Set.of(
            "acceptedTranscriptCount", "acceptedTurnCount", "terminalTransitionCount",
            "cleanupAcquireCount", "cleanupReleaseCount", "planValid",
            "budgetedPromptHash", "dispatchedPromptHash", "authoritativeMode",
            "supportedEpistemicRoute", "authoritativeEpistemicContract",
            "actualDispatchFitsBudget", "providerEventOwned",
            "providerDeclaredReadyOrDrained", "providerActiveOwnerCount",
            "providerDeltaMonotonic", "schedulerReady",
            "schedulerSustainableForeground", "starvationRepeated", "residencyStable",
            "objectiveClaim", "compatibleClaimVerdict", "propertyAssertion",
            "propertyLevelSupport", "answerabilityRestricted", "unqualifiedCertainty",
            "canonicalSpansValid", "historyWritten", "playbackConfirmed", "actionClaim",
            "actionAuthority", "factualPromotionAttempt", "generatedSpeechOnlyEvidence",
            "provenancePresent", "nextUseAvailable", "configHash",
            "configurationHash", "provider", "route", "outputContract",
            "policyVersion", "beliefRevisionValid", "duplicateEventConsistent",
            "actionOccurrenceSupported", "persistenceProposalValid");
    private final Path root;
    private final Path exportRoot;
    private final String buildRevision;
    private final Consumer<String> diagnostics;
    private final Consumer<SentinelEvent> events;
    private final ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(MAX_QUEUE);
    private final LinkedHashMap<String, RegressionCandidate> candidates =
            new LinkedHashMap<>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, RegressionCandidate> eldest) {
                    return size() > MAX_CANDIDATES;
                }
            };
    private final Map<String, String> families = new LinkedHashMap<>();
    private final IncidentReplayHarness replayHarness = new IncidentReplayHarness();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong replayCount = new AtomicLong();
    private final Thread worker;
    private volatile BooleanSupplier idle = () -> false;
    private volatile String latestCandidate = "none";
    private volatile String latestReplay = "none";
    private volatile IncidentReplayHarness.ReplayReport latestReplayReport;

    public RegressionCandidateExtractor(Path modDataRoot, String buildRevision,
            Consumer<String> diagnostics, Consumer<SentinelEvent> events) {
        this.root = modDataRoot.resolve("diagnostics").resolve("regression-candidates");
        this.exportRoot = modDataRoot.resolve("diagnostics").resolve("exports");
        this.buildRevision = safe(buildRevision, "UNKNOWN");
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.events = events == null ? ignored -> { } : events;
        loadExisting();
        worker = new Thread(this::run, "orbis-regression-candidate-worker");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public void setIdleGate(BooleanSupplier value) {
        idle = value == null ? () -> false : value;
    }

    public void capture(String incidentId, SentinelEvent event,
            SentinelObservation observation, SentinelContracts.EnforcementDecision decision) {
        if (incidentId == null || event == null || observation == null || decision == null
                || event.failureSignature() == null || event.failureSignature().isBlank()) return;
        offer(() -> extract(incidentId, event, observation, decision));
    }

    private void extract(String incidentId, SentinelEvent event,
            SentinelObservation observation, SentinelContracts.EnforcementDecision decision) {
        FixtureKind kind = kind(event.invariantId(), observation.boundary());
        if (kind == null) return;
        Map<String, String> inputs = minimize(observation.facts());
        if (inputs.isEmpty()) return;
        String family = String.join("|", event.failureSignature(), buildRevision,
                RecoveryPolicyRegistry.VERSION,
                inputs.getOrDefault("configurationHash",
                        inputs.getOrDefault("configHash", "default")),
                decision.recoveryPolicyId());
        synchronized (candidates) {
            if (families.containsKey(family)) return;
        }
        String familyHash = hash(family);
        String id = "CAND-" + familyHash.substring(0, 20).toUpperCase();
        long seed = Long.parseUnsignedLong(familyHash.substring(0, 15), 16);
        LinkedHashMap<String, String> behavior = new LinkedHashMap<>();
        behavior.put("boundary", observation.boundary().name());
        behavior.put("recoveryPolicyId", safe(decision.recoveryPolicyId(), "none"));
        behavior.put("recoveryState", decision.recoveryState().name());
        behavior.put("circuitState", decision.circuitState().name());
        Instant now = Instant.now();
        RegressionCandidate raw = new RegressionCandidate(SCHEMA_VERSION, id, incidentId,
                buildRevision, event.npcId(), event.failureSignature(), event.invariantId(),
                kind, seed, inputs, behavior, "FAILURE_CONTAINED_AND_NEXT_USE_SAFE",
                capabilities(kind), CandidateStatus.REPLAYABLE, SANITIZER_VERSION, "", now, now);
        RegressionCandidate candidate = withChecksum(raw, CandidateStatus.REPLAYABLE, now);
        synchronized (candidates) {
            candidates.put(id, candidate);
            families.put(family, id);
            latestCandidate = id;
        }
        persist(candidate);
        emit("SENTINEL_REGRESSION_CANDIDATE_CREATED", event, id,
                event.failureSignature());
        diagnostics.accept("SENTINEL_REGRESSION_CANDIDATE_CREATED candidate=" + id
                + " kind=" + kind + " incident=" + incidentId);
        if (!decision.allowed() && idle.getAsBoolean()) replayInternal(candidate, true);
    }

    public CompletableFuture<IncidentReplayHarness.ReplayReport> replay(String candidateId) {
        var result = new CompletableFuture<IncidentReplayHarness.ReplayReport>();
        if (!offer(() -> {
            RegressionCandidate candidate;
            synchronized (candidates) { candidate = candidates.get(candidateId); }
            if (candidate == null) {
                result.completeExceptionally(new IllegalArgumentException(
                        "Unknown regression candidate: " + candidateId));
                return;
            }
            result.complete(replayInternal(candidate, false));
        })) result.completeExceptionally(new IllegalStateException("Replay queue is full"));
        return result;
    }

    public CompletableFuture<List<IncidentReplayHarness.ReplayReport>> smoke() {
        var result = new CompletableFuture<List<IncidentReplayHarness.ReplayReport>>();
        if (!offer(() -> {
            List<RegressionCandidate> selected;
            synchronized (candidates) {
                selected = candidates.values().stream()
                        .filter(value -> !buildRevision.equals(value.sourceBuildRevision()))
                        .filter(RegressionCandidateExtractor::unresolved)
                        .sorted(Comparator.comparing(RegressionCandidate::createdAt))
                        .limit(MAX_SMOKE).toList();
            }
            ArrayList<IncidentReplayHarness.ReplayReport> reports = new ArrayList<>();
            for (RegressionCandidate value : selected) reports.add(replayInternal(value, false));
            result.complete(List.copyOf(reports));
        })) result.completeExceptionally(new IllegalStateException("Replay queue is full"));
        return result;
    }

    private IncidentReplayHarness.ReplayReport replayInternal(RegressionCandidate candidate,
            boolean automatic) {
        if (!checksumMatches(candidate)) {
            IncidentReplayHarness.ReplayReport invalid = new IncidentReplayHarness.ReplayReport(
                    candidate.candidateId(), IncidentReplayHarness.ReplayOutcome
                            .HARNESS_CAPABILITY_MISSING,
                    false, false, true, true, true, "CHECKSUM_MISMATCH");
            latestReplayReport = invalid;
            latestReplay = candidate.candidateId() + ":CHECKSUM_MISMATCH";
            emit("SENTINEL_REPLAY_FAILED", candidate, "CHECKSUM_MISMATCH");
            return invalid;
        }
        replayCount.incrementAndGet();
        latestReplay = candidate.candidateId() + ":STARTED";
        emit("SENTINEL_REPLAY_STARTED", candidate, automatic ? "AUTO_IDLE" : "OPERATOR");
        IncidentReplayHarness.ReplayReport report = replayHarness.replay(candidate);
        CandidateStatus status = switch (report.outcome()) {
            case CONTAINMENT_VERIFIED, RECOVERY_VERIFIED,
                    FALSE_POSITIVE_OR_STALE_CANDIDATE ->
                        CandidateStatus.REPLAY_PASSED_CURRENT_BUILD;
            case REPRODUCED_UNRESOLVED -> CandidateStatus.REPLAY_FAILED_CURRENT_BUILD;
            case HARNESS_CAPABILITY_MISSING -> CandidateStatus.NON_DETERMINISTIC;
        };
        RegressionCandidate updated = withChecksum(candidate, status, Instant.now());
        synchronized (candidates) { candidates.put(updated.candidateId(), updated); }
        persist(updated);
        latestReplayReport = report;
        latestReplay = candidate.candidateId() + ':' + report.outcome();
        emit(status == CandidateStatus.REPLAY_PASSED_CURRENT_BUILD
                ? "SENTINEL_REPLAY_PASSED" : "SENTINEL_REPLAY_FAILED", candidate,
                report.outcome().name());
        return report;
    }

    public Path export(String candidateId) {
        RegressionCandidate value;
        synchronized (candidates) { value = candidates.get(candidateId); }
        if (value == null) throw new IllegalArgumentException(
                "Unknown regression candidate: " + candidateId);
        Path target = exportRoot.resolve(candidateId + ".json");
        JsonFiles.writeAtomic(target, value);
        return target;
    }

    public List<RegressionCandidate> candidates() {
        synchronized (candidates) { return List.copyOf(candidates.values()); }
    }

    public Snapshot snapshot() {
        synchronized (candidates) {
            long unresolved = candidates.values().stream()
                    .filter(RegressionCandidateExtractor::unresolved).count();
            return new Snapshot(candidates.size(), unresolved, latestCandidate,
                    latestReplay, latestReplayReport, queue.size(), dropped.get(),
                    replayCount.get());
        }
    }

    private void loadExisting() {
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.list(root)) {
            paths.filter(value -> value.getFileName().toString().endsWith(".json"))
                    .limit(MAX_CANDIDATES).forEach(path -> {
                        try {
                            RegressionCandidate value = JsonFiles.read(path,
                                    RegressionCandidate.class);
                            if (value != null && value.candidateId() != null) {
                                candidates.put(value.candidateId(), value);
                                latestCandidate = value.candidateId();
                            }
                        } catch (RuntimeException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    private void run() {
        while (!closed.get() || !queue.isEmpty()) {
            try {
                Runnable task = queue.poll(250, TimeUnit.MILLISECONDS);
                if (task != null) task.run();
            } catch (InterruptedException interrupted) {
                if (closed.get() && queue.isEmpty()) break;
            } catch (RuntimeException failure) {
                diagnostics.accept("SENTINEL_CANDIDATE_WORKER_FAILED type="
                        + failure.getClass().getSimpleName());
            }
        }
    }

    private boolean offer(Runnable task) {
        if (closed.get()) return false;
        boolean accepted = queue.offer(task);
        if (!accepted) dropped.incrementAndGet();
        return accepted;
    }

    private void persist(RegressionCandidate value) {
        JsonFiles.writeAtomic(root.resolve(value.candidateId() + ".json"), value);
    }

    private void emit(String type, SentinelEvent source, String reason, String signature) {
        events.accept(new SentinelEvent(type, source.npcId(), source.invariantId(),
                source.verdict(), source.severity(), source.confidence(), source.scopeKey(),
                reason, signature, source.occurrenceCount(), source.correlationIds(), 0,
                Instant.now()));
    }

    private void emit(String type, RegressionCandidate candidate, String reason) {
        events.accept(new SentinelEvent(type, candidate.sourceNpcId(),
                candidate.invariantId(), SentinelContracts.VerdictStatus.PASS,
                SentinelContracts.Severity.NOTICE, SentinelContracts.Confidence.PROVEN,
                "REPLAY:" + candidate.candidateId(), reason, candidate.failureSignature(),
                1, List.of(candidate.sourceIncidentId()), 0, Instant.now()));
    }

    private static RegressionCandidate withChecksum(RegressionCandidate value,
            CandidateStatus status, Instant at) {
        String checksum = hash(String.join("|", value.schemaVersion(), value.candidateId(),
                value.sourceIncidentId(), value.sourceBuildRevision(),
                value.failureSignature(), value.invariantId(), value.fixtureKind().name(),
                Long.toUnsignedString(value.deterministicSeed()), canonical(value.semanticInputs()),
                canonical(value.syntheticBehavior()), value.expectedInvariantOutcome(),
                value.requiredHarnessCapabilities().toString(), status.name(),
                value.sanitizerVersion(), value.createdAt().toString(), at.toString()));
        return value.withStatus(status, checksum, at);
    }

    private static boolean checksumMatches(RegressionCandidate value) {
        return value != null && value.payloadSha256() != null
                && value.payloadSha256().equals(withChecksum(value, value.status(),
                        value.updatedAt()).payloadSha256());
    }

    private static Map<String, String> minimize(Map<String, String> facts) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        facts.entrySet().stream().filter(value -> SAFE_FACTS.contains(value.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(value -> result.put(
                        value.getKey(), compact(value.getValue())));
        return Map.copyOf(result);
    }

    private static FixtureKind kind(String invariant, SentinelContracts.Boundary boundary) {
        if (invariant == null) return null;
        return switch (invariant) {
            case "TURN-001" -> FixtureKind.STT_PARTIAL_FINAL;
            case "TURN-002", "TURN-003" -> FixtureKind.QUEUE_CLEANUP;
            case "PLAN-001", "PLAN-004" -> FixtureKind.TURN_PLAN;
            case "PLAN-002" -> FixtureKind.PROMPT_BUDGET;
            case "PLAN-003" -> FixtureKind.ROUTE_AUTHORITY;
            case "PROV-001", "PROV-003" -> FixtureKind.PROVIDER_STREAM;
            case "PROV-002" -> FixtureKind.PROVIDER_CANCEL_DRAIN;
            case "RES-001", "RES-002", "RES-003" -> FixtureKind.RESOURCE_SEQUENCE;
            case "EPI-001", "EPI-002", "EPI-003" -> FixtureKind.ATOMIC_CLAIM;
            case "SPEECH-001", "SPEECH-002", "SPEECH-003" ->
                    FixtureKind.CANONICAL_SPEECH_LEDGER;
            case "ACT-001" -> FixtureKind.ACTION_AUTHORITY;
            case "EPI-004", "PERSIST-001", "PERSIST-002", "PERSIST-003",
                    "PERSIST-004", "PERSIST-005" -> FixtureKind.PERSISTENCE_EVENT;
            default -> boundary == SentinelContracts.Boundary.LATENCY_WINDOW_UPDATE
                    ? FixtureKind.LATENCY_WINDOW : null;
        };
    }

    private static List<String> capabilities(FixtureKind kind) {
        return switch (kind) {
            case PROVIDER_STREAM, PROVIDER_CANCEL_DRAIN ->
                    List.of("SyntheticNemotronProvider", "TurnStateModel");
            case STT_PARTIAL_FINAL -> List.of("SyntheticMoonshineProvider", "TurnStateModel");
            case RESOURCE_SEQUENCE -> List.of("SyntheticResourceScheduler", "TurnStateModel");
            case CANONICAL_SPEECH_LEDGER ->
                    List.of("SyntheticChatterboxProvider", "GoldenTraceAssertions");
            case PERSISTENCE_EVENT -> List.of("persistence-fixtures", "TurnStateModel");
            default -> List.of("ConversationMatrixHarness", "GoldenTraceAssertions");
        };
    }

    private static boolean unresolved(RegressionCandidate value) {
        return value.status() == CandidateStatus.NEW
                || value.status() == CandidateStatus.REPLAYABLE
                || value.status() == CandidateStatus.REPLAY_FAILED_CURRENT_BUILD;
    }

    private static String hash(String text) {
        return SentinelPromptIdentity.hash(List.of(new ChatMessage("candidate", text)));
    }
    private static String compact(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        return clean.length() <= 256 ? clean : clean.substring(0, 256);
    }
    private static String canonical(Map<String, String> value) {
        return new java.util.TreeMap<>(value == null ? Map.of() : value).toString();
    }
    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : compact(value);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        worker.interrupt();
        try { worker.join(2_000); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public record Snapshot(int totalCandidates, long unresolvedCandidates,
            String latestCandidate, String latestReplay,
            IncidentReplayHarness.ReplayReport latestReplayReport, int queueDepth,
            long droppedTasks, long replayCount) { }
}
