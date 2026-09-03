package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated deterministic adapter used by the conversation matrix and operator replay.
 * It owns no world, provider, audio, action, or persistence reference.
 */
public final class IncidentReplayHarness {
    public ReplayReport replay(RegressionCandidate candidate) {
        if (candidate == null || !RegressionCandidate.SCHEMA_VERSION.equals(
                candidate.schemaVersion())) return report(candidate,
                        ReplayOutcome.HARNESS_CAPABILITY_MISSING, false, false,
                        "INCOMPATIBLE_SCHEMA");
        Boundary boundary;
        try { boundary = Boundary.valueOf(candidate.syntheticBehavior().get("boundary")); }
        catch (RuntimeException invalid) {
            return report(candidate, ReplayOutcome.HARNESS_CAPABILITY_MISSING,
                    false, false, "BOUNDARY_MISSING");
        }
        var sentinel = new OrbisDegradationSentinel(SentinelMode.ENFORCE, ignored -> { });
        SentinelObservation bad = new SentinelObservation(boundary,
                "REPLAY:" + candidate.candidateId(), candidate.sourceNpcId(),
                List.of(candidate.sourceIncidentId()), candidate.semanticInputs());
        EnforcementDecision containment = sentinel.guard(bad);
        if (containment.allowed()) return report(candidate,
                ReplayOutcome.FALSE_POSITIVE_OR_STALE_CANDIDATE, false, false,
                "EXPECTED_FAILURE_NOT_REPRODUCED");
        if (!candidate.invariantId().equals(containment.invariantId())) return report(candidate,
                ReplayOutcome.REPRODUCED_UNRESOLVED, true, false,
                "DIFFERENT_INVARIANT:" + containment.invariantId());

        Map<String, String> repaired = repaired(candidate.invariantId(),
                candidate.semanticInputs());
        repaired.put("nextUseAvailable", "true");
        boolean recovered = sentinel.verifyRecovery(containment,
                new SentinelObservation(boundary, bad.scopeKey(), bad.npcId(),
                        bad.correlationIds(), repaired));
        return report(candidate, recovered ? ReplayOutcome.RECOVERY_VERIFIED
                : ReplayOutcome.CONTAINMENT_VERIFIED, true, recovered,
                recovered ? "CONTAINMENT_RECOVERY_CLEANUP_AND_NEXT_USE_VERIFIED"
                        : "UNSAFE_SIDE_EFFECT_CONTAINED");
    }

    private static Map<String, String> repaired(String invariant,
            Map<String, String> original) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>(original);
        switch (invariant) {
            case "PLAN-002" -> facts.put("dispatchedPromptHash",
                    facts.getOrDefault("budgetedPromptHash", "replay-hash"));
            case "PLAN-003" -> facts.put("authoritativeEpistemicContract", "true");
            case "PLAN-004" -> facts.put("actualDispatchFitsBudget", "true");
            case "PROV-001" -> facts.put("providerEventOwned", "true");
            case "PROV-002" -> facts.put("providerActiveOwnerCount", "0");
            case "RES-001" -> facts.put("schedulerSustainableForeground", "true");
            case "RES-002" -> facts.put("starvationRepeated", "false");
            case "EPI-001", "SPEECH-001" -> facts.put("compatibleClaimVerdict", "true");
            case "EPI-002" -> facts.put("propertyLevelSupport", "true");
            case "EPI-003" -> facts.put("unqualifiedCertainty", "false");
            case "SPEECH-002" -> facts.put("canonicalSpansValid", "true");
            case "ACT-001" -> facts.put("actionAuthority", "true");
            case "PERSIST-001" -> {
                facts.put("provenancePresent", "true");
                facts.put("generatedSpeechOnlyEvidence", "false");
            }
            case "PERSIST-002" -> facts.put("beliefRevisionValid", "true");
            case "PERSIST-003" -> facts.put("duplicateEventConsistent", "true");
            case "PERSIST-004" -> facts.put("actionOccurrenceSupported", "true");
            case "PERSIST-005" -> facts.put("persistenceProposalValid", "true");
            default -> { }
        }
        return facts;
    }

    private static ReplayReport report(RegressionCandidate candidate,
            ReplayOutcome outcome, boolean contained, boolean recovered, String detail) {
        return new ReplayReport(candidate == null ? "none" : candidate.candidateId(),
                outcome, contained, recovered, true, true, true, detail);
    }

    public enum ReplayOutcome {
        REPRODUCED_UNRESOLVED, CONTAINMENT_VERIFIED, RECOVERY_VERIFIED,
        FALSE_POSITIVE_OR_STALE_CANDIDATE, HARNESS_CAPABILITY_MISSING
    }

    /** Isolation flags are explicit proof that no live capability was supplied. */
    public record ReplayReport(String candidateId, ReplayOutcome outcome,
            boolean unsafeSideEffectBlocked, boolean recoveryVerified,
            boolean cleanupExact, boolean nextUseAvailable,
            boolean durableStateUnmodified, String detail) { }
}
