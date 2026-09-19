package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Targeted S1 registry, historical incident, false-positive and overhead gate. */
public final class R077SentinelS1Test {
    private R077SentinelS1Test() { }

    public static void main(String[] args) {
        registryIsVersionedAndNamesExistingAuthorities();
        modesCannotEnforceInS1();
        historicalFailuresAreDetected();
        healthyControlsDoNotViolate();
        signaturesNormalizeRawVariations();
        healthUsesSmallestScope();
        commonTurnOverheadIsBounded();
        System.out.println("R077 Sentinel S1 tests passed.");
    }

    private static void registryIsVersionedAndNamesExistingAuthorities() {
        var registry = new InvariantRegistry();
        assert registry.version().equals("S1.1");
        List<String> required = List.of("TURN-001", "TURN-002", "TURN-003",
                "PLAN-001", "PLAN-002", "PLAN-003", "PLAN-004", "PROV-001",
                "PROV-002", "PROV-003", "RES-001", "RES-002", "RES-003",
                "EPI-001", "EPI-002", "EPI-003", "EPI-004", "SPEECH-001",
                "SPEECH-002", "SPEECH-003", "ACT-001", "PERSIST-001");
        assert registry.all().stream().map(InvariantDefinition::id).toList()
                .containsAll(required);
        assert registry.all().stream().allMatch(value -> value.version() == 1
                && !value.authoritativeOwner().isBlank()
                && value.evaluationDeadline().toMillis() == 1);
    }

    private static void modesCannotEnforceInS1() {
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        assert sentinel.configuredMode() == SentinelMode.ENFORCE;
        assert sentinel.effectiveMode() == SentinelMode.ENFORCE;
        sentinel.configure(SentinelMode.OFF);
        assert sentinel.observe(observation(Boundary.PROVIDER_DISPATCH,
                "TURN:off", providerHealthy())).isEmpty();
    }

    private static void historicalFailuresAreDetected() {
        var events = new ArrayList<SentinelEvent>();
        var sentinel = new OrbisDegradationSentinel(SentinelMode.OBSERVE, events::add);

        assert failed(sentinel.observe(observation(Boundary.PROVIDER_DISPATCH,
                "ROUTE:OBJECTIVE_PROPERTY", facts(
                        "planValid", "true", "budgetedPromptHash", "a",
                        "dispatchedPromptHash", "a", "actualDispatchFitsBudget", "true",
                        "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                        "authoritativeEpistemicContract", "false", "route",
                        "OBJECTIVE_PROPERTY", "outputContract", "DIALOGUE_TEXT"))),
                "PLAN-003", "EPISTEMIC_ROUTE_NOT_AUTHORITATIVE");

        assert failed(sentinel.observe(observation(Boundary.PROVIDER_DISPATCH,
                "TURN:budget", facts(
                        "planValid", "true", "budgetedPromptHash", "context-a",
                        "dispatchedPromptHash", "context-a-plus-b",
                        "actualDispatchFitsBudget", "false", "authoritativeMode", "false",
                        "supportedEpistemicRoute", "false",
                        "authoritativeEpistemicContract", "false"))),
                "PLAN-002", "POST_RENDER_PROMPT_DRIFT");

        assert failed(sentinel.observe(observation(Boundary.SPEECH_LEDGER_APPEND,
                "TURN:speech", facts("objectiveClaim", "true",
                        "compatibleClaimVerdict", "false", "canonicalSpansValid", "true"))),
                "SPEECH-001", "LEDGER_APPEND_MISSING_CLAIM_EVIDENCE");

        assert failed(sentinel.observe(observation(Boundary.PROVIDER_STREAM_EVENT,
                "BRANCH:7", facts("providerEventOwned", "false",
                        "providerDeltaMonotonic", "true", "provider", "NEMOTRON"))),
                "PROV-001", "STALE_PROVIDER_EVENT_OWNERSHIP");

        assert failed(sentinel.observe(observation(Boundary.READINESS_SAMPLE,
                "RESOURCE_PROFILE:NEMOTRON_CURRENT", facts("schedulerReady", "true",
                        "schedulerSustainableForeground", "false",
                        "starvationRepeated", "false", "residencyStable", "true",
                        "provider", "NEMOTRON"))), "RES-001",
                "FALSE_READY_RESOURCE_ENVELOPE");

        assert failed(sentinel.observe(observation(Boundary.TERMINAL_CLEANUP,
                "TURN:cleanup", facts("terminalTransitionCount", "2",
                        "cleanupAcquireCount", "1", "cleanupReleaseCount", "2",
                        "historyWritten", "false", "playbackConfirmed", "false"))),
                "TURN-002", "DUPLICATE_OR_MISSING_TERMINAL");
        assert events.stream().anyMatch(value -> value.event()
                .equals("SENTINEL_DEGRADATION_SIGNAL"));
    }

