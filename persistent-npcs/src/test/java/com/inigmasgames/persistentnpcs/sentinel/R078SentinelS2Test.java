package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Targeted S2 enforcement, bounded recovery, circuit and incident gate. */
public final class R078SentinelS2Test {
    private R078SentinelS2Test() { }
    public static void main(String[] args) throws Exception {
        policiesPromoteOnlyApprovedProvenInvariants();
        enforceContainsUnsafeSideEffects();
        observeNeverContains();
        repeatedSignatureOpensOnlyScopedCircuit();
        halfOpenRequiresVerifiedNextUse();
        incidentsAreAutomaticAndDeduplicated();
        incidentWriterFailureCannotFailGameplay();
        currentPersistenceWriterRejectsSpeechOnlyEvidence();
        guardOverheadRemainsBounded();
        System.out.println("R078 Sentinel S2 tests passed.");
    }

    private static void policiesPromoteOnlyApprovedProvenInvariants() {
        var policies = new RecoveryPolicyRegistry();
        for (String id : List.of("PLAN-002", "PLAN-003", "PLAN-004", "PROV-001",
                "PROV-002", "RES-001", "RES-002", "EPI-001", "EPI-002",
                "EPI-003", "SPEECH-001", "SPEECH-002", "ACT-001", "PERSIST-001")) {
            var policy = policies.forInvariant(id);
            assert policy != null : id;
            assert policy.maxAttemptsPerTurn() == 1;
            assert policy.actions().stream().allMatch(java.util.Objects::nonNull);
        }
        assert policies.forInvariant("TURN-001") == null;
    }

