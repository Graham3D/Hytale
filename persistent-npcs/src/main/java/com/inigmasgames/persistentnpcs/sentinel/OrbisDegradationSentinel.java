package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * S1 deterministic observer. It consumes immutable proofs, projects health and emits
 * diagnostics; it has no authority to block, retry, recover, route, persist or release.
 */
public final class OrbisDegradationSentinel {
    private final InvariantRegistry registry;
    private final FailureSignatureEngine signatures;
    private final SentinelStateProjection projection;
    private final Consumer<SentinelEvent> events;
    private final AtomicReference<SentinelMode> configuredMode;
    private final RecoveryPolicyRegistry recoveryPolicies = new RecoveryPolicyRegistry();
    private final ScopedCircuitBreakerRegistry circuits = new ScopedCircuitBreakerRegistry();
    private final java.util.concurrent.ConcurrentHashMap<String, String> quarantinedScopes =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile OrbisIncidentRecorder incidents;
    private volatile RegressionCandidateExtractor regressionCandidates;

    public OrbisDegradationSentinel() {
        this(SentinelMode.OBSERVE, ignored -> { });
    }

    public OrbisDegradationSentinel(SentinelMode mode, Consumer<SentinelEvent> events) {
        registry = new InvariantRegistry();
        signatures = new FailureSignatureEngine();
        projection = new SentinelStateProjection();
        this.events = events == null ? ignored -> { } : events;
        configuredMode = new AtomicReference<>(mode == null ? SentinelMode.OBSERVE : mode);
    }

    public void setIncidentRecorder(OrbisIncidentRecorder incidents) {
        this.incidents = incidents;
    }

    public void setRegressionCandidates(RegressionCandidateExtractor value) {
        regressionCandidates = value;
    }

    /** S2 pre-side-effect guard. Only promoted PROVEN failures block in ENFORCE. */
    public EnforcementDecision guard(SentinelObservation observation) {
        if (observation == null || configuredMode.get() == SentinelMode.OFF) {
            return EnforcementDecision.allow();
        }
        String openSignature = quarantinedScopes.get(observation.scopeKey());
        if (openSignature != null) {
            CircuitState state = circuits.state(openSignature + '|' + observation.scopeKey());
            if (state == CircuitState.OPEN) return new EnforcementDecision(false,
                    "CIRCUIT", "SCOPED_CIRCUIT_OPEN", openSignature,
                    "S2_FAIL_FAST_OPEN_CIRCUIT", RecoveryState.SKIPPED_CIRCUIT_OPEN,
                    state, List.of(RecoveryAction.FAIL_FAST_WITH_SAFE_REASON));
            if (state == CircuitState.HALF_OPEN) projection.health(
                    observation.scopeKey(), Health.RECOVERING);
        }
        List<InvariantVerdict> verdicts = observe(observation);
        for (InvariantVerdict verdict : verdicts) {
            if (verdict.status() != VerdictStatus.FAIL
                    || verdict.confidence() != Confidence.PROVEN
                    || !promoted(verdict.invariantId())) continue;
            InvariantDefinition definition = registry.require(verdict.invariantId());
            String signature = signatures.signature(definition,
                    verdict.boundedReasonCode(), observation);
            RecoveryPolicyRegistry.Policy policy = recoveryPolicies.forInvariant(
                    verdict.invariantId());
            CircuitState circuit = circuits.failure(signature + '|'
                    + observation.scopeKey());
            if (circuit == CircuitState.OPEN) {
                quarantinedScopes.put(observation.scopeKey(), signature);
                projection.health(observation.scopeKey(), Health.QUARANTINED);
            } else projection.health(observation.scopeKey(), Health.RECOVERING);
            boolean enforce = configuredMode.get() == SentinelMode.ENFORCE;
            List<RecoveryAction> actions = policy == null ? List.of(
                    RecoveryAction.REJECT_SIDE_EFFECT) : policy.actions();
            EnforcementDecision decision = new EnforcementDecision(!enforce,
                    verdict.invariantId(), verdict.boundedReasonCode(), signature,
                    policy == null ? "S2_CONTAIN_ONLY" : policy.id(),
                    enforce ? RecoveryState.CONTAINED : RecoveryState.REQUESTED,
                    circuit, actions);
            SentinelEvent event = new SentinelEvent("SENTINEL_RECOVERY_REQUESTED",
                    observation.npcId(), verdict.invariantId(), verdict.status(),
                    definition.severity(), verdict.confidence(), observation.scopeKey(),
                    verdict.boundedReasonCode(), signature,
                    projection.snapshot().signatureOccurrences().getOrDefault(signature, 0),
                    observation.correlationIds(), verdict.evaluationMicros(), Instant.now());
            emit(event);
            if (circuit == CircuitState.OPEN) emit(new SentinelEvent(
                    "SENTINEL_CIRCUIT_OPEN", observation.npcId(), verdict.invariantId(),
                    verdict.status(), definition.severity(), verdict.confidence(),
                    observation.scopeKey(), "REPEATED_IDENTICAL_SIGNATURE", signature,
                    projection.snapshot().signatureOccurrences().getOrDefault(signature, 0),
                    observation.correlationIds(), verdict.evaluationMicros(), Instant.now()));
            OrbisIncidentRecorder recorder = incidents;
            if (recorder != null) recorder.capture(event, observation, decision);
            return decision;
        }
        if (openSignature != null) {
            circuits.verified(openSignature + '|' + observation.scopeKey());
            quarantinedScopes.remove(observation.scopeKey());
            projection.health(observation.scopeKey(), Health.HEALTHY);
        }
        return EnforcementDecision.allow();
    }

