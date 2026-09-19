package com.inigmasgames.persistentnpcs.conversation.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;

/** Immutable authority consumed by one conversational provider dispatch. */
public record TurnExecutionPlan(
        UUID responseId,
        UUID providerRequestId,
        long branchEpoch,
        CognitionMode cognitionMode,
        ContextProfile contextProfile,
        DecisionContract decisionContract,
        SpeechContract speechContract,
        ContractBudgetPlan budgets,
        DeadlinePlan deadlines,
        RecoveryPolicy recoveryPolicy,
        List<String> evidenceIds,
        List<String> omittedContextSections,
        String pruningReason,
        EpistemicContract epistemicContract,
        Instant compiledAt) {

    public enum CognitionMode { FAST, GROUNDED, DIRECT_ACTION, DELIBERATIVE, AUTONOMOUS }

    public record DeadlinePlan(long firstTokenMillis, long reasoningMillis,
            long providerHardMillis) {
        public DeadlinePlan {
            if (firstTokenMillis < 50 || reasoningMillis < 50 || providerHardMillis < 100) {
                throw new IllegalArgumentException("positive bounded deadlines required");
            }
        }
    }

    public record RecoveryPolicy(int maximumAttempts, boolean sameModelOnly,
            boolean reasoningOffRecovery, boolean plannerCorrectedStructuredRetry,
            String deterministicRecoveryDialogue) {
        public RecoveryPolicy {
            if (maximumAttempts < 0 || maximumAttempts > 1) {
                throw new IllegalArgumentException("at most one recovery attempt is permitted");
            }
            deterministicRecoveryDialogue = deterministicRecoveryDialogue == null
                    ? "" : deterministicRecoveryDialogue.strip();
        }
    }

    public TurnExecutionPlan {
        if (responseId == null || providerRequestId == null || branchEpoch < 0
                || cognitionMode == null || contextProfile == null
                || decisionContract == null || speechContract == null || budgets == null
                || deadlines == null || recoveryPolicy == null) {
            throw new IllegalArgumentException("complete immutable turn plan required");
        }
        if (!budgets.fits()) throw new PlanRejectedException(budgets.rejectionReason());
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
        omittedContextSections = List.copyOf(
                omittedContextSections == null ? List.of() : omittedContextSections);
        pruningReason = pruningReason == null ? "" : pruningReason.strip();
        compiledAt = compiledAt == null ? Instant.now() : compiledAt;
    }
}
