package com.inigmasgames.persistentnpcs.conversation.contract;

/** Conservative pre-dispatch prompt/schema/reasoning/final budget proof. */
public record ContractBudgetPlan(
        int contextWindowTokens,
        int promptTokens,
        int schemaTokens,
        int reasoningReserveTokens,
        int finalAnswerReserveTokens,
        int safetyMarginTokens,
        int boundedWorstCaseSerializedTokens,
        int requiredOutputTokens,
        boolean fits,
        String rejectionReason) {

    public ContractBudgetPlan {
        rejectionReason = rejectionReason == null ? "" : rejectionReason.strip();
    }

    public int totalReservedTokens() {
        return promptTokens + schemaTokens + reasoningReserveTokens
                + finalAnswerReserveTokens + safetyMarginTokens;
    }
}
