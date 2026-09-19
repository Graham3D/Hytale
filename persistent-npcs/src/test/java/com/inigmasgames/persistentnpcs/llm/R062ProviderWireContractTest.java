package com.inigmasgames.persistentnpcs.llm;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;

/** Gate 2 regression for the provider wire mode selected by each response contract. */
public final class R062ProviderWireContractTest {
    private R062ProviderWireContractTest() { }

    public static void main(String[] args) {
        LlmRequest dialogue = request(null, 0.30);
        assert OpenAiCompatibleProvider.shouldStream(dialogue, true)
                : "plain dialogue must retain low-latency SSE";
        assert !OpenAiCompatibleProvider.shouldStream(dialogue, false)
                : "provider-level streaming disable must remain authoritative";

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "json_schema");
        LlmRequest strictDecision = request(schema, 0.0);
        assert !OpenAiCompatibleProvider.shouldStream(strictDecision, true)
                : "strict structured decisions must be returned as one buffered JSON object";
        assert strictDecision.temperatureOverride() == 0.0
                : "strict decisions must use deterministic temperature";

        // A bounded deliberative memo is plain text and may stream; the final schema request
        // is independently buffered by the same invariant above.
        LlmRequest memo = request(null, 0.15).withExecutionPolicy(new LlmExecutionPolicy(
                "DELIBERATIVE_MEMO", LlmExecutionPolicy.ReasoningMode.ENABLED,
                List.of("R062_GATE2"), 160));
        assert OpenAiCompatibleProvider.shouldStream(memo, true);
        System.out.println("R062 provider wire-contract tests passed.");
    }

    private static LlmRequest request(JsonObject responseFormat, double temperature) {
        UUID requestId = UUID.randomUUID();
        return new LlmRequest(requestId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ChatMessage("system", "bounded test"),
                        new ChatMessage("user", "hello")),
                List.of(), responseFormat, temperature, 128, requestId,
                new LlmExecutionPolicy("R062_TEST",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        List.of("R062_GATE2"), 128));
    }
}
