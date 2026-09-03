package com.inigmasgames.persistentnpcs.epistemic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** E2 semantic plan compiler. It describes permissible content, never final NPC prose. */
public final class EpistemicAnswerPlanner {
    private EpistemicAnswerPlanner() { }

    public static Result compile(DialogueFrame frame, EpistemicQueryPlan query,
            EvidencePacket packet, Answerability answerability, boolean authoritative) {
        List<EvidenceRef> usable = new ArrayList<>(packet.supporting());
        if (answerability == Answerability.UNKNOWN) usable.addAll(packet.contextual());
        List<String> propositions = usable.stream().map(EvidenceRef::compactProposition)
                .filter(value -> !value.isBlank()).distinct().limit(4).toList();
        List<String> unsupported = new ArrayList<>();
        if (answerability == Answerability.UNKNOWN && !frame.predicateKey().isBlank()) {
            unsupported.add(frame.predicateKey());
        }
        Set<String> forbidden = new LinkedHashSet<>(packet.restrictions());
        if (answerability == Answerability.SUBJECTIVE) {
            forbidden.add("UNSUPPORTED_OBJECTIVE_BIOGRAPHY");
            forbidden.add("SUBJECTIVE_MODE_CANNOT_SMUGGLE_OBJECTIVE_PREMISES");
        }
        if (answerability == Answerability.UNKNOWN
                || answerability == Answerability.NEEDS_CURRENT_PERCEPTION) {
            forbidden.add("ASSERT_UNKNOWN_REQUESTED_PROPERTY");
        }
        if (query.requestedAction() != null && !query.requestedAction().isBlank()) {
            forbidden.add("UNCOMMITTED_ACTION_PROMISE");
        }
        String answerKind = switch (answerability) {
            case KNOWN -> frame.act() == DialogueAct.CLARIFICATION_REQUEST
                    ? "BOUND_CLARIFICATION" : "DIRECT_FACT";
            case INFERRED -> "QUALIFIED_INFERENCE";
            case SUBJECTIVE -> packet.supporting().isEmpty()
                    ? "BOUNDED_SUBJECTIVE" : "AUTHORED_PREFERENCE";
            case CONFLICTED -> "CONFLICT_DISCLOSURE";
            case NEEDS_ACTION -> "ACTION_CAPABILITY_PENDING_AUTHORITY";
            case NEEDS_CLARIFICATION, AMBIGUOUS, UNRESOLVED -> "REQUEST_CLARIFICATION";
            case NEEDS_CURRENT_PERCEPTION -> "CURRENT_PERCEPTION_UNAVAILABLE";
            case UNKNOWN -> frame.predicateKey().startsWith("PROPERTY:")
                    ? "UNKNOWN_PROPERTY" : "UNKNOWN_FACT";
            default -> "BOUNDED_UNCERTAINTY";
        };
        String goal = switch (answerability) {
            case KNOWN -> "Answer the requested proposition directly from the listed evidence.";
            case INFERRED -> "State only the supported inference and preserve its uncertainty.";
            case SUBJECTIVE -> "Express a bounded personality-consistent subjective response without adding objective biography.";
            case CONFLICTED -> "Acknowledge the unresolved conflict without selecting an unsupported winner.";
            case NEEDS_ACTION -> "Describe capability only; leave validation, commitment, and execution to action authority.";
            case NEEDS_CLARIFICATION, AMBIGUOUS, UNRESOLVED -> "Ask for the unresolved referent or missing meaning.";
            case NEEDS_CURRENT_PERCEPTION -> "Admit that current authoritative perception is unavailable.";
            case UNKNOWN -> "Acknowledge only contextual facts and do not assert the requested unknown property.";
            default -> "Remain within the bounded evidence contract.";
        };
        int objectiveClaims = switch (answerability) {
            case KNOWN, INFERRED -> Math.min(2, propositions.size());
            case UNKNOWN -> Math.min(1, propositions.size());
            default -> 0;
        };
        List<String> uncertainty = switch (answerability) {
            case CONFLICTED -> List.of("COMPATIBLE_EVIDENCE_DISAGREES");
            case UNKNOWN -> List.of("NO_ADMISSIBLE_SUPPORT_FOR_REQUESTED_PROPOSITION");
            case NEEDS_CURRENT_PERCEPTION -> List.of("CURRENT_PERCEPTION_UNAVAILABLE");
            case NEEDS_CLARIFICATION, AMBIGUOUS, UNRESOLVED ->
                    List.of(frame.ambiguityReason().isBlank()
                            ? "INPUT_OR_REFERENT_UNRESOLVED" : frame.ambiguityReason());
            case INFERRED -> List.of("NON_AUTHORITATIVE_OR_DERIVED_EVIDENCE");
            default -> List.of();
        };
        AnswerPlan plan = new AnswerPlan(AnswerPlan.SCHEMA_VERSION, answerKind,
                propositions, usable, answerability.name(), 2, objectiveClaims,
                requiredSlots(frame, answerability), Set.copyOf(forbidden),
                authoritative ? "E3_AUTHORITATIVE" : "E2_SHADOW",
                goal, unsupported, query.requestedAction(), uncertainty);
        ClaimPolicy policy = policy(answerability, forbidden, !query.requestedAction().isBlank());
        return new Result(plan, policy);
    }

    private static Set<String> requiredSlots(DialogueFrame frame, Answerability answerability) {
        if (answerability == Answerability.KNOWN && frame.predicateKey().equals("NAME")) {
            return Set.of("SUPPORTED_NAME");
        }
        if (answerability == Answerability.KNOWN && !frame.predicateKey().isBlank()) {
            return Set.of("ANSWER_PREDICATE:" + frame.predicateKey());
        }
        if (answerability == Answerability.SUBJECTIVE
                && (frame.predicateKey().equals("DESIRE")
                        || frame.predicateKey().equals("EMOTION"))) {
            return Set.of("SUBJECTIVE_PREDICATE:" + frame.predicateKey());
        }
        if (answerability == Answerability.NEEDS_CLARIFICATION
                || answerability == Answerability.AMBIGUOUS
                || answerability == Answerability.UNRESOLVED) return Set.of("CLARIFYING_QUESTION");
        return Set.of();
    }

    private static ClaimPolicy policy(Answerability value, Set<String> restrictions,
            boolean actionBearing) {
        EnumSet<ClaimMode> modes = switch (value) {
            case KNOWN -> EnumSet.of(ClaimMode.OBJECTIVE_FACT, ClaimMode.QUESTION);
            case INFERRED -> EnumSet.of(ClaimMode.INFERENCE, ClaimMode.QUESTION);
            case SUBJECTIVE -> EnumSet.of(ClaimMode.SUBJECTIVE_OPINION, ClaimMode.EMOTION,
                    ClaimMode.DESIRE, ClaimMode.HYPOTHETICAL, ClaimMode.METAPHOR,
                    ClaimMode.QUESTION);
            case NEEDS_ACTION -> EnumSet.of(ClaimMode.INTENTION, ClaimMode.QUESTION);
            default -> EnumSet.of(ClaimMode.QUESTION);
        };
        return new ClaimPolicy(ClaimPolicy.SCHEMA_VERSION, modes, restrictions,
                value != Answerability.SUBJECTIVE, actionBearing, false);
    }

    public record Result(AnswerPlan answerPlan, ClaimPolicy claimPolicy) { }
}
