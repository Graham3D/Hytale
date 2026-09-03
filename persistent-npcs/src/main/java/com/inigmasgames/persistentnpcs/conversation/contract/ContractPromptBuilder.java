package com.inigmasgames.persistentnpcs.conversation.contract;

import com.inigmasgames.persistentnpcs.cognition.CognitionContext;
import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.List;

/** Small allowlisted prompt compiler for structured choice/deliberative contracts. */
public final class ContractPromptBuilder {
    private ContractPromptBuilder() { }

    public static LlmRequest compact(LlmRequest base, NpcProfile profile,
            String playerMessage, CognitionTurn cognition, TurnPlanCompiler.Draft draft,
            List<LlmToolDefinition> tools) {
        CognitionContext context = cognition == null ? null : cognition.context();
        String decision = cognition == null || cognition.decision() == null ? "unknown"
                : cognition.decision().selectedIntent().name();
        String candidates = cognition == null || cognition.decision() == null ? "[]"
                : compact(cognition.decision().candidateIntents().stream().limit(3)
                        .map(value -> value.intent().name() + "=" + value.utility()).toList()
                                .toString(), 280);
        String relationship = context == null ? "none" : compact(
                context.relationships().stream().limit(2).toList().toString(), 260);
        String memories = context == null ? "none" : compact(context.memories().stream()
                .limit(3).map(value -> value.summary()).toList().toString(), 360);
        String world = context == null || context.semanticWorld() == null ? "unavailable"
                : compact(context.semanticWorld().promptBlock(playerMessage, true), 520);
        String goals = profile == null ? "[]" : compact(profile.goals().toString(), 220);
        String values = profile == null ? "[]" : compact(profile.values().toString(), 220);
        String actions = tools == null ? "[]" : tools.stream().limit(3)
                .map(value -> value.function().name()).toList().toString();
        String system = """
                You are %s, one persistent Hytale NPC. This is a compact Orbis decision
                contract. Return only the requested strict JSON; do not output reasoning,
                markdown, world coordinates, IDs not present in the schema, or hidden work.
                Keep spokenText natural, in character, and at most 220 characters. Subjective
                preferences and social reactions are allowed. World, memory, relationship, and
                action claims require compatible supplied evidence. Do not promise a physical
                action unless Orbis has already committed it.

                CONTRACT=%s
                PERSONALITY=%s
                SPEAKING_STYLE=%s
                VALUES=%s
                GOALS=%s
                DETERMINISTIC_INTENT=%s
                COMPETING_INTENTS=%s
                RELATIONSHIP=%s
                RELEVANT_MEMORIES=%s
                RELEVANT_WORLD=%s
                AVAILABLE_CHOICES=%s
                """.formatted(profile == null ? "NPC" : profile.name(),
                draft.decisionContract().kind(),
                compact(profile == null ? "" : profile.personality(), 360),
                compact(profile == null ? "" : profile.speakingStyle(), 220), values, goals,
                decision, candidates, relationship, memories, world, actions);
        return new LlmRequest(base.conversationId(), base.npcId(), base.playerId(),
                List.of(new ChatMessage("system", system),
                        new ChatMessage("user", playerMessage)),
                tools == null ? List.of() : tools, null, null, null,
                base.providerRequestId(), base.executionPolicy(), null);
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
