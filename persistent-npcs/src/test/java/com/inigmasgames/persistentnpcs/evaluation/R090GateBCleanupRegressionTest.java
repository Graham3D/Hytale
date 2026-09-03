package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.epistemic.ConversationWorkspace;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicAnswerPlanner;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicFeatureMode;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicShadowAnalyzer;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicStatus;
import com.inigmasgames.persistentnpcs.epistemic.EvidencePacket;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceRef;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceSourceKind;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceSufficiency;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Frozen only after the focused real-Nemotron Gate-B cleanup probe passed 12/12. */
public final class R090GateBCleanupRegressionTest {
    private R090GateBCleanupRegressionTest() { }

    public static void main(String[] args) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        knownCorrectionsCannotCollapseToUncertainty();
        typedUncertaintyIsSemanticAndFailClosed();
        System.out.println("R090 strict Gate-B cleanup regressions passed.");
    }

    private static void knownCorrectionsCannotCollapseToUncertainty() {
        for (String utterance : List.of("My name is Graham, not Grant.",
                "My name is Daniel, not David.",
                "I live in Oakvale, not Riverbend.",
                "The key is silver, not gold.",
                "My name is Daniel, not David.")) {
            EpistemicContract contract = knownCorrection(utterance);
            var result = new EpistemicClaimFirewall().validate(
                    "I don't know enough to say that for certain.", contract, false);
            String expectedValue = contract.evidence().supporting().getFirst().objectValue();
            assert result.valid() : utterance + " -> " + result;
            assert result.repaired() : utterance + " -> " + result;
            assert result.dialogue().toLowerCase(java.util.Locale.ROOT)
                    .contains(expectedValue.toLowerCase(java.util.Locale.ROOT))
                    : utterance + " -> " + result;
            assert !result.dialogue().toLowerCase(java.util.Locale.ROOT)
                    .contains("don't know") : utterance + " -> " + result;
        }
    }

    private static EpistemicContract knownCorrection(String utterance) {
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(utterance,
                new ConversationWorkspace());
        assert base != null && base.queryPlan().queryKind().equals("CORRECTION") : utterance;
        var frame = base.dialogueFrame();
        String value = frame.objectKey()
                .replaceFirst("^(?:PERSON_NAME|CORRECTED_VALUE):", "")
                .replace('_', ' ');
        EvidenceRef evidence = new EvidenceRef(EvidenceRef.SCHEMA_VERSION,
                "test-player-correction", EvidenceSourceKind.PLAYER_TESTIMONY,
                EpistemicStatus.KNOWN, 1, true, "Player correction: " + value,
                frame.subjectKey(), frame.predicateKey(), value, Instant.now(),
                "CURRENT_TURN", "TEST_PLAYER", true, false, "", "CURRENT_TURN");
        EvidencePacket packet = new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                base.queryPlan().queryKind(), frame.subjectKey(), frame.predicateKey(),
                List.of(evidence), List.of(), List.of(), List.of(),
                EvidenceSufficiency.SUFFICIENT, List.of(), List.of(), 0, List.of(),
                List.of(EvidenceSourceKind.PLAYER_TESTIMONY), 32, 1, false);
        EpistemicAnswerPlanner.Result planned = EpistemicAnswerPlanner.compile(frame,
                base.queryPlan(), packet, Answerability.KNOWN, true);
        return new EpistemicContract(base.schemaVersion(), EpistemicFeatureMode.AUTHORITATIVE,
                frame, base.queryPlan(), packet, Answerability.KNOWN, planned.answerPlan(),
                planned.claimPolicy(), base.budget(), List.of("R090_GATE_B_CLEANUP"),
                base.planningMicros(), Instant.now());
    }

    private static void typedUncertaintyIsSemanticAndFailClosed() {
        for (String response : List.of("I'm not sure.", "I don't know.", "I can't tell.",
                "I haven't seen that.",
                "I'm not sure, but if it were red, I would keep my distance.")) {
            var verdicts = uncertaintyVerdicts(response);
            assert passes(verdicts, "EVAL-REQUIRED-PROPOSITION-UNCERTAINTY")
                    : response + " -> " + verdicts;
            assert passes(verdicts, "EVAL-UNKNOWN-NO-OBJECTIVE-ASSERTION")
                    : response + " -> " + verdicts;
        }
        var unsafe = uncertaintyVerdicts("I'm not sure. The dragon is red.");
        assert passes(unsafe, "EVAL-REQUIRED-PROPOSITION-UNCERTAINTY") : unsafe;
        assert !passes(unsafe, "EVAL-UNKNOWN-NO-OBJECTIVE-ASSERTION") : unsafe;
    }

    private static List<EvaluationContracts.StageVerdict> uncertaintyVerdicts(String response) {
        UUID turn = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        List<EvaluationContracts.StageObservation> observations = List.of(
                observation(EvaluationContracts.BoundaryId.INGRESS, 1, turn, responseId,
                        OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED, Map.of()),
                observation(EvaluationContracts.BoundaryId.TURN_PLAN, 2, turn, responseId,
                        OrbisEventType.TURN_PLAN_COMPILED, Map.of(
                                "epistemicQueryKind", "OBJECTIVE_PROPERTY",
                                "epistemicAnswerability", "UNKNOWN",
                                "epistemicEvidenceIds", "[]",
                                "epistemicEvidenceSources", "[]")),
                observation(EvaluationContracts.BoundaryId.CONTEXT_RENDER, 3, turn, responseId,
                        OrbisEventType.LLM_DISPATCHED, Map.of(
                                "contextSections", "[PROFILE]",
                                "epistemicEvidenceIds", "[]")),
                observation(EvaluationContracts.BoundaryId.CLEANUP, 4, turn, responseId,
                        OrbisEventType.TURN_COMPLETED, Map.of()));
        var expected = new EvaluationContracts.ExpectedTurnContract("RESPOND",
                "OBJECTIVE_PROPERTY", Set.of(), Set.of(), Answerability.UNKNOWN,
                List.of(new EvaluationContracts.ExpectedProposition("CURRENT_NPC",
                        "UNCERTAINTY", "", "", "CURRENT_TURN", Set.of())),
                Set.of(), "", EvaluationContracts.ExpectedStateDelta.none(),
                Set.of("PROFILE"), Set.of(), 5_000);
        return new ExpectedTurnOracle().evaluate(expected, observations, response,
                ExpectedTurnOracle.StateDeltaSnapshot.none(), 100);
    }

    private static boolean passes(List<EvaluationContracts.StageVerdict> values,
            String invariant) {
        return values.stream().filter(value -> value.invariantId().equals(invariant))
                .allMatch(value -> value.verdict()
                        == EvaluationContracts.EvaluationVerdict.PASS);
    }

    private static EvaluationContracts.StageObservation observation(
            EvaluationContracts.BoundaryId boundary, long sequence, UUID turn,
            UUID response, OrbisEventType type, Map<String, String> facts) {
        return new EvaluationContracts.StageObservation(boundary, sequence, Instant.now(),
                turn, response, type, facts);
    }
}
