package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.cognition.IntentCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministic routing only; no provider call is made to choose cognition depth. */
public final class AdaptiveReasoningRouter {
    private AdaptiveReasoningRouter() { }

    public static AdaptiveReasoningDecision route(CognitiveContextPlan context,
            DialogueMode mode, CognitionTurn cognition, int offeredActionCount,
            String playerMessage) {
        ArrayList<String> reasons = new ArrayList<>();
        String text = normalize(playerMessage);
        if (mode == DialogueMode.NPC_INITIATED_CURIOSITY) {
            reasons.add("NPC_INITIATED_COGNITION");
            reasons.add("BACKGROUND_PRIORITY_YIELDS_TO_PLAYER");
            return decision(AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION, reasons);
        }

        boolean conflict = hasCompetingIntentConflict(cognition);
        boolean multiStep = isMultiStep(text);
        boolean consequential = isConsequential(text);
        boolean ambiguous = isAmbiguous(text);
        boolean oneAuthoritativeAction = cognition != null && cognition.decision() != null
                && cognition.decision().actionRequests().size() == 1
                && offeredActionCount == 1;
        // A generic information-response candidate is always present for questions such as
        // "Can you follow me?". It must not turn a validated one-step action into a fake
        // competing-intent conflict.
        if (oneAuthoritativeAction && !multiStep && !consequential && !ambiguous) {
            reasons.add("ONE_VALIDATED_SINGLE_STEP_ACTION");
            reasons.add("GENERIC_QUESTION_CANDIDATE_IGNORED");
            return decision(AdaptiveReasoningPolicy.DIRECT_ACTION, reasons);
        }
        if (conflict) reasons.add("COMPETING_INTENTS_CLOSE_UTILITY");
        if (multiStep) reasons.add("MULTI_STEP_OR_PLANNING_REQUEST");
        if (consequential) reasons.add("CONSEQUENTIAL_RELATIONSHIP_OR_GOAL_CHOICE");
        if (ambiguous) reasons.add("AMBIGUOUS_DECISION");

        CognitiveDepth depth = context == null
                ? CognitiveDepth.COMPLEX_INTENT : context.depth();
        if (depth == CognitiveDepth.COMPLEX_INTENT) {
            if (offeredActionCount == 1 && !conflict && !multiStep
                    && !consequential && !ambiguous) {
                reasons.add("ONE_VALIDATED_SINGLE_STEP_ACTION");
                return decision(AdaptiveReasoningPolicy.DIRECT_ACTION, reasons);
            }
            reasons.add("COMPLEX_CONTEXT_REQUIRES_SYNTHESIS");
            return decision(AdaptiveReasoningPolicy.DELIBERATIVE, reasons);
        }
        if (depth == CognitiveDepth.DIRECT_FACT
                || depth == CognitiveDepth.CONTEXTUAL_CONVERSATION
                || context != null && !context.authoritativeConstraints().isEmpty()) {
            reasons.add(depth == CognitiveDepth.DIRECT_FACT
                    ? "AUTHORITATIVE_FACT_ALREADY_AVAILABLE"
                    : "BOUNDED_GROUNDED_CONTEXT_AVAILABLE");
            return decision(AdaptiveReasoningPolicy.GROUNDED_DIALOGUE, reasons);
        }
        reasons.add("SIMPLE_SOCIAL_NO_ACTION_OR_CONFLICT");
        return decision(AdaptiveReasoningPolicy.FAST_DIALOGUE, reasons);
    }

    private static boolean hasCompetingIntentConflict(CognitionTurn cognition) {
        if (cognition == null || cognition.decision() == null) return false;
        List<IntentCandidate> candidates = cognition.decision().candidateIntents().stream()
                .sorted(Comparator.comparingDouble(IntentCandidate::utility).reversed()).toList();
        return candidates.size() > 1
                && candidates.getFirst().utility() - candidates.get(1).utility() < 0.12
                && candidates.get(1).utility() >= 0.45;
    }

    private static boolean isMultiStep(String text) {
        return text.matches(".*\\b(?:plan|strategy|first .+ then|after that|step by step|"
                + "several|multiple|together with)\\b.*");
    }

    private static boolean isConsequential(String text) {
        return text.matches(".*\\b(?:betray|trust|forgive|abandon|sacrifice|risk your life|"
                + "choose between|promise forever|marry|kill|attack|dangerous decision)\\b.*");
    }

    private static boolean isAmbiguous(String text) {
        return text.matches(".*\\b(?:not sure what|what should we do about|on the other hand|"
                + "conflicting|contradiction|either .+ or)\\b.*");
    }

    private static AdaptiveReasoningDecision decision(AdaptiveReasoningPolicy policy,
            List<String> reasons) {
        return new AdaptiveReasoningDecision(policy, List.copyOf(reasons));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }
}
