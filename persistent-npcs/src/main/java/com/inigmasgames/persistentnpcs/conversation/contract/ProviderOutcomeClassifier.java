package com.inigmasgames.persistentnpcs.conversation.contract;

import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import java.util.Locale;

/** Deterministic provider completion classification used before contract validation. */
public final class ProviderOutcomeClassifier {
    public enum Outcome {
        COMPLETE,
        TRUNCATED_OUTPUT,
        CONTRACT_INVALID,
        REASONING_ONLY,
        EMPTY_OUTPUT
    }

    private ProviderOutcomeClassifier() { }

    public static Outcome classify(LlmResult result, TurnExecutionPlan plan) {
        String text = result == null || result.text() == null ? "" : result.text().strip();
        if (text.isBlank()) {
            if (result != null && result.reasoningTelemetry().reasoningEventCount() > 0) {
                return Outcome.REASONING_ONLY;
            }
            return Outcome.EMPTY_OUTPUT;
        }
        String finish = result.finishReason() == null ? ""
                : result.finishReason().toLowerCase(Locale.ROOT);
        int completionTokens = result.usage().completionTokens();
        int limit = plan == null ? Integer.MAX_VALUE
                : plan.decisionContract().maximumOutputTokens();
        if (finish.contains("length") || finish.contains("max_token")
                || completionTokens > 0 && completionTokens >= limit) {
            return Outcome.TRUNCATED_OUTPUT;
        }
        if (plan != null && plan.decisionContract().structured()) {
            try {
                if (!JsonParser.parseString(text).isJsonObject()) return Outcome.CONTRACT_INVALID;
            } catch (RuntimeException invalid) {
                return likelyTruncated(text) ? Outcome.TRUNCATED_OUTPUT
                        : Outcome.CONTRACT_INVALID;
            }
        }
        return Outcome.COMPLETE;
    }

    private static boolean likelyTruncated(String text) {
        int braces = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') quoted = !quoted;
            else if (!quoted && c == '{') braces++;
            else if (!quoted && c == '}') braces--;
        }
        return quoted || braces > 0 || text.endsWith(":") || text.endsWith(",");
    }
}
