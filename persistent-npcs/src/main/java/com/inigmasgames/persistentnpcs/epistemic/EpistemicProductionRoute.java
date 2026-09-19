package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningDecision;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * E3 adapter into the existing production route types. This owns no parallel route enum:
 * supported authoritative query kinds select the existing reasoning/context contracts.
 */
public final class EpistemicProductionRoute {
    private EpistemicProductionRoute() { }

    public static boolean authoritative(EpistemicContract contract) {
        return contract != null && contract.mode() == EpistemicFeatureMode.AUTHORITATIVE
                && contract.answerPlan() != null
                && "E3_AUTHORITATIVE".equals(contract.answerPlan().status());
    }

    public static AdaptiveReasoningDecision reasoning(EpistemicContract contract,
            AdaptiveReasoningDecision legacy) {
        if (!authoritative(contract)) return legacy;
        EpistemicQueryKind kind = kind(contract.queryPlan().queryKind());
        if (contract.answerability() == Answerability.NEEDS_CLARIFICATION
                || contract.answerability() == Answerability.AMBIGUOUS
                || contract.answerability() == Answerability.UNRESOLVED) {
            return new AdaptiveReasoningDecision(AdaptiveReasoningPolicy.GROUNDED_DIALOGUE,
                    List.of("E3_AUTHORITATIVE_ROUTE", "EPISTEMIC_CLARIFICATION_REQUIRED"));
        }
        AdaptiveReasoningPolicy policy = switch (kind) {
            case SUBJECTIVE_PREFERENCE, GENERAL_SOCIAL -> AdaptiveReasoningPolicy.FAST_DIALOGUE;
            case ACTION_REQUEST -> AdaptiveReasoningPolicy.DIRECT_ACTION;
            case IDENTITY_RECALL, EPISODIC_RECALL, CURRENT_PERCEPTION, NPC_SELF_STATE,
                    RELATIONSHIP_FACT, CLARIFICATION, CORRECTION, OBJECTIVE_PROPERTY,
                    UNRESOLVED -> AdaptiveReasoningPolicy.GROUNDED_DIALOGUE;
        };
        return new AdaptiveReasoningDecision(policy, List.of(
                "E3_AUTHORITATIVE_ROUTE", "EPISTEMIC_QUERY_" + kind.name()));
    }

    public static CognitiveContextPlan context(EpistemicContract contract,
            CognitiveContextPlan legacy) {
        // Context collection begins before E2 enrichment can compile the final AnswerPlan.
        // The deterministic E1 semantic route is already authoritative at that boundary; do
        // not wait for E3 status and let a legacy SIMPLE_SOCIAL classification shape cognition.
        if (contract == null || contract.mode() != EpistemicFeatureMode.AUTHORITATIVE) {
            return legacy;
        }
        EpistemicQueryKind kind = kind(contract.queryPlan().queryKind());
        boolean social = kind == EpistemicQueryKind.SUBJECTIVE_PREFERENCE
                || kind == EpistemicQueryKind.GENERAL_SOCIAL;
        LinkedHashSet<String> included = new LinkedHashSet<>(Set.of(
                "PROFILE", "PERSONALITY", "RECENT_CONVERSATION"));
        if (kind == EpistemicQueryKind.ACTION_REQUEST) included.add("ACTIONS");
        // Perception capture is an external boundary adapter invoked only when this section is
        // requested. Omitting it here made CURRENT_PERCEPTION authoritative in name while
        // silently feeding E2 an unavailable snapshot in both production and evaluation.
        if (kind == EpistemicQueryKind.CURRENT_PERCEPTION
                || kind == EpistemicQueryKind.OBJECTIVE_PROPERTY) {
            included.add("SEMANTIC_WORLD");
        }
        Set<String> all = CognitiveContextPlan.full("EPISTEMIC").includedSections();
        LinkedHashSet<String> excluded = new LinkedHashSet<>(all);
        excluded.removeAll(included);
        return new CognitiveContextPlan(social ? CognitiveDepth.SIMPLE_SOCIAL
                        : CognitiveDepth.DIRECT_FACT,
                "EPISTEMIC_" + kind.name(), included, excluded,
                legacy == null ? List.of() : legacy.authoritativeConstraints());
    }

    private static EpistemicQueryKind kind(String value) {
        try {
            return EpistemicQueryKind.valueOf(value == null ? "UNRESOLVED" : value);
        } catch (IllegalArgumentException ignored) {
            return EpistemicQueryKind.UNRESOLVED;
        }
    }
}
