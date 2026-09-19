package com.inigmasgames.persistentnpcs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionSchema;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionValidator;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningDecision;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.contract.ProviderOutcomeClassifier;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnExecutionPlan;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnPlanCompiler;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Manual Gate 2 probe against the real installed Nemotron provider. */
public final class R062RealProviderCalibration {
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private R062RealProviderCalibration() { }

    public static void main(String[] args) {
        String endpoint = args.length > 0 ? args[0]
                : "http://127.0.0.1:11434/v1/chat/completions";
        int gpuLayers = args.length > 1 ? Integer.parseInt(args[1]) : 12;
        FrameworkConfig config = new FrameworkConfig(endpoint, "nemotron-3-nano:4b", "",
                2_000, 90_000, 0.2, 640, 800, 0, 10, 600,
                true, 90_000, 30_000, "none");
        JsonObject report = new JsonObject();
        report.addProperty("provider", "NEMOTRON");
        report.addProperty("model", "nemotron-3-nano:4b");
        report.addProperty("gpuLayers", gpuLayers);
        report.addProperty("physicalPttValidated", false);
        JsonArray runs = new JsonArray();
        report.add("runs", runs);
        try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(config,
                OpenAiCompatibleProvider.ToolChoicePolicy.NAMED_SINGLE,
                value -> System.err.println("PROVIDER " + value), gpuLayers, "10m")) {
            runs.add(run(provider, AdaptiveReasoningPolicy.FAST_DIALOGUE, false, false,
                    "You are Mara, a practical and observant young blacksmith. Answer in one "
                            + "short natural spoken reply. Do not invent facts.",
                    "Hello Mara. How are you?", null));
            runs.add(run(provider, AdaptiveReasoningPolicy.GROUNDED_DIALOGUE, false, false,
                    "You are Mara. Authoritative perception: the player is holding an Onyxium "
                            + "dagger. Answer only from that evidence in one short spoken reply.",
                    "What can you see in my hand?", null));
            runs.add(run(provider, AdaptiveReasoningPolicy.DIRECT_ACTION, true, false,
                    "You are Mara. Authoritative action result: FOLLOW_PLAYER completed and you "
                            + "are now following the player. Describe only that result briefly.",
                    "Follow me.", null));
            runs.add(runExactNpcDecision(provider));
            runs.add(run(provider, AdaptiveReasoningPolicy.DELIBERATIVE, false, true,
                    "You are Mara. Select one grounded response. Return only the requested strict "
                            + "JSON decision and no markdown.",
                    "A friend asks for help, but you promised your father to finish urgent work.",
                    compactDecisionSchema()));
            runs.add(runDeliberative(provider));
            runs.add(run(provider, AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION,
                    false, true,
                    "You are Mara during an autonomous planning tick. Select one bounded intent "
                            + "and return only the requested strict JSON decision.",
                    "Choose between CONTINUE_SCHEDULE and INSPECT_NEARBY_WORKBENCH.",
                    compactDecisionSchema()));
        }
        System.out.println(GSON.toJson(report));
    }

    private static JsonObject runExactNpcDecision(OpenAiCompatibleProvider provider) {
        UUID responseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
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
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", new JsonObject());
        parameters.addProperty("additionalProperties", false);
        NpcDecisionSchema.Contract contract = NpcDecisionSchema.build(responseId, null,
                context, List.of(new LlmToolDefinition("FOLLOW_PLAYER",
                        "Begin following the focused player.", parameters)));
        String instruction = "Return exactly one JSON object matching NPC_DECISION_SCHEMA. "
                + "Use intent AMBIENT_RESPONSE, emotion CALM, paralinguisticEvent NONE, "
                + "groundingEvidenceRefs=[], spokenText=All right., and include the offered "
                + "FOLLOW_PLAYER action targeting the player. Orbis injects authoritative "
                + "response, NPC, and actor IDs; do not add those fields. No markdown or reasoning. "
                + "NPC_DECISION_SCHEMA=" + contract.schema();
        List<ChatMessage> messages = List.of(new ChatMessage("system", instruction),
                new ChatMessage("user", "Follow me."));
        AdaptiveReasoningDecision reasoning = new AdaptiveReasoningDecision(
                AdaptiveReasoningPolicy.DIRECT_ACTION, List.of("R062_EXACT_SCHEMA"));
        TurnPlanCompiler.Draft draft = TurnPlanCompiler.draft(
                CognitiveContextPlan.full("DISCRETIONARY_ACTION"), reasoning,
                false, true, true);
        TurnExecutionPlan plan = TurnPlanCompiler.compile(responseId, requestId, 1, draft,
                messages, contract.schema(), List.of());
        LlmRequest request = new LlmRequest(session.sessionId(), npcId, playerId, messages,
                List.of(), contract.responseFormat(), 0.0,
                plan.decisionContract().maximumOutputTokens(), requestId,
                new LlmExecutionPolicy("DISCRETIONARY_CHOICE_FINAL",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        reasoning.reasonCodes(), 256)).withTurnExecutionPlan(plan);
        JsonObject measured = execute(provider, "EXACT_NPC_DECISION_SCHEMA", request, plan,
                System.nanoTime());
        NpcDecisionValidator.Validation validation = new NpcDecisionValidator().validate(
                measured.get("response").getAsString(), responseId, npcId, contract);
        measured.addProperty("javaValidation", validation.result());
        JsonArray rejected = new JsonArray();
        validation.rejectedFieldsOrActions().forEach(rejected::add);
        measured.add("rejectedFieldsOrActions", rejected);
        return measured;
    }

    private static JsonObject run(OpenAiCompatibleProvider provider,
            AdaptiveReasoningPolicy policy, boolean deterministicAction,
            boolean discretionaryChoice, String system, String user, JsonObject format) {
        UUID responseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        List<ChatMessage> messages = List.of(new ChatMessage("system", system),
                new ChatMessage("user", user));
        AdaptiveReasoningDecision reasoning = new AdaptiveReasoningDecision(policy,
                List.of("R062_REAL_PROVIDER_CALIBRATION"));
        TurnPlanCompiler.Draft draft = TurnPlanCompiler.draft(
                CognitiveContextPlan.full(policy.name()), reasoning,
                deterministicAction, discretionaryChoice, true);
        JsonObject schema = format == null ? null
                : format.getAsJsonObject("json_schema").getAsJsonObject("schema");
        TurnExecutionPlan plan = TurnPlanCompiler.compile(responseId, requestId, 1, draft,
                messages, schema, List.of("CALIBRATION_EVIDENCE"));
        boolean strictFinal = format != null;
        LlmExecutionPolicy execution = new LlmExecutionPolicy(policy.name(),
                !strictFinal && policy.reasoningEnabled()
                        ? LlmExecutionPolicy.ReasoningMode.ENABLED
                        : LlmExecutionPolicy.ReasoningMode.DISABLED,
                reasoning.reasonCodes(), policy.finalAnswerTokens());
        LlmRequest request = new LlmRequest(responseId, UUID.randomUUID(), UUID.randomUUID(),
                messages, List.of(), format, format == null ? 0.30 : 0.0,
                plan.decisionContract().maximumOutputTokens(), requestId, execution)
                .withTurnExecutionPlan(plan);
        String route = discretionaryChoice
                ? policy == AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION
                        ? "AUTONOMOUS" : "DISCRETIONARY_CHOICE"
                : policy.name();
        return execute(provider, route, request, plan, System.nanoTime());
    }

    private static JsonObject runDeliberative(OpenAiCompatibleProvider provider) {
        long wholeStarted = System.nanoTime();
        UUID responseId = UUID.randomUUID();
        UUID finalRequestId = UUID.randomUUID();
        List<ChatMessage> base = List.of(
                new ChatMessage("system", "You are Mara. Consider obligations, relationships, "
                        + "and risk. Never reveal hidden chain-of-thought."),
                new ChatMessage("user", "A friend asks you to abandon an urgent promise to your "
                        + "father for a risky repair. Decide what to do."));
        AdaptiveReasoningDecision reasoning = new AdaptiveReasoningDecision(
                AdaptiveReasoningPolicy.DELIBERATIVE, List.of("R062_TWO_STAGE_CALIBRATION"));
        TurnPlanCompiler.Draft draft = TurnPlanCompiler.draft(
                CognitiveContextPlan.full("CONFLICTING_OBLIGATIONS"), reasoning,
                false, false, true);
        JsonObject format = compactDecisionSchema();
        JsonObject schema = format.getAsJsonObject("json_schema").getAsJsonObject("schema");
        TurnExecutionPlan finalPlan = TurnPlanCompiler.compile(responseId, finalRequestId, 1,
                draft, base, schema, List.of("PROMISE_TO_FATHER", "FRIEND_REQUEST"));

        UUID memoRequestId = UUID.randomUUID();
        ArrayList<ChatMessage> memoMessages = new ArrayList<>(base);
        memoMessages.add(0, new ChatMessage("system", "Produce a compact decision memo only: "
                + "evidence, constraints, and recommended intent in at most three short "
                + "sentences. No dialogue or JSON."));
        LlmRequest memoRequest = new LlmRequest(responseId, UUID.randomUUID(), UUID.randomUUID(),
                memoMessages, List.of(), null, 0.15, 112, memoRequestId,
                new LlmExecutionPolicy("DELIBERATIVE_MEMO",
                        LlmExecutionPolicy.ReasoningMode.ENABLED,
                        reasoning.reasonCodes(), 112))
                .withTurnExecutionPlan(TurnPlanCompiler.deliberativeMemo(
                        finalPlan, memoRequestId, memoMessages));
        JsonObject memo = execute(provider, "DELIBERATIVE_MEMO", memoRequest,
                memoRequest.turnExecutionPlan(), System.nanoTime());
        String memoText = memo.get("response").getAsString();

        ArrayList<ChatMessage> finalMessages = new ArrayList<>(base);
        finalMessages.add(0, new ChatMessage("system", "ORBIS_BOUNDED_DECISION_MEMO="
                + memoText.replaceAll("\\s+", " ").strip()
                + "\nReturn only the strict final JSON contract. Do not continue reasoning."));
        TurnExecutionPlan recompiled = TurnPlanCompiler.recompile(finalPlan,
                finalMessages, schema);
        LlmRequest finalRequest = new LlmRequest(responseId, UUID.randomUUID(), UUID.randomUUID(),
                finalMessages, List.of(), format, 0.0,
                recompiled.decisionContract().maximumOutputTokens(), finalRequestId,
                new LlmExecutionPolicy("DELIBERATIVE_FINAL",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        reasoning.reasonCodes(), 160)).withTurnExecutionPlan(recompiled);
        JsonObject result = execute(provider, "DELIBERATIVE_TWO_STAGE", finalRequest,
                recompiled, wholeStarted);
        result.add("memo", memo);
        return result;
    }

    private static JsonObject execute(OpenAiCompatibleProvider provider, String route,
            LlmRequest request, TurnExecutionPlan plan, long started) {
        AtomicInteger deltas = new AtomicInteger();
        LlmResult result = provider.generateResponse(request, ignored ->
                deltas.incrementAndGet()).join();
        JsonObject value = new JsonObject();
        value.addProperty("route", route);
        value.addProperty("contract", plan.decisionContract().kind().name());
        value.addProperty("wireMode", request.responseFormat() == null ? "SSE" : "JSON");
        value.addProperty("reasoningMode",
                request.executionPolicy().requestedReasoningMode().name());
        value.addProperty("plannedPromptTokens", plan.budgets().promptTokens());
        value.addProperty("plannedOutputTokens", plan.budgets().requiredOutputTokens());
        value.addProperty("actualPromptTokens", result.usage().promptTokens());
        value.addProperty("actualCompletionTokens", result.usage().completionTokens());
        value.addProperty("ttftMs", result.latency().timeToFirstTokenMillis());
        value.addProperty("providerCompletionMs", result.latency().completionMillis());
        value.addProperty("wallMs", (System.nanoTime() - started) / 1_000_000L);
        value.addProperty("tokensPerSecond", result.usage().tokensPerSecond(
                result.latency().completionMillis()));
        value.addProperty("streamDeltas", deltas.get());
        value.addProperty("finishReason", result.finishReason());
        value.addProperty("outcome", ProviderOutcomeClassifier.classify(result, plan).name());
        value.addProperty("actualReasoningMode", result.reasoningTelemetry().actualMode());
        value.addProperty("response", result.text().replaceAll("\\s+", " ").strip());
        return value;
    }

    private static JsonObject compactDecisionSchema() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "object");
        root.addProperty("additionalProperties", false);
        JsonObject properties = new JsonObject();
        properties.add("intent", enumString("CONTINUE_OBLIGATION", "HELP_FRIEND",
                "CONTINUE_SCHEDULE", "INSPECT_NEARBY_WORKBENCH"));
        JsonObject spoken = new JsonObject();
        spoken.addProperty("type", "string");
        spoken.addProperty("maxLength", 220);
        properties.add("spokenText", spoken);
        properties.add("emotion", enumString("CALM", "CURIOUS", "UNEASY"));
        JsonObject actions = new JsonObject();
        actions.addProperty("type", "array");
        actions.addProperty("maxItems", 0);
        properties.add("actions", actions);
        root.add("properties", properties);
        JsonArray required = new JsonArray();
        List.of("intent", "spokenText", "emotion", "actions").forEach(required::add);
        root.add("required", required);
        JsonObject named = new JsonObject();
        named.addProperty("name", "npc_decision_calibration");
        named.addProperty("strict", true);
        named.add("schema", root);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("json_schema", named);
        return format;
    }

    private static JsonObject enumString(String... values) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        JsonArray allowed = new JsonArray();
        for (String value : values) allowed.add(value);
        schema.add("enum", allowed);
        return schema;
    }
}
