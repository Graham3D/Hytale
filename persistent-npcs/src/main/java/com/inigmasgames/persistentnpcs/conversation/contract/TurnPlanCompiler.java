package com.inigmasgames.persistentnpcs.conversation.contract;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningDecision;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicFeatureMode;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicProductionRoute;

/** The only route/output/context/budget compiler for live conversational dispatches. */
public final class TurnPlanCompiler {
    private TurnPlanCompiler() { }

    public static Draft draft(CognitiveContextPlan routed,
            AdaptiveReasoningDecision reasoning, boolean deterministicAction,
            boolean discretionaryChoice, boolean hasPlayerFacingSpeech) {
        AdaptiveReasoningPolicy policy = reasoning == null
                ? AdaptiveReasoningPolicy.DELIBERATIVE : reasoning.policy();
        ContextProfile profile = ContextProfile.forRoute(policy, deterministicAction,
                discretionaryChoice);
        ContextProfile.Restriction restriction = profile.restrict(routed);
        DecisionContract decision = deterministicAction
                ? DecisionContract.actionResult()
                : discretionaryChoice ? DecisionContract.choice()
                : switch (policy) {
                    case FAST_DIALOGUE -> DecisionContract.dialogue(false);
                    case GROUNDED_DIALOGUE -> DecisionContract.dialogue(true);
                    case DIRECT_ACTION -> DecisionContract.actionResult();
                    case DELIBERATIVE -> DecisionContract.deliberativeFinal();
                    case AUTONOMOUS_DELIBERATION -> DecisionContract.autonomous();
                };
        SpeechContract speech = !hasPlayerFacingSpeech
                ? SpeechContract.silent()
                : decision.structured() ? SpeechContract.afterValidation()
                : SpeechContract.plain(policy.earlySpeechEligible());
        TurnExecutionPlan.CognitionMode mode = switch (policy) {
            case FAST_DIALOGUE -> TurnExecutionPlan.CognitionMode.FAST;
            case GROUNDED_DIALOGUE -> TurnExecutionPlan.CognitionMode.GROUNDED;
            case DIRECT_ACTION -> TurnExecutionPlan.CognitionMode.DIRECT_ACTION;
            case DELIBERATIVE -> TurnExecutionPlan.CognitionMode.DELIBERATIVE;
            case AUTONOMOUS_DELIBERATION -> TurnExecutionPlan.CognitionMode.AUTONOMOUS;
        };
        return new Draft(mode, profile, restriction, decision, speech, policy);
    }

    /** Explicit speech exception for already-authorized bounded NPC-to-NPC scenes. */
    public static Draft autonomousSceneSpeech(CognitiveContextPlan routed) {
        ContextProfile profile = ContextProfile.forRoute(
                AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION, false, false);
        return new Draft(TurnExecutionPlan.CognitionMode.AUTONOMOUS, profile,
                profile.restrict(routed), DecisionContract.dialogue(true),
                SpeechContract.afterValidation(),
                AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION);
    }

    public static TurnExecutionPlan compile(UUID responseId, UUID providerRequestId,
            long branchEpoch, Draft draft, List<ChatMessage> messages, JsonObject schema,
            List<String> evidenceIds) {
        return compile(responseId, providerRequestId, branchEpoch, draft, messages, schema,
                evidenceIds, null);
    }

    public static TurnExecutionPlan compile(UUID responseId, UUID providerRequestId,
            long branchEpoch, Draft draft, List<ChatMessage> messages, JsonObject schema,
            List<String> evidenceIds, EpistemicContract epistemicContract) {
        if (epistemicContract != null
                && epistemicContract.mode() == EpistemicFeatureMode.AUTHORITATIVE
                && (!epistemicContract.answerPlan().status().equals("E3_AUTHORITATIVE")
                        || epistemicContract.answerability().name().equals("UNIMPLEMENTED"))) {
            throw new PlanRejectedException("incomplete authoritative EpistemicContract");
        }
        if (EpistemicProductionRoute.authoritative(epistemicContract)
                && draft.policy().reasoningEnabled()) {
            throw new PlanRejectedException(
                    "supported authoritative EpistemicContract cannot use legacy deliberation");
        }
        int reasoningReserve = draft.policy().reasoningEnabled()
                ? Math.min(384, draft.policy().providerTokenBudget()) : 0;
        ContractBudgetPlan budget = ContractBudgetPlanner.plan(messages, schema,
                draft.contextProfile(), draft.decisionContract(), reasoningReserve,
                ContractBudgetPlanner.DEFAULT_CONTEXT_WINDOW_TOKENS);
        long hard = draft.mode() == TurnExecutionPlan.CognitionMode.DELIBERATIVE
                ? 12_000L : 8_000L;
        TurnExecutionPlan.DeadlinePlan deadlines = new TurnExecutionPlan.DeadlinePlan(
                3_000L, draft.policy().reasoningEnabled() ? 8_000L : 1_000L, hard);
        TurnExecutionPlan.RecoveryPolicy recovery = new TurnExecutionPlan.RecoveryPolicy(
                1, true, true, draft.decisionContract().structured(),
                "I lost my train of thought. Could you ask me again?");
        return new TurnExecutionPlan(responseId, providerRequestId,
                Math.max(0, branchEpoch), draft.mode(), draft.contextProfile(),
                draft.decisionContract(), draft.speechContract(), budget, deadlines,
                recovery, evidenceIds, draft.restriction().omittedSections(),
                draft.restriction().pruningReason(), epistemicContract, Instant.now());
    }

    /** Rebudget a follow-up dispatch without changing the turn's immutable route contracts. */
    public static TurnExecutionPlan recompile(TurnExecutionPlan prior,
            List<ChatMessage> messages, JsonObject schema) {
        if (prior == null) throw new IllegalArgumentException("prior turn plan required");
        ContractBudgetPlan budget = ContractBudgetPlanner.plan(messages, schema,
                prior.contextProfile(), prior.decisionContract(), 0,
                prior.budgets().contextWindowTokens());
        return new TurnExecutionPlan(prior.responseId(), prior.providerRequestId(),
                prior.branchEpoch(), prior.cognitionMode(), prior.contextProfile(),
                prior.decisionContract(), prior.speechContract(), budget,
                prior.deadlines(), prior.recoveryPolicy(), prior.evidenceIds(),
                prior.omittedContextSections(), prior.pruningReason(),
                prior.epistemicContract(), Instant.now());
    }

    public static TurnExecutionPlan deliberativeMemo(TurnExecutionPlan finalPlan,
            UUID providerRequestId, List<ChatMessage> messages) {
        DecisionContract memo = DecisionContract.deliberativeMemo();
        ContractBudgetPlan budget = ContractBudgetPlanner.plan(messages, null,
                finalPlan.contextProfile(), memo, 384,
                finalPlan.budgets().contextWindowTokens());
        return new TurnExecutionPlan(finalPlan.responseId(), providerRequestId,
                finalPlan.branchEpoch(), TurnExecutionPlan.CognitionMode.DELIBERATIVE,
                finalPlan.contextProfile(), memo, SpeechContract.silent(), budget,
                finalPlan.deadlines(), finalPlan.recoveryPolicy(), finalPlan.evidenceIds(),
                finalPlan.omittedContextSections(), finalPlan.pruningReason(),
                finalPlan.epistemicContract(), Instant.now());
    }

    public record Draft(TurnExecutionPlan.CognitionMode mode,
            ContextProfile contextProfile, ContextProfile.Restriction restriction,
            DecisionContract decisionContract, SpeechContract speechContract,
            AdaptiveReasoningPolicy policy) { }
}
