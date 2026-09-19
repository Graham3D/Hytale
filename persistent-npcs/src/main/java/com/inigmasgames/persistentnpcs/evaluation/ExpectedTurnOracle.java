package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.AtomicClaimExtractor;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic semantic/contract oracle. It never uses provider prose as truth. */
public final class ExpectedTurnOracle {
    public List<EvaluationContracts.StageVerdict> evaluate(
            EvaluationContracts.ExpectedTurnContract expected,
            List<EvaluationContracts.StageObservation> observations,
            String canonicalText, StateDeltaSnapshot stateDelta, long elapsedMillis) {
        ArrayList<EvaluationContracts.StageVerdict> verdicts = new ArrayList<>();
        EvaluationContracts.StageObservation ingress = first(observations,
                OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED);
        verdicts.add(verdict(EvaluationContracts.BoundaryId.INGRESS,
                "EVAL-INGRESS-AUTHORITATIVE", count(observations,
                        OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED) == 1,
                "one authoritative accepted transcript", ingress == null
                        ? "missing" : count(observations,
                                OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED)
                                + " " + ingress.facts()));

        EvaluationContracts.StageObservation plan = first(observations,
                OrbisEventType.TURN_PLAN_COMPILED);
        verdicts.add(verdict(EvaluationContracts.BoundaryId.TURN_PLAN,
                "EVAL-AUTHORITATIVE-TURN-PLAN", plan != null,
                "TURN_PLAN_COMPILED", plan == null ? "missing" : plan.facts().toString()));
        if (expected.queryKind() != null && !expected.queryKind().isBlank()) {
            String actual = fact(plan, "epistemicQueryKind", "UNOBSERVED");
            verdicts.add(verdict(EvaluationContracts.BoundaryId.QUERY_PLAN,
                    "EVAL-QUERY-KIND", expected.queryKind().equalsIgnoreCase(actual),
                    expected.queryKind(), actual));
        }
        if (expected.expectedAnswerability() != null) {
            String actual = fact(plan, "epistemicAnswerability", "UNOBSERVED");
            verdicts.add(verdict(EvaluationContracts.BoundaryId.ANSWERABILITY,
                    "EVAL-ANSWERABILITY", expected.expectedAnswerability().name()
                            .equalsIgnoreCase(actual), expected.expectedAnswerability().name(),
                    actual));
        }

        EvaluationContracts.StageObservation dispatch = first(observations,
                OrbisEventType.LLM_DISPATCHED);
        verdicts.add(verdict(EvaluationContracts.BoundaryId.PROVIDER,
                "EVAL-PROVIDER-DISPATCHED", dispatch != null,
                "LLM_DISPATCHED", dispatch == null ? "missing" : dispatch.facts().toString()));
        Set<String> sections = parseSet(fact(dispatch, "contextSections", ""));
        for (String required : expected.requiredContextSections()) {
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CONTEXT_RENDER,
                    "EVAL-CONTEXT-REQUIRED-" + required, sections.contains(required),
                    "contains " + required, sections.toString()));
        }
        for (String forbidden : expected.forbiddenContextSections()) {
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CONTEXT_RENDER,
                    "EVAL-CONTEXT-FORBIDDEN-" + forbidden, !sections.contains(forbidden),
                    "omits " + forbidden, sections.toString()));
        }
        if (!expected.requiredEvidence().isEmpty()) {
            Set<String> evidence = parseSet(fact(plan, "epistemicEvidenceIds", ""));
            boolean selected = evidence.containsAll(expected.requiredEvidence());
            verdicts.add(verdict(EvaluationContracts.BoundaryId.RETRIEVAL,
                    "EVAL-REQUIRED-EVIDENCE", selected,
                    expected.requiredEvidence().toString(), evidence.toString()));
        }
        Set<String> sources = parseSet(fact(plan, "epistemicEvidenceSources", ""));
        if (!expected.allowedSources().isEmpty()) {
            boolean allowed = sources.stream().allMatch(expected.allowedSources()::contains);
            verdicts.add(verdict(EvaluationContracts.BoundaryId.RETRIEVAL,
                    "EVAL-EVIDENCE-SOURCE-ALLOWLIST", allowed,
                    "subset of " + expected.allowedSources(), sources.toString()));
        }
        if (dispatch != null) {
            Set<String> dispatchedEvidence = parseSet(fact(dispatch,
                    "epistemicEvidenceIds", ""));
            Set<String> plannedEvidence = parseSet(fact(plan,
                    "epistemicEvidenceIds", ""));
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CONTEXT_RENDER,
                    "EVAL-PLAN-DISPATCH-EVIDENCE-CONSISTENCY",
                    dispatchedEvidence.equals(plannedEvidence), plannedEvidence.toString(),
                    dispatchedEvidence.toString()));
        }
        if (expected.expectedAction() != null && !expected.expectedAction().isBlank()) {
            String action = fact(plan, "epistemicRequestedAction", "");
            verdicts.add(verdict(EvaluationContracts.BoundaryId.ANSWER_PLAN,
                    "EVAL-EXPECTED-ACTION", expected.expectedAction().equalsIgnoreCase(action),
                    expected.expectedAction(), action));
        }

        String normalized = normalize(canonicalText);
        verdicts.add(verdict(EvaluationContracts.BoundaryId.CANONICAL_RESPONSE,
                "EVAL-NONEMPTY-CANONICAL-RESPONSE", !normalized.isBlank(),
                "non-empty canonical response", canonicalText));
        var claims = new AtomicClaimExtractor().extract(canonicalText);
        for (EvaluationContracts.ExpectedProposition proposition
                : expected.requiredPropositions()) {
            boolean present = semanticContains(normalized, proposition, claims);
            boolean mode = proposition.claimMode().isBlank() || claims.stream().anyMatch(value ->
                    value.mode().name().equalsIgnoreCase(proposition.claimMode()));
            boolean evidenceSource = proposition.evidenceSources().isEmpty()
                    || sources.stream().anyMatch(proposition.evidenceSources()::contains);
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CANONICAL_RESPONSE,
                    "EVAL-REQUIRED-PROPOSITION-" + proposition.predicate(),
                    present && mode && evidenceSource,
                    proposition.subject() + " " + proposition.predicate() + " "
                            + proposition.value() + " mode=" + proposition.claimMode()
                            + " sources=" + proposition.evidenceSources(), canonicalText
                            + " claims=" + claims + " sources=" + sources));
        }
        if (expected.expectedAnswerability()
                == com.inigmasgames.persistentnpcs.epistemic.Answerability.UNKNOWN) {
            List<String> unsupportedAssertions = claims.stream()
                    .filter(value -> value.mode()
                            == com.inigmasgames.persistentnpcs.epistemic.ClaimMode.OBJECTIVE_FACT)
                    .map(value -> value.text()).toList();
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CLAIM_FIREWALL,
                    "EVAL-UNKNOWN-NO-OBJECTIVE-ASSERTION", unsupportedAssertions.isEmpty(),
                    "typed uncertainty/hypothetical without objective assertion",
                    unsupportedAssertions.toString()));
        }
        for (String forbidden : expected.forbiddenClaims()) {
            boolean absent = !normalized.contains(normalize(forbidden));
            verdicts.add(verdict(EvaluationContracts.BoundaryId.CLAIM_FIREWALL,
                    "EVAL-FORBIDDEN-CLAIM-" + forbidden, absent,
                    "absent", canonicalText));
        }
        verdicts.add(verdict(EvaluationContracts.BoundaryId.STATE_DELTA,
                "EVAL-NO-FORBIDDEN-WRITE", stateDelta == null
                        || stateDelta.forbiddenWrites().isEmpty(), "none",
                stateDelta == null ? "not captured" : stateDelta.forbiddenWrites().toString()));
        evaluateStateDelta(verdicts, expected.stateDelta(), stateDelta);
        boolean completed = count(observations, OrbisEventType.TURN_COMPLETED) == 1;
        verdicts.add(verdict(EvaluationContracts.BoundaryId.CLEANUP,
                "EVAL-CLEAN-TERMINAL", completed, "one TURN_COMPLETED",
                Integer.toString(count(observations, OrbisEventType.TURN_COMPLETED))));
        verdicts.add(verdict(EvaluationContracts.BoundaryId.CLEANUP,
                "EVAL-LATENCY-BOUND", elapsedMillis <= expected.maximumLatencyMillis(),
                "<=" + expected.maximumLatencyMillis() + "ms", elapsedMillis + "ms"));
        return List.copyOf(verdicts);
    }

    private static boolean semanticContains(String text,
            EvaluationContracts.ExpectedProposition proposition,
            List<com.inigmasgames.persistentnpcs.epistemic.AtomicClaim> claims) {
        if (proposition.predicate().equalsIgnoreCase("UNCERTAINTY")) {
            return claims.stream().anyMatch(value -> value.predicateKey()
                    .equals("EPISTEMIC_UNCERTAINTY")
                    && (proposition.subject().isBlank()
                            || proposition.subject().equalsIgnoreCase(value.subjectKey())));
        }
        if (proposition.value().isBlank() && !proposition.predicate().isBlank()) {
            return claims.stream().anyMatch(value -> value.predicateKey()
                    .equalsIgnoreCase(proposition.predicate())
                    && (proposition.subject().isBlank()
                            || proposition.subject().equalsIgnoreCase(value.subjectKey())));
        }
        if (proposition.value().contains("|")) {
            return java.util.Arrays.stream(proposition.value().split("\\|"))
                    .map(ExpectedTurnOracle::normalize).filter(value -> !value.isBlank())
                    .anyMatch(text::contains);
        }
        List<String> valueTokens = List.of(normalize(proposition.value()).split(" ")).stream()
                .filter(token -> token.length() > 2).toList();
        if (valueTokens.isEmpty()) return !normalize(proposition.subject()).isBlank()
                && text.contains(normalize(proposition.subject()));
        long matches = valueTokens.stream().filter(text::contains).count();
        return matches >= Math.max(1, (int) Math.ceil(valueTokens.size() * 0.60));
    }

    private static void evaluateStateDelta(List<EvaluationContracts.StageVerdict> verdicts,
            EvaluationContracts.ExpectedStateDelta expected, StateDeltaSnapshot actual) {
        StateDeltaSnapshot captured = actual == null ? StateDeltaSnapshot.none() : actual;
        checkDelta(verdicts, "MEMORY", expected.memoryContains(), captured.memoriesAdded());
        checkDelta(verdicts, "BELIEF", expected.beliefContains(), captured.beliefsAdded());
        checkDelta(verdicts, "RELATIONSHIP", expected.relationshipContains(),
                captured.relationshipsChanged());
        Set<String> all = new LinkedHashSet<>();
        all.addAll(captured.memoriesAdded()); all.addAll(captured.beliefsAdded());
        all.addAll(captured.relationshipsChanged());
        for (String forbidden : expected.forbiddenWrites()) {
            boolean absent = all.stream().noneMatch(value -> normalize(value)
                    .contains(normalize(forbidden)));
            verdicts.add(verdict(EvaluationContracts.BoundaryId.STATE_DELTA,
                    "EVAL-FORBIDDEN-STATE-WRITE-" + forbidden, absent,
                    "absent", all.toString()));
        }
    }

    private static void checkDelta(List<EvaluationContracts.StageVerdict> verdicts,
            String kind, Set<String> expected, Set<String> actual) {
        for (String needle : expected) {
            boolean present = actual.stream().anyMatch(value -> normalize(value)
                    .contains(normalize(needle)));
            verdicts.add(verdict(EvaluationContracts.BoundaryId.STATE_DELTA,
                    "EVAL-EXPECTED-" + kind + "-DELTA-" + needle, present,
                    needle, actual.toString()));
        }
    }

    private static EvaluationContracts.StageVerdict verdict(
            EvaluationContracts.BoundaryId boundary, String invariant, boolean passes,
            String expected, String actual) {
        return new EvaluationContracts.StageVerdict(boundary, passes
                ? EvaluationContracts.EvaluationVerdict.PASS
                : EvaluationContracts.EvaluationVerdict.FAIL,
                invariant, expected, actual);
    }

    private static EvaluationContracts.StageObservation first(
            List<EvaluationContracts.StageObservation> observations, OrbisEventType type) {
        return observations.stream().filter(value -> value.eventType() == type)
                .findFirst().orElse(null);
    }

    private static int count(List<EvaluationContracts.StageObservation> observations,
            OrbisEventType type) {
        return (int) observations.stream().filter(value -> value.eventType() == type).count();
    }

    private static String fact(EvaluationContracts.StageObservation observation,
            String key, String fallback) {
        return observation == null ? fallback : observation.facts().getOrDefault(key, fallback);
    }

    private static Set<String> parseSet(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String normalized = value == null ? "" : value.replace('[', ' ').replace(']', ' ');
        for (String part : normalized.split(",")) {
            String item = part.strip();
            if (!item.isBlank()) result.add(item);
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip();
    }

    public record StateDeltaSnapshot(Set<String> memoriesAdded, Set<String> beliefsAdded,
            Set<String> relationshipsChanged, Set<String> forbiddenWrites) {
        public StateDeltaSnapshot {
            memoriesAdded = Set.copyOf(memoriesAdded == null ? Set.of() : memoriesAdded);
            beliefsAdded = Set.copyOf(beliefsAdded == null ? Set.of() : beliefsAdded);
            relationshipsChanged = Set.copyOf(relationshipsChanged == null
                    ? Set.of() : relationshipsChanged);
            forbiddenWrites = Set.copyOf(forbiddenWrites == null ? Set.of() : forbiddenWrites);
        }
        public static StateDeltaSnapshot none() {
            return new StateDeltaSnapshot(Set.of(), Set.of(), Set.of(), Set.of());
        }
    }
}
