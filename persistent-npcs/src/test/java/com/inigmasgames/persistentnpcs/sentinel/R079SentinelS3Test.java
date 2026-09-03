package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Targeted S3 extraction, minimization, replay, isolation and smoke gate. */
public final class R079SentinelS3Test {
    private R079SentinelS3Test() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r079-s3-");
        extractionReplayAndDedup(root);
        automaticIdleReplay(root.resolve("automatic"));
        priorRevisionSmoke(root.resolve("prior"));
        healthyGuardPerformanceUnaffected();
        System.out.println("R079 Sentinel S3 tests passed.");
    }

    private static void automaticIdleReplay(Path root) {
        try (var extractor = new RegressionCandidateExtractor(root, "R079",
                     ignored -> { }, ignored -> { });
             var recorder = new OrbisIncidentRecorder(root, ignored -> { })) {
            extractor.setIdleGate(() -> true);
            recorder.setCandidateExtractor(extractor);
            var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE,
                    ignored -> { });
            sentinel.setIncidentRecorder(recorder);
            fail(sentinel, Boundary.SPEECH_LEDGER_APPEND, facts(
                    "objectiveClaim", "false", "canonicalSpansValid", "false"));
            await(() -> extractor.snapshot().latestReplayReport() != null);
            assert extractor.snapshot().replayCount() == 1;
            assert extractor.snapshot().latestReplay().contains("RECOVERY_VERIFIED")
                    : extractor.snapshot();
        }
    }

    private static void extractionReplayAndDedup(Path root) throws Exception {
        try (var extractor = new RegressionCandidateExtractor(root, "R079",
                     ignored -> { }, ignored -> { });
             var recorder = new OrbisIncidentRecorder(root, ignored -> { })) {
            recorder.setCandidateExtractor(extractor);
            var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE,
                    ignored -> { });
            sentinel.setIncidentRecorder(recorder);
            sentinel.setRegressionCandidates(extractor);

            fail(sentinel, Boundary.PROVIDER_DISPATCH, facts(
                    "planValid", "true", "budgetedPromptHash", "same",
                    "dispatchedPromptHash", "same", "actualDispatchFitsBudget", "true",
                    "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                    "authoritativeEpistemicContract", "false"));
            fail(sentinel, Boundary.PROVIDER_DISPATCH, facts(
                    "planValid", "true", "budgetedPromptHash", "a",
                    "dispatchedPromptHash", "b", "actualDispatchFitsBudget", "true",
                    "authoritativeMode", "false", "supportedEpistemicRoute", "false"));
            fail(sentinel, Boundary.CLAIM_VALIDATION, facts(
                    "objectiveClaim", "true", "compatibleClaimVerdict", "false"));
            fail(sentinel, Boundary.PROVIDER_TERMINAL, facts(
                    "providerDeclaredReadyOrDrained", "true",
                    "providerActiveOwnerCount", "1"));
            fail(sentinel, Boundary.READINESS_SAMPLE, facts(
                    "schedulerReady", "true", "schedulerSustainableForeground", "false"));
            fail(sentinel, Boundary.SPEECH_LEDGER_APPEND, facts(
                    "objectiveClaim", "false", "canonicalSpansValid", "false"));
            fail(sentinel, Boundary.BELIEF_WRITE_PROPOSED, facts(
                    "factualPromotionAttempt", "true", "provenancePresent", "false",
                    "generatedSpeechOnlyEvidence", "true"));
            // Same root signature must not create a second candidate family.
            fail(sentinel, Boundary.SPEECH_LEDGER_APPEND, facts(
                    "objectiveClaim", "false", "canonicalSpansValid", "false"));
            await(() -> extractor.snapshot().totalCandidates() >= 7);
            assert extractor.snapshot().totalCandidates() == 7
                    : extractor.candidates();

            var kinds = extractor.candidates().stream()
                    .map(RegressionCandidate::fixtureKind).collect(java.util.stream.Collectors
                            .toSet());
            assert kinds.containsAll(List.of(RegressionCandidate.FixtureKind.ROUTE_AUTHORITY,
                    RegressionCandidate.FixtureKind.PROMPT_BUDGET,
                    RegressionCandidate.FixtureKind.ATOMIC_CLAIM,
                    RegressionCandidate.FixtureKind.PROVIDER_CANCEL_DRAIN,
                    RegressionCandidate.FixtureKind.RESOURCE_SEQUENCE,
                    RegressionCandidate.FixtureKind.CANONICAL_SPEECH_LEDGER,
                    RegressionCandidate.FixtureKind.PERSISTENCE_EVENT)) : kinds;
            for (RegressionCandidate candidate : extractor.candidates()) {
                assert candidate.payloadSha256().matches("[0-9a-f]{64}");
                assert candidate.semanticInputs().keySet().stream().noneMatch(key ->
                        key.toLowerCase().contains("audio")
                                || key.toLowerCase().contains("reasoning")
                                || key.toLowerCase().contains("secret"));
                var replay = extractor.replay(candidate.candidateId()).join();
                assert replay.unsafeSideEffectBlocked() : replay;
                assert replay.durableStateUnmodified() : replay;
                assert replay.nextUseAvailable() : replay;
                assert replay.outcome() == IncidentReplayHarness.ReplayOutcome.RECOVERY_VERIFIED
                        || replay.outcome() == IncidentReplayHarness.ReplayOutcome
                                .CONTAINMENT_VERIFIED : replay;
            }
            await(() -> extractor.snapshot().queueDepth() == 0);
            assert extractor.snapshot().droppedTasks() == 0;
            assert Files.isDirectory(root.resolve("diagnostics")
                    .resolve("regression-candidates"));
        }
    }

    private static void priorRevisionSmoke(Path root) throws Exception {
        try (var old = new RegressionCandidateExtractor(root, "R078",
                     ignored -> { }, ignored -> { });
             var recorder = new OrbisIncidentRecorder(root, ignored -> { })) {
            recorder.setCandidateExtractor(old);
            var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE,
                    ignored -> { });
            sentinel.setIncidentRecorder(recorder);
            fail(sentinel, Boundary.PROVIDER_DISPATCH, facts(
                    "planValid", "true", "budgetedPromptHash", "old-a",
                    "dispatchedPromptHash", "old-b", "actualDispatchFitsBudget", "true"));
            await(() -> old.snapshot().totalCandidates() == 1);
        }
        try (var current = new RegressionCandidateExtractor(root, "R079",
                ignored -> { }, ignored -> { })) {
            List<IncidentReplayHarness.ReplayReport> reports = current.smoke().join();
            assert reports.size() == 1 : reports;
            assert reports.getFirst().unsafeSideEffectBlocked() : reports.getFirst();
        }
    }

    private static void healthyGuardPerformanceUnaffected() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        var healthy = observation(Boundary.PROVIDER_DISPATCH, facts(
                "planValid", "true", "budgetedPromptHash", "same",
                "dispatchedPromptHash", "same", "actualDispatchFitsBudget", "true",
                "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                "authoritativeEpistemicContract", "true"));
        long[] micros = new long[4_000];
        for (int i = 0; i < micros.length; i++) {
            long start = System.nanoTime();
            assert sentinel.guard(healthy).allowed();
            micros[i] = (System.nanoTime() - start) / 1_000;
        }
        java.util.Arrays.sort(micros);
        long p95 = micros[(int) (micros.length * .95)];
        assert p95 <= 3_000 : p95;
        System.out.println("S3 healthy guard p95=" + p95 + "us");
    }

    private static void fail(OrbisDegradationSentinel sentinel, Boundary boundary,
            Map<String, String> facts) {
        assert !sentinel.guard(observation(boundary, facts)).allowed();
    }
    private static SentinelObservation observation(Boundary boundary,
            Map<String, String> facts) {
        return new SentinelObservation(boundary, "TEST:" + boundary, UUID.randomUUID(),
                List.of("responseId=" + UUID.randomUUID()), facts);
    }
    private static Map<String, String> facts(String... pairs) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return Map.copyOf(result);
    }
    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.onSpinWait();
        assert condition.getAsBoolean();
    }
}
