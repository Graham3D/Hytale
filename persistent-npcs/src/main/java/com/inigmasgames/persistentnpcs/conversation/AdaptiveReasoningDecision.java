package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import java.util.List;

public record AdaptiveReasoningDecision(
        AdaptiveReasoningPolicy policy,
        List<String> reasonCodes) {

    public AdaptiveReasoningDecision {
        policy = policy == null ? AdaptiveReasoningPolicy.DELIBERATIVE : policy;
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
    }

    public LlmExecutionPolicy llmPolicy() {
        return new LlmExecutionPolicy(policy.name(), policy.reasoningEnabled()
                ? LlmExecutionPolicy.ReasoningMode.ENABLED
                : LlmExecutionPolicy.ReasoningMode.DISABLED,
                reasonCodes, policy.finalAnswerTokens());
    }
}
