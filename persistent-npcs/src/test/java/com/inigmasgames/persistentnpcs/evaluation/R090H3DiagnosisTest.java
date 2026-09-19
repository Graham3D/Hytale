package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** H3 gate: imported incident and synthetic layer drift diagnose distinct boundaries. */
public final class R090H3DiagnosisTest {
    private R090H3DiagnosisTest() { }

    public static void main(String[] args) {
        Path trace = Path.of("src", "test", "resources", "fixtures", "evaluation",
                "R090H3_Lycander_retrieval_context_authority_failure.jsonl");
        var imported = new TraceImportService().analyzeTurn(trace,
                "What did I hide, and where did I hide it?");
        assert imported.queryKind().equals("EPISODIC_RECALL") : imported;
        assert imported.answerability().equals("PARTIALLY_KNOWN") : imported;
        assert imported.rawModelOutput().contains("Four jars") : imported;
        assert imported.canonicalResponse().contains("don't remember") : imported;
        assert imported.failures().contains(EvaluationContracts.FailureClass.RETRIEVAL);
        assert imported.failures().contains(EvaluationContracts.FailureClass.CONTEXT_RENDER);
        assert imported.failures().contains(EvaluationContracts.FailureClass.CLAIM_AUTHORITY);

        UUID turn = UUID.randomUUID();
        var plan = observation(EvaluationContracts.BoundaryId.TURN_PLAN, 1, turn,
                OrbisEventType.TURN_PLAN_COMPILED, Map.of(
                        "epistemicQueryKind", "EPISODIC_RECALL",
                        "epistemicAnswerability", "KNOWN",
                        "epistemicEvidenceIds", "[MEMORY:hidden-sword]",
                        "epistemicEvidenceSources", "[EPISODIC_MEMORY]"));
        var dispatch = observation(EvaluationContracts.BoundaryId.CONTEXT_RENDER, 2, turn,
                OrbisEventType.LLM_DISPATCHED, Map.of(
                        "contextSections", "[PROFILE, PERSONALITY, RECENT_CONVERSATION]",
                        "epistemicEvidenceIds", "[MEMORY:hidden-sword]"));
        var ingress = observation(EvaluationContracts.BoundaryId.INGRESS, 0, turn,
                OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED, Map.of());
        var expected = new EvaluationContracts.ExpectedTurnContract("RECALL",
                "EPISODIC_RECALL", Set.of("MEMORY:hidden-sword"),
                Set.of("EPISODIC_MEMORY"),
                Answerability.KNOWN, List.of(), Set.of(), "",
                EvaluationContracts.ExpectedStateDelta.none(), Set.of("MEMORIES"), Set.of(),
                5_000);
        var terminal = observation(EvaluationContracts.BoundaryId.CLEANUP, 3, turn,
                OrbisEventType.TURN_COMPLETED, Map.of());
        List<EvaluationContracts.StageVerdict> verdicts = new ExpectedTurnOracle().evaluate(
                expected, List.of(ingress, plan, dispatch, terminal),
                "The sword was under a rock.",
                ExpectedTurnOracle.StateDeltaSnapshot.none(), 100);
        var diagnosis = new EarliestBoundaryDiagnoser().diagnose(verdicts,
                List.of(ingress, plan, dispatch, terminal));
        assert diagnosis != null;
        assert diagnosis.earliestFailedBoundary()
                == EvaluationContracts.BoundaryId.CONTEXT_RENDER : diagnosis;
        assert diagnosis.failureClass()
                == EvaluationContracts.FailureClass.CONTEXT_RENDER : diagnosis;
        System.out.println("R090 H3 observation/oracle/diagnosis gate passed.");
    }

    private static EvaluationContracts.StageObservation observation(
            EvaluationContracts.BoundaryId boundary, long sequence, UUID turn,
            OrbisEventType type, Map<String, String> facts) {
        return new EvaluationContracts.StageObservation(boundary, sequence, Instant.now(),
                turn, null, type, facts);
    }
}
