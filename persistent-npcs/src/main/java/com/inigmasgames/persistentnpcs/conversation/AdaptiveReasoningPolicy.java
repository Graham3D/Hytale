package com.inigmasgames.persistentnpcs.conversation;

/** Orbis-owned cognition depth for one LLM wording/decision request. */
public enum AdaptiveReasoningPolicy {
    FAST_DIALOGUE(false, 56, 56, true, true),
    GROUNDED_DIALOGUE(false, 88, 88, false, true),
    DIRECT_ACTION(false, 96, 96, false, false),
    DELIBERATIVE(true, 160, 512, false, false),
    AUTONOMOUS_DELIBERATION(true, 192, 640, false, false);

    private final boolean reasoningEnabled;
    private final int finalAnswerTokens;
    private final int providerTokenBudget;
    private final boolean earlySpeechEligible;
    private final boolean deterministicWordingOnly;

    AdaptiveReasoningPolicy(boolean reasoningEnabled, int finalAnswerTokens,
            int providerTokenBudget, boolean earlySpeechEligible,
            boolean deterministicWordingOnly) {
        this.reasoningEnabled = reasoningEnabled;
        this.finalAnswerTokens = finalAnswerTokens;
        this.providerTokenBudget = providerTokenBudget;
        this.earlySpeechEligible = earlySpeechEligible;
        this.deterministicWordingOnly = deterministicWordingOnly;
    }

    public boolean reasoningEnabled() { return reasoningEnabled; }
    public int finalAnswerTokens() { return finalAnswerTokens; }
    public int providerTokenBudget() { return providerTokenBudget; }
    public boolean earlySpeechEligible() { return earlySpeechEligible; }
    public boolean deterministicWordingOnly() { return deterministicWordingOnly; }
}
