package com.inigmasgames.persistentnpcs.cognition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.perception.PerceivedEntity;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Generates the provider schema from the exact actions and grounded IDs offered this turn. */
public final class NpcDecisionSchema {
    private NpcDecisionSchema() { }

    public static Contract build(UUID responseId, CognitionTurn cognition,
            NpcActionContext context, List<LlmToolDefinition> offeredTools) {
        List<LlmToolDefinition> tools = List.copyOf(
                offeredTools == null ? List.of() : offeredTools);
        Set<String> intents = new LinkedHashSet<>();
        if (cognition != null && cognition.decision() != null) {
            cognition.decision().candidateIntents().forEach(value ->
                    intents.add(value.intent().name()));
            intents.add(cognition.decision().selectedIntent().name());
        }
        if (intents.isEmpty()) intents.add(GroundedIntent.AMBIENT_RESPONSE.name());

        Set<UUID> targets = groundedTargets(context);
        Set<String> evidence = new LinkedHashSet<>();
        if (cognition != null && cognition.context() != null) {
            evidence.addAll(cognition.context().evidenceRefs());
        }

        JsonObject root = object();
        JsonObject properties = root.getAsJsonObject("properties");
        properties.add("intent", enumString(intents));
        JsonObject spoken = type("string");
        spoken.addProperty("minLength", 1);
        spoken.addProperty("maxLength", 220);
        properties.add("spokenText", spoken);
        Set<String> emotions = new LinkedHashSet<>();
        if (cognition != null && cognition.decision() != null) {
            emotions.add(cognition.decision().emotion().name());
        } else {
            java.util.Arrays.stream(VocalEmotion.values()).map(Enum::name)
                    .forEach(emotions::add);
        }
        properties.add("emotion", enumString(emotions));
        List<String> events = new java.util.ArrayList<>();
        events.add("NONE");
        if (cognition != null && cognition.decision() != null) {
            cognition.decision().paralinguisticEvent().map(Enum::name).ifPresent(events::add);
        } else {
            java.util.Arrays.stream(ParalinguisticEvent.values()).map(Enum::name)
                    .forEach(events::add);
        }
        properties.add("paralinguisticEvent", enumString(events));
        properties.add("actions", actionArray(tools, targets));
        properties.add("groundingEvidenceRefs", evidenceArray(evidence));
        require(root, "intent", "spokenText", "emotion",
                "paralinguisticEvent", "actions", "groundingEvidenceRefs");

        JsonObject named = new JsonObject();
        named.addProperty("name", "npc_decision");
        named.addProperty("strict", true);
        named.add("schema", root);
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_schema");
        responseFormat.add("json_schema", named);
        return new Contract(responseFormat, root, tools, Set.copyOf(intents),
                Set.copyOf(targets), Set.copyOf(evidence), Set.copyOf(emotions),
                Set.copyOf(events));
    }

    private static JsonObject actionArray(List<LlmToolDefinition> tools,
            Set<UUID> targets) {
        JsonObject array = type("array");
        array.addProperty("minItems", 0);
        array.addProperty("maxItems", tools.isEmpty() ? 0 : 1);
        if (tools.isEmpty()) return array;
        JsonArray variants = new JsonArray();
        for (LlmToolDefinition tool : tools) {
            JsonObject action = object();
            JsonObject props = action.getAsJsonObject("properties");
            props.add("actionId", constant(tool.function().name()));
            props.add("targetStableId", enumString(targets.stream()
                    .map(UUID::toString).toList()));
            JsonObject parameters = tool.function().parameters() == null
                    ? object() : tool.function().parameters().deepCopy();
            parameters.addProperty("additionalProperties", false);
            props.add("parameters", parameters);
            require(action, "actionId", "targetStableId", "parameters");
            variants.add(action);
        }
        JsonObject item = new JsonObject();
        item.add("oneOf", variants);
        array.add("items", item);
        return array;
    }

    private static JsonObject evidenceArray(Set<String> evidence) {
        JsonObject array = type("array");
        array.addProperty("uniqueItems", true);
        array.addProperty("maxItems", Math.min(2, evidence.size()));
        if (!evidence.isEmpty()) array.add("items", enumString(evidence));
        return array;
    }

    private static Set<UUID> groundedTargets(NpcActionContext context) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(context.profile().id());
        ids.add(context.session().playerId());
        var perception = context.perception();
        addEntities(ids, perception.nearbyPlayers());
        addEntities(ids, perception.nearbyNpcs());
        addEntities(ids, perception.nearbyHostiles());
        addEntities(ids, perception.nearbyInteractables());
        addEntities(ids, perception.nearbyCraftingStations());
        addItems(ids, perception.nearbyItems());
        if (perception.focusedPlayerHeldItem() != null
                && perception.focusedPlayerHeldItem().entityId() != null) {
            ids.add(perception.focusedPlayerHeldItem().entityId());
        }
        if (context.knownNpcLocator() != null
                && context.knownNpcLocator().targetStableId() != null) {
            ids.add(context.knownNpcLocator().targetStableId());
        }
        return ids;
    }

    private static void addEntities(Set<UUID> ids, List<PerceivedEntity> values) {
        if (values != null) values.stream().map(PerceivedEntity::entityId)
                .filter(java.util.Objects::nonNull).forEach(ids::add);
    }

    private static void addItems(Set<UUID> ids, List<PerceivedItem> values) {
        if (values != null) values.stream().map(PerceivedItem::entityId)
                .filter(java.util.Objects::nonNull).forEach(ids::add);
    }

    private static JsonObject object() {
        JsonObject schema = type("object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject type(String name) {
        JsonObject value = new JsonObject();
        value.addProperty("type", name);
        return value;
    }

    private static JsonObject constant(String value) {
        JsonObject result = type("string");
        result.addProperty("const", value);
        return result;
    }

    private static JsonObject enumString(Iterable<String> values) {
        JsonObject result = type("string");
        JsonArray allowed = new JsonArray();
        values.forEach(allowed::add);
        result.add("enum", allowed);
        return result;
    }

    private static void require(JsonObject object, String... names) {
        JsonArray required = new JsonArray();
        for (String name : names) required.add(name);
        object.add("required", required);
    }

    public record Contract(JsonObject responseFormat, JsonObject schema,
            List<LlmToolDefinition> offeredTools, Set<String> allowedIntents,
            Set<UUID> allowedTargetIds, Set<String> allowedEvidenceRefs,
            Set<String> allowedEmotions, Set<String> allowedParalinguisticEvents) {
        public Contract {
            responseFormat = responseFormat.deepCopy();
            schema = schema.deepCopy();
            offeredTools = List.copyOf(offeredTools);
            allowedIntents = Set.copyOf(allowedIntents);
            allowedTargetIds = Set.copyOf(allowedTargetIds);
            allowedEvidenceRefs = Set.copyOf(allowedEvidenceRefs);
            allowedEmotions = Set.copyOf(allowedEmotions);
            allowedParalinguisticEvents = Set.copyOf(allowedParalinguisticEvents);
        }
    }
}