    public void requireAllowed(SentinelObservation observation) {
        EnforcementDecision decision = guard(observation);
        if (!decision.allowed()) throw new SentinelGuardException(
                decision.invariantId(), decision.reasonCode());
    }

    public boolean verifyRecovery(EnforcementDecision decision,
            SentinelObservation postconditionProof) {
        if (decision == null || decision.failureSignature().isBlank()
                || postconditionProof == null || !postconditionProof.bool("nextUseAvailable")) {
            return false;
        }
        boolean invariantPasses = observe(postconditionProof).stream()
                .filter(value -> value.invariantId().equals(decision.invariantId()))
                .allMatch(value -> value.status() == VerdictStatus.PASS
                        || value.status() == VerdictStatus.NOT_APPLICABLE);
        if (!invariantPasses) return false;
        String key = decision.failureSignature() + '|' + postconditionProof.scopeKey();
        circuits.verified(key);
        quarantinedScopes.remove(postconditionProof.scopeKey());
        projection.health(postconditionProof.scopeKey(), Health.HEALTHY);
        emit(new SentinelEvent("SENTINEL_RECOVERY_VERIFIED", postconditionProof.npcId(),
                decision.invariantId(), VerdictStatus.PASS, Severity.NOTICE,
                Confidence.PROVEN, postconditionProof.scopeKey(),
                "ORIGINAL_INVARIANT_AND_NEXT_USE_PASS", decision.failureSignature(), 0,
                postconditionProof.correlationIds(), 0, Instant.now()));
        return true;
    }

    private static boolean promoted(String id) {
        return java.util.Set.of("PLAN-002", "PLAN-003", "PLAN-004", "PROV-001",
                "PROV-002", "RES-001", "RES-002", "EPI-001", "EPI-002",
                "EPI-003", "SPEECH-001", "SPEECH-002", "ACT-001", "PERSIST-001",
                "PERSIST-002", "PERSIST-003", "PERSIST-004", "PERSIST-005")
                .contains(id);
    }

