package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionSchema;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionValidator;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Verifies both installed models honor OpenAI response_format through the real endpoint. */
public final class R038LiveStructuredDecisionTest {
    private R038LiveStructuredDecisionTest() { }

    public static void main(String[] args) {
        verify("hf.co/openresearchtools/Qwen3.5-4B-Instruct-GGUF:Q4_K_M");
        verify("nemotron-3-nano:4b");
        System.out.println("R038 live Qwen/Nemotron structured-output tests passed.");
    }

    private static void verify(String model) {
        verifySimpleWireSchema(model);
        verifyFullNpcDecisionSchema(model);
    }

    private static void verifySimpleWireSchema(String model) {
        UUID responseId = UUID.randomUUID();
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        properties.add("responseId", constant(responseId.toString()));
        JsonObject spoken = new JsonObject();
        spoken.addProperty("type", "string");
        spoken.addProperty("minLength", 1);
        properties.add("spokenText", spoken);
        JsonObject actions = new JsonObject();
        actions.addProperty("type", "array");
        actions.addProperty("maxItems", 0);
        properties.add("actions", actions);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("responseId"); required.add("spokenText"); required.add("actions");
        schema.add("required", required);
        JsonObject named = new JsonObject();
        named.addProperty("name", "npc_decision_live_test");
        named.addProperty("strict", true);
        named.add("schema", schema);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("json_schema", named);

        LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(
                        new ChatMessage("system", "Return only the requested JSON object."),
                        new ChatMessage("user", "Say hello without an action.")))
                .withSystemInstruction("Use the authoritative structured-output contract.")
                .constrained(format, 0.2);
        try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config(model))) {
            var result = provider.generateResponse(request).join();
            JsonObject parsed = com.inigmasgames.persistentnpcs.json.JsonFiles.GSON.fromJson(
                    result.text(), JsonObject.class);
            assert parsed.get("responseId").getAsString().equals(responseId.toString());
            assert !parsed.get("spokenText").getAsString().isBlank();
            assert parsed.getAsJsonArray("actions").isEmpty();
            System.out.println(model + " structured ttft="
                    + result.latency().timeToFirstTokenMillis() + "ms total="
                    + result.latency().completionMillis() + "ms");
        }
    }

    private static void verifyFullNpcDecisionSchema(String model) {
        UUID responseId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npcId, "Mara", "apprentice blacksmith",
                "bold and practical", "A village smith.", "Learn the craft.", "home",
                "forge", List.of(), List.of(), List.of(), List.of("FOLLOW_PLAYER"), 0);
        ConversationSession session = new ConversationSession(UUID.randomUUID(), npcId,
                playerId, Instant.now());
        NpcPerceptionSnapshot perception = new NpcPerceptionSnapshot(npcId,
                UUID.randomUUID(), UUID.randomUUID(), null, 0, 64, 0, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of());
        NpcActionContext context = new NpcActionContext(profile, session, perception,
                "Follow me.");
        JsonObject empty = new JsonObject();
        empty.addProperty("type", "object");
        empty.add("properties", new JsonObject());
        empty.addProperty("additionalProperties", false);
        NpcDecisionSchema.Contract contract = NpcDecisionSchema.build(responseId, null,
                context, List.of(new LlmToolDefinition("FOLLOW_PLAYER",
                        "Begin following the focused player.", empty)));
        String instruction = "You are Mara, a concise practical Hytale NPC. The player said "
                + "Follow me. Return exactly one JSON object matching this schema. If you agree, "
                + "the same object must include FOLLOW_PLAYER. No markdown or reasoning. SCHEMA="
                + contract.schema();
        LlmRequest request = new LlmRequest(session.sessionId(), npcId, playerId, List.of(
                new ChatMessage("system", instruction),
                new ChatMessage("user", "Follow me."))).constrained(
                        contract.responseFormat(), 0.2);
        try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config(model))) {
            var result = provider.generateResponse(request).join();
            var validated = new NpcDecisionValidator().validate(result.text(), responseId,
                    npcId, contract);
            assert validated.valid() : model + " rejected="
                    + validated.rejectedFieldsOrActions() + " raw=" + result.text();
            System.out.println(model + " full NpcDecision valid actions="
                    + validated.decision().actions().stream().map(value -> value.actionId())
                            .toList());
        }
    }

    private static JsonObject constant(String value) {
        JsonObject result = new JsonObject();
        result.addProperty("type", "string");
        result.addProperty("const", value);
        return result;
    }

    private static FrameworkConfig config(String model) {
        return new FrameworkConfig("http://127.0.0.1:11434/v1/chat/completions", model,
                "", 1_500, 12_000, 0.2, 180, 600, 6, 2_000, 300,
                true, 60_000, 15_000, "none");
    }
}
