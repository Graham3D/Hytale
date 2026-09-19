package com.inigmasgames.persistentnpcs.conversation.contract;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import java.util.List;

/** Rejects impossible prompt/schema/output combinations before provider admission. */
public final class ContractBudgetPlanner {
    public static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 4_096;
    public static final int DEFAULT_SAFETY_MARGIN_TOKENS = 192;

    private ContractBudgetPlanner() { }

    public static ContractBudgetPlan plan(List<ChatMessage> messages, JsonObject schema,
            ContextProfile contextProfile, DecisionContract decisionContract,
            int reasoningReserveTokens, int configuredContextWindow) {
        int prompt = estimateMessages(messages);
        int schemaTokens = schema == null ? 0 : estimate(JsonFiles.GSON.toJson(schema));
        int requiredOutput = (int) Math.ceil(
                decisionContract.boundedWorstCaseSerializedTokens() * 1.25d);
        int contextWindow = Math.max(512, configuredContextWindow);
        int finalReserve = Math.max(decisionContract.maximumOutputTokens(), requiredOutput);
        int reasoning = Math.max(0, reasoningReserveTokens);
        int safety = DEFAULT_SAFETY_MARGIN_TOKENS;
        String rejection = "";
        if (prompt > contextProfile.promptTokenCeiling()) {
            rejection = "PROMPT_PROFILE_CEILING_EXCEEDED:" + prompt + ">"
                    + contextProfile.promptTokenCeiling();
        } else if (decisionContract.maximumOutputTokens() < requiredOutput) {
            rejection = "OUTPUT_BUDGET_BELOW_BOUNDED_CONTRACT:"
                    + decisionContract.maximumOutputTokens() + "<" + requiredOutput;
        } else if (prompt + schemaTokens + reasoning + finalReserve + safety > contextWindow) {
            rejection = "CONTEXT_WINDOW_OVERFLOW:"
                    + (prompt + schemaTokens + reasoning + finalReserve + safety)
                    + ">" + contextWindow;
        }
        return new ContractBudgetPlan(contextWindow, prompt, schemaTokens, reasoning,
                finalReserve, safety, decisionContract.boundedWorstCaseSerializedTokens(),
                requiredOutput, rejection.isBlank(), rejection);
    }

    public static int estimate(String value) {
        if (value == null || value.isEmpty()) return 0;
        return Math.max(1, (value.length() + 3) / 4);
    }

    private static int estimateMessages(List<ChatMessage> messages) {
        return (messages == null ? List.<ChatMessage>of() : messages).stream()
                .mapToInt(message -> 4 + estimate(message.role()) + estimate(message.content()))
                .sum() + 8;
    }
}