    public List<InvariantVerdict> observe(SentinelObservation observation) {
        if (observation == null || configuredMode.get() == SentinelMode.OFF) return List.of();
        projection.observe(observation);
        var results = new ArrayList<InvariantVerdict>();
        for (InvariantDefinition definition : registry.at(observation.boundary())) {
            if (!definition.enabledInObserve()) continue;
            long started = System.nanoTime();
            Evaluation evaluation;
            try { evaluation = evaluate(definition.id(), observation); }
            catch (RuntimeException failure) {
                evaluation = new Evaluation(VerdictStatus.EVALUATOR_ERROR,
                        Confidence.SUSPECT, "EVALUATOR_EXCEPTION_"
                                + failure.getClass().getSimpleName().toUpperCase(Locale.ROOT));
            }
            long micros = Math.max(0, (System.nanoTime() - started) / 1_000L);
            var verdict = new InvariantVerdict(definition.id(), evaluation.status(),
                    evaluation.confidence(), evaluation.reason(),
                    observation.correlationIds(), Instant.now(), micros);
            results.add(verdict);
            String signature = null;
            if (verdict.status() == VerdictStatus.FAIL
                    || verdict.status() == VerdictStatus.EVALUATOR_ERROR) {
                signature = signatures.signature(definition, verdict.boundedReasonCode(),
                        observation);
            }
            Health before = projection.snapshot().scopedHealth()
                    .getOrDefault(observation.scopeKey(), Health.HEALTHY);
            Health after = projection.apply(definition, verdict, observation.scopeKey(),
                    signature);
            if (signature != null) {
                int count = projection.snapshot().signatureOccurrences()
                        .getOrDefault(signature, 0);
                emit(new SentinelEvent("SENTINEL_INVARIANT_VIOLATED", observation.npcId(),
                        definition.id(), verdict.status(), definition.severity(),
                        verdict.confidence(), observation.scopeKey(),
                        verdict.boundedReasonCode(), signature, count,
                        observation.correlationIds(), micros, Instant.now()));
                emit(new SentinelEvent("SENTINEL_DEGRADATION_SIGNAL", observation.npcId(),
                        definition.id(), verdict.status(), definition.severity(),
                        verdict.confidence(), observation.scopeKey(),
                        verdict.boundedReasonCode(), signature, count,
                        observation.correlationIds(), micros, Instant.now()));
            }
            if (before != after) emit(new SentinelEvent("SENTINEL_HEALTH_CHANGED",
                    observation.npcId(), definition.id(), verdict.status(),
                    definition.severity(), verdict.confidence(), observation.scopeKey(),
                    before + "_TO_" + after, signature, signature == null ? 0
                            : projection.snapshot().signatureOccurrences()
                                    .getOrDefault(signature, 0),
                    observation.correlationIds(), micros, Instant.now()));
        }
        return List.copyOf(results);
    }

    private void emit(SentinelEvent event) {
        try { events.accept(event); } catch (RuntimeException ignored) {
            // Diagnostics cannot recurse into or affect production behavior.
        }
    }

