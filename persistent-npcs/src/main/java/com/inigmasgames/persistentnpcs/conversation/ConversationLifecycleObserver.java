package com.inigmasgames.persistentnpcs.conversation;

import java.util.Map;

/** Non-authoritative observer for Orbis lifecycle correlation. */
@FunctionalInterface
public interface ConversationLifecycleObserver {
    enum Stage {
        CONTEXT_BUILDING,
        TURN_PLAN_COMPILED,
        CONTRACT_BUDGET_PLANNED,
        LLM_QUEUED,
        LLM_DISPATCHED,
        LLM_STREAMING,
        PHRASE_VALIDATED,
        DECISION_VALIDATING,
        CONTRACT_VALID,
        CONTRACT_INVALID,
        TRUNCATED_OUTPUT,
        RECOVERY_ATTEMPTED,
        RECOVERY_SUCCEEDED,
        RECOVERY_EXHAUSTED
    }

    void onStage(Stage stage, Map<String, String> facts);

    static ConversationLifecycleObserver none() {
        return (stage, facts) -> { };
    }
}