    private static void healthyControlsDoNotViolate() {
        var sentinel = new OrbisDegradationSentinel();
        assert noFailures(sentinel.observe(observation(Boundary.PROVIDER_DISPATCH,
                "ROUTE:CURRENT_PERCEPTION", providerHealthy())));
        assert noFailures(sentinel.observe(observation(Boundary.PROVIDER_STREAM_EVENT,
                "BRANCH:healthy", facts("providerEventOwned", "true",
                        "providerDeltaMonotonic", "true"))));
        assert noFailures(sentinel.observe(observation(Boundary.READINESS_SAMPLE,
                "RESOURCE_PROFILE:NEMOTRON_CURRENT", facts("schedulerReady", "true",
                        "schedulerSustainableForeground", "true",
                        "starvationRepeated", "false", "residencyStable", "true"))));
        assert noFailures(sentinel.observe(observation(Boundary.CLAIM_VALIDATION,
                "ROUTE:OBJECTIVE_PROPERTY", facts("objectiveClaim", "true",
                        "compatibleClaimVerdict", "true", "propertyAssertion", "true",
                        "propertyLevelSupport", "true", "answerabilityRestricted", "false",
                        "unqualifiedCertainty", "false"))));
        assert noFailures(sentinel.observe(observation(Boundary.SPEECH_LEDGER_APPEND,
                "TURN:healthy", facts("objectiveClaim", "true",
                        "compatibleClaimVerdict", "true", "canonicalSpansValid", "true"))));
        assert noFailures(sentinel.observe(observation(Boundary.TERMINAL_CLEANUP,
                "TURN:healthy", facts("terminalTransitionCount", "1",
                        "cleanupAcquireCount", "1", "cleanupReleaseCount", "1",
                        "historyWritten", "true", "playbackConfirmed", "true"))));
    }

    private static void signaturesNormalizeRawVariations() {
        var events = new ArrayList<SentinelEvent>();
        var sentinel = new OrbisDegradationSentinel(SentinelMode.OBSERVE, events::add);
        for (String free : List.of("854 MiB", "855 MiB", "856 MiB")) {
            sentinel.observe(observation(Boundary.READINESS_SAMPLE,
                    "RESOURCE_PROFILE:NEMOTRON_CURRENT", facts("schedulerReady", "true",
                            "schedulerSustainableForeground", "false",
                            "starvationRepeated", "true", "starvationClass",
                            "FOREGROUND_HEADROOM", "freeVram", free,
                            "residencyStable", "true", "provider", "NEMOTRON")));
        }
        List<String> signatures = events.stream()
                .filter(value -> value.invariantId().equals("RES-001"))
                .map(SentinelEvent::failureSignature).distinct().toList();
        assert signatures.size() == 1 : signatures;
        assert sentinel.snapshot().signatureOccurrences().get(signatures.getFirst()) == 3;
    }

    private static void healthUsesSmallestScope() {
        var sentinel = new OrbisDegradationSentinel();
        sentinel.observe(observation(Boundary.PROVIDER_STREAM_EVENT,
                "PROVIDER:NEMOTRON", facts("providerEventOwned", "false",
                        "providerDeltaMonotonic", "true")));
        var health = sentinel.snapshot().scopedHealth();
        assert health.get("PROVIDER:NEMOTRON") == Health.DEGRADED;
        assert !health.containsKey("GLOBAL:ORBIS");
    }

    private static void commonTurnOverheadIsBounded() {
        var sentinel = new OrbisDegradationSentinel();
        var observation = observation(Boundary.PROVIDER_DISPATCH,
                "ROUTE:CURRENT_PERCEPTION", providerHealthy());
        for (int index = 0; index < 2_000; index++) sentinel.observe(observation);
        long[] micros = new long[10_000];
        for (int index = 0; index < micros.length; index++) {
            long started = System.nanoTime();
            sentinel.observe(observation);
            micros[index] = Math.max(0, (System.nanoTime() - started) / 1_000L);
        }
        Arrays.sort(micros);
        long p95 = micros[(int) (micros.length * .95)];
        assert p95 <= 3_000 : "common-turn sentinel p95=" + p95 + "us";
        assert sentinel.snapshot().averageEvaluationMicros() <= 1_000d
                : sentinel.snapshot().averageEvaluationMicros();
        System.out.println("Sentinel overhead: dispatch p95=" + p95
                + "us evaluatorAverage="
                + "%.2f".formatted(sentinel.snapshot().averageEvaluationMicros()) + "us");
    }

    private static Map<String, String> providerHealthy() {
        return facts("planValid", "true", "budgetedPromptHash", "same",
                "dispatchedPromptHash", "same", "actualDispatchFitsBudget", "true",
                "authoritativeMode", "true", "supportedEpistemicRoute", "true",
                "authoritativeEpistemicContract", "true", "route",
                "CURRENT_PERCEPTION", "outputContract", "DIALOGUE_TEXT");
    }

    private static SentinelObservation observation(Boundary boundary, String scope,
            Map<String, String> facts) {
        return new SentinelObservation(boundary, scope, UUID.randomUUID(),
                List.of("fixture=S1"), facts);
    }
    private static Map<String, String> facts(String... pairs) {
        var values = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return Map.copyOf(values);
    }
    private static boolean failed(List<InvariantVerdict> values, String id, String reason) {
        return values.stream().anyMatch(value -> value.invariantId().equals(id)
                && value.status() == VerdictStatus.FAIL
                && value.boundedReasonCode().equals(reason));
    }
    private static boolean noFailures(List<InvariantVerdict> values) {
        return values.stream().noneMatch(value -> value.status() == VerdictStatus.FAIL
                || value.status() == VerdictStatus.EVALUATOR_ERROR);
    }
}