    private static Evaluation evaluate(String id, SentinelObservation o) {
        return switch (id) {
            case "TURN-001" -> requiredCounts(o, "acceptedTranscriptCount", 1,
                    "acceptedTurnCount", 1, "DUPLICATE_OR_MISSING_UTTERANCE_TURN");
            case "TURN-002" -> exact(o, "terminalTransitionCount", 1,
                    "DUPLICATE_OR_MISSING_TERMINAL");
            case "TURN-003" -> exactPair(o, "cleanupAcquireCount", "cleanupReleaseCount", 1,
                    "DUPLICATE_OR_LEAKED_CLEANUP");
            case "PLAN-001" -> truth(o, "planValid", "PROVIDER_DISPATCH_WITHOUT_VALID_PLAN");
            case "PLAN-002" -> equalText(o, "budgetedPromptHash", "dispatchedPromptHash",
                    "POST_RENDER_PROMPT_DRIFT");
            case "PLAN-003" -> !o.bool("authoritativeMode")
                    || !o.bool("supportedEpistemicRoute")
                            ? na() : truth(o, "authoritativeEpistemicContract",
                                    "EPISTEMIC_ROUTE_NOT_AUTHORITATIVE");
            case "PLAN-004" -> truth(o, "actualDispatchFitsBudget",
                    "ACTUAL_DISPATCH_EXCEEDS_COMPILED_BUDGET");
            case "PROV-001" -> truth(o, "providerEventOwned",
                    "STALE_PROVIDER_EVENT_OWNERSHIP");
            case "PROV-002" -> !o.bool("providerDeclaredReadyOrDrained") ? na()
                    : exact(o, "providerActiveOwnerCount", 0,
                            "PROVIDER_FALSE_READY_OR_DRAIN");
            case "PROV-003" -> truth(o, "providerDeltaMonotonic",
                    "PROVIDER_DELTA_NON_MONOTONIC_OR_DUPLICATE");
            case "RES-001" -> !o.bool("schedulerReady") ? na()
                    : truth(o, "schedulerSustainableForeground",
                            "FALSE_READY_RESOURCE_ENVELOPE");
            case "RES-002" -> o.fact("starvationRepeated") == null ? insufficient()
                    : o.bool("starvationRepeated") ? fail("REPEATED_RESOURCE_STARVATION")
                            : pass();
            case "RES-003" -> truth(o, "residencyStable",
                    "PROVIDER_RESIDENCY_PING_PONG");
            case "EPI-001" -> !o.bool("objectiveClaim") ? na()
                    : truth(o, "compatibleClaimVerdict", "OBJECTIVE_CLAIM_MISSING_VERDICT");
            case "EPI-002" -> !o.bool("propertyAssertion") ? na()
                    : truth(o, "propertyLevelSupport", "ENTITY_ONLY_PROPERTY_SUPPORT");
            case "EPI-003" -> !o.bool("answerabilityRestricted") ? na()
                    : falsity(o, "unqualifiedCertainty", "RESTRICTED_ANSWER_BECAME_CERTAIN");
            case "SPEECH-001" -> !o.bool("objectiveClaim") ? na()
                    : truth(o, "compatibleClaimVerdict", "LEDGER_APPEND_MISSING_CLAIM_EVIDENCE");
            case "SPEECH-002" -> truth(o, "canonicalSpansValid",
                    "CANONICAL_SPAN_ORDER_OR_CONTIGUITY");
            case "SPEECH-003" -> !o.bool("historyWritten") ? na()
                    : truth(o, "playbackConfirmed", "UNDELIVERED_TEXT_ENTERED_HISTORY");
            case "ACT-001" -> !o.bool("actionClaim") ? na()
                    : truth(o, "actionAuthority", "ACTION_CLAIM_WITHOUT_RESULT_AUTHORITY");
            case "EPI-004" -> !o.bool("factualPromotionAttempt") ? na()
                    : falsity(o, "generatedSpeechOnlyEvidence",
                            "GENERATED_SPEECH_PROMOTED_AS_EVIDENCE");
            case "PERSIST-001" -> !o.bool("factualPromotionAttempt") ? na()
                    : both(o, "provenancePresent", true, "generatedSpeechOnlyEvidence", false,
                            "PERSISTENCE_PROVENANCE_OR_SPEECH_CONTAMINATION");
            case "PERSIST-002" -> truth(o, "beliefRevisionValid",
                    "INVALID_BELIEF_REVISION");
            case "PERSIST-003" -> truth(o, "duplicateEventConsistent",
                    "DIVERGENT_DUPLICATE_BELIEF_EVENT");
            case "PERSIST-004" -> truth(o, "actionOccurrenceSupported",
                    "UNSUPPORTED_ACTION_OCCURRENCE_BELIEF");
            case "PERSIST-005" -> truth(o, "persistenceProposalValid",
                    "CORRUPT_BELIEF_PERSISTENCE_PROPOSAL");
            default -> insufficient();
        };
    }