    private static void enforceContainsUnsafeSideEffects() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        assert !sentinel.guard(obs(Boundary.PROVIDER_DISPATCH, "ROUTE:PROPERTY",
                badPlan())).allowed();
        assert !sentinel.guard(obs(Boundary.CLAIM_VALIDATION, "ROUTE:PROPERTY", facts(
                "objectiveClaim", "true", "compatibleClaimVerdict", "false",
                "propertyAssertion", "true", "propertyLevelSupport", "false",
                "answerabilityRestricted", "true", "unqualifiedCertainty", "true")))
                .allowed();
        assert !sentinel.guard(obs(Boundary.SPEECH_LEDGER_APPEND, "TURN:speech", facts(
                "objectiveClaim", "true", "compatibleClaimVerdict", "false",
                "canonicalSpansValid", "false"))).allowed();
        assert !sentinel.guard(obs(Boundary.ACTION_COMMIT, "TURN:action", facts(
                "actionClaim", "true", "actionAuthority", "false"))).allowed();
        assert !sentinel.guard(obs(Boundary.BELIEF_WRITE_PROPOSED,
                "PERSISTENCE_STREAM:MARA", facts("factualPromotionAttempt", "true",
                        "provenancePresent", "false",
                        "generatedSpeechOnlyEvidence", "true"))).allowed();
    }

    private static void observeNeverContains() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.OBSERVE, ignored -> { });
        EnforcementDecision decision = sentinel.guard(obs(Boundary.PROVIDER_DISPATCH,
                "ROUTE:PROPERTY", badPlan()));
        assert decision.allowed();
        assert decision.recoveryState() == RecoveryState.REQUESTED;
    }

    private static void repeatedSignatureOpensOnlyScopedCircuit() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        String scope = "ROUTE:OBJECTIVE_PROPERTY";
        EnforcementDecision third = null;
        for (int index = 0; index < 3; index++) third = sentinel.guard(
                obs(Boundary.PROVIDER_DISPATCH, scope, badPlan()));
        assert third != null && third.circuitState() == CircuitState.OPEN;
        EnforcementDecision fast = sentinel.guard(obs(Boundary.PROVIDER_DISPATCH,
                scope, healthyPlan()));
        assert !fast.allowed() && fast.recoveryState() == RecoveryState.SKIPPED_CIRCUIT_OPEN;
        assert sentinel.guard(obs(Boundary.PROVIDER_DISPATCH,
                "ROUTE:GENERAL_SOCIAL", healthyPlan())).allowed();
        assert sentinel.snapshot().scopedHealth().get(scope) == Health.QUARANTINED;
    }

    private static void halfOpenRequiresVerifiedNextUse() {
        MutableClock clock = new MutableClock();
        var circuits = new ScopedCircuitBreakerRegistry(2, Duration.ofSeconds(5), clock);
        assert circuits.failure("route") == CircuitState.CLOSED;
        assert circuits.failure("route") == CircuitState.OPEN;
        clock.advance(Duration.ofSeconds(6));
        assert circuits.state("route") == CircuitState.HALF_OPEN;
        circuits.verified("route");
        assert circuits.state("route") == CircuitState.CLOSED;

        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        EnforcementDecision failed = sentinel.guard(obs(Boundary.PROVIDER_DISPATCH,
                "TURN:verify", badPlan()));
        Map<String, String> proof = new LinkedHashMap<>(healthyPlan());
        proof.put("nextUseAvailable", "true");
        assert sentinel.verifyRecovery(failed, obs(Boundary.PROVIDER_DISPATCH,
                "TURN:verify", proof));
        assert sentinel.snapshot().scopedHealth().get("TURN:verify") == Health.HEALTHY;
    }

    private static void incidentsAreAutomaticAndDeduplicated() throws Exception {
        Path root = Files.createTempDirectory("r078-incidents-");
        try (var recorder = new OrbisIncidentRecorder(root, ignored -> { })) {
            var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
            sentinel.setIncidentRecorder(recorder);
            for (int index = 0; index < 3; index++) sentinel.guard(obs(
                    Boundary.PROVIDER_DISPATCH, "ROUTE:PROPERTY", badPlan()));
            recorder.awaitIdle();
            var snapshot = recorder.snapshot();
            assert snapshot.uniqueSignatures() >= 1;
            assert snapshot.droppedWrites() == 0;
            long bundles;
            try (var files = Files.walk(root)) {
                bundles = files.filter(path -> path.toString().endsWith(".json")).count();
            }
            assert bundles >= 1 && bundles < 3 : bundles;
            try (var files = Files.walk(root)) {
                assert files.anyMatch(path -> path.getFileName().toString()
                        .equals("occurrences.jsonl"));
            }
        }
    }

    private static void incidentWriterFailureCannotFailGameplay() throws Exception {
        Path fileRoot = Files.createTempFile("r078-not-directory-", ".tmp");
        try (var recorder = new OrbisIncidentRecorder(fileRoot, ignored -> { })) {
            var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
            sentinel.setIncidentRecorder(recorder);
            EnforcementDecision result = sentinel.guard(obs(Boundary.PROVIDER_DISPATCH,
                    "TURN:writer-failure", badPlan()));
            assert !result.allowed();
            recorder.awaitIdle();
            assert recorder.snapshot().writerFailures() >= 1;
        }
    }

    private static void guardOverheadRemainsBounded() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        SentinelObservation healthy = obs(Boundary.PROVIDER_DISPATCH,
                "ROUTE:HEALTHY", healthyPlan());
        for (int i = 0; i < 2_000; i++) sentinel.guard(healthy);
        long[] values = new long[8_000];
        for (int i = 0; i < values.length; i++) {
            long start = System.nanoTime(); sentinel.guard(healthy);
            values[i] = (System.nanoTime() - start) / 1_000;
        }
        java.util.Arrays.sort(values);
        long p95 = values[(int) (values.length * .95)];
        assert p95 <= 3_000 : p95;
        System.out.println("S2 guard overhead p95=" + p95 + "us");
    }

    private static void currentPersistenceWriterRejectsSpeechOnlyEvidence() throws Exception {
        Path root = Files.createTempDirectory("r078-persistence-");
        var store = new com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore(root);
        store.load();
        store.setDegradationSentinel(new OrbisDegradationSentinel(
                SentinelMode.ENFORCE, ignored -> { }));
        UUID npc = UUID.randomUUID(), source = UUID.randomUUID(), response = UUID.randomUUID();
        var unsafe = new com.inigmasgames.persistentnpcs.cognition.SourcedBelief(
                UUID.randomUUID(), npc, source, source, "player", "REPORT", "claim",
                Instant.now(), .8, .5, UUID.randomUUID(), response,
                List.of("RESPONSE:" + response));
        for (int index = 0; index < 3; index++) {
            boolean rejected = false;
            try { store.append(unsafe); }
            catch (SentinelGuardException expected) { rejected = true; }
            assert rejected;
        }
        assert store.readOnly(npc);
    }

    private static Map<String, String> healthyPlan() {
        return facts("planValid", "true", "budgetedPromptHash", "same",
                "dispatchedPromptHash", "same", "actualDispatchFitsBudget", "true",
                "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                "authoritativeEpistemicContract", "true", "route", "OBJECTIVE_PROPERTY",
                "outputContract", "DIALOGUE_TEXT");
    }
    private static Map<String, String> badPlan() {
        return facts("planValid", "true", "budgetedPromptHash", "a",
                "dispatchedPromptHash", "b", "actualDispatchFitsBudget", "false",
                "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                "authoritativeEpistemicContract", "false", "route", "OBJECTIVE_PROPERTY",
                "outputContract", "DIALOGUE_TEXT");
    }
    private static SentinelObservation obs(Boundary boundary, String scope,
            Map<String, String> facts) {
        return new SentinelObservation(boundary, scope, UUID.randomUUID(),
                List.of("responseId=" + UUID.randomUUID()), facts);
    }
    private static Map<String, String> facts(String... pairs) {
        var values = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return Map.copyOf(values);
    }
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-31T12:00:00Z");
        void advance(Duration value) { now = now.plus(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