    private static Evaluation truth(SentinelObservation o, String key, String reason) {
        return o.fact(key) == null ? insufficient() : o.bool(key) ? pass() : fail(reason);
    }
    private static Evaluation falsity(SentinelObservation o, String key, String reason) {
        return o.fact(key) == null ? insufficient() : !o.bool(key) ? pass() : fail(reason);
    }
    private static Evaluation exact(SentinelObservation o, String key, int expected,
            String reason) {
        return o.fact(key) == null ? insufficient()
                : o.integer(key, Integer.MIN_VALUE) == expected ? pass() : fail(reason);
    }
    private static Evaluation equalCounts(SentinelObservation o, String left, String right,
            String reason) {
        if (o.fact(left) == null || o.fact(right) == null) return insufficient();
        return o.integer(left, -1) == o.integer(right, -2) ? pass() : fail(reason);
    }
    private static Evaluation exactPair(SentinelObservation o, String left, String right,
            int expected, String reason) {
        if (o.fact(left) == null || o.fact(right) == null) return insufficient();
        return o.integer(left, -1) == expected && o.integer(right, -1) == expected
                ? pass() : fail(reason);
    }
    private static Evaluation requiredCounts(SentinelObservation o, String first, int firstValue,
            String second, int secondValue, String reason) {
        if (o.fact(first) == null || o.fact(second) == null) return insufficient();
        return o.integer(first, -1) == firstValue && o.integer(second, -1) == secondValue
                ? pass() : fail(reason);
    }
    private static Evaluation equalText(SentinelObservation o, String left, String right,
            String reason) {
        if (o.fact(left) == null || o.fact(right) == null) return insufficient();
        return o.fact(left).equals(o.fact(right)) ? pass() : fail(reason);
    }
    private static Evaluation both(SentinelObservation o, String first, boolean firstValue,
            String second, boolean secondValue, String reason) {
        if (o.fact(first) == null || o.fact(second) == null) return insufficient();
        return o.bool(first) == firstValue && o.bool(second) == secondValue
                ? pass() : fail(reason);
    }
    private static Evaluation pass() {
        return new Evaluation(VerdictStatus.PASS, Confidence.PROVEN, "PROOF_PRESENT");
    }
    private static Evaluation fail(String reason) {
        return new Evaluation(VerdictStatus.FAIL, Confidence.PROVEN, reason);
    }
    private static Evaluation na() {
        return new Evaluation(VerdictStatus.NOT_APPLICABLE, Confidence.PROVEN,
                "NOT_APPLICABLE");
    }
    private static Evaluation insufficient() {
        return new Evaluation(VerdictStatus.INSUFFICIENT_DATA,
                Confidence.INSUFFICIENT_DATA, "INSUFFICIENT_IMMUTABLE_PROOF");
    }

    public SentinelMode configuredMode() { return configuredMode.get(); }
    public SentinelMode effectiveMode() {
        return configuredMode.get();
    }
    public void configure(SentinelMode mode) {
        configuredMode.set(mode == null ? SentinelMode.OBSERVE : mode);
    }
    public InvariantRegistry registry() { return registry; }
    public RecoveryPolicyRegistry recoveryPolicies() { return recoveryPolicies; }
    public ScopedCircuitBreakerRegistry circuits() { return circuits; }
    public java.util.Map<String, String> quarantinedScopes() {
        return java.util.Map.copyOf(quarantinedScopes);
    }
    public SentinelStateProjection.Snapshot snapshot() { return projection.snapshot(); }
    public String diagnostics() {
        var value = snapshot();
        return "Sentinel mode=" + configuredMode() + " effective=" + effectiveMode()
                + " registry=" + registry.version() + " activeViolations="
                + value.activeViolations() + " scopedHealth=" + value.scopedHealth()
                + " lastSignature=" + value.lastSignature() + " occurrences="
                + value.lastOccurrenceCount() + " evaluatorAvgMicros="
                + "%.2f".formatted(value.averageEvaluationMicros())
                + " recoveryPolicy=" + RecoveryPolicyRegistry.VERSION
                + " circuits=" + circuits.snapshot()
                + " quarantinedScopes=" + quarantinedScopes
                + (incidents == null ? "" : " lastIncident="
                        + incidents.snapshot().lastIncidentId()
                        + " incidentQueue=" + incidents.snapshot().queueDepth())
                + (regressionCandidates == null ? "" : " candidates="
                        + regressionCandidates.snapshot().totalCandidates()
                        + " unresolvedCandidates="
                        + regressionCandidates.snapshot().unresolvedCandidates()
                        + " latestCandidate="
                        + regressionCandidates.snapshot().latestCandidate()
                        + " latestReplay="
                        + regressionCandidates.snapshot().latestReplay());
    }

    private record Evaluation(VerdictStatus status, Confidence confidence, String reason) { }
}
