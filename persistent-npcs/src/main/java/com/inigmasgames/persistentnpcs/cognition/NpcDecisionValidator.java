package com.inigmasgames.persistentnpcs.cognition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fail-closed Java-side validation; provider schema enforcement is defense in depth. */
public final class NpcDecisionValidator {
    private static final Set<String> TOP_LEVEL = Set.of("intent", "spokenText", "emotion",
            "paralinguisticEvent", "actions",
            "groundingEvidenceRefs");
    private static final Set<String> ACTION_FIELDS = Set.of("actionId", "targetStableId",
            "parameters");
    private final NpcGroundingClaimValidator grounding = new NpcGroundingClaimValidator();

    public Validation validate(String raw, UUID responseId, UUID npcStableId,
            NpcDecisionSchema.Contract contract) {
        List<String> rejected = new ArrayList<>();
        JsonObject root;
        try {
            root = JsonFiles.GSON.fromJson(raw, JsonObject.class);
            if (root == null) throw new IllegalArgumentException("JSON value was null");
        } catch (RuntimeException failure) {
            return Validation.rejected("INVALID_JSON",
                    List.of(compact(failure.getMessage())), List.of());
        }
        rejectUnknown(root, TOP_LEVEL, "decision", rejected);
        requireFields(root, TOP_LEVEL, "decision", rejected);

        if (responseId == null) rejected.add("authoritative responseId is missing");
        if (npcStableId == null) rejected.add("authoritative npcStableId is missing");

        GroundedIntent intent = enumValue(root, "intent", GroundedIntent.class, rejected);
        if (intent != null && !contract.allowedIntents().contains(intent.name())) {
            rejected.add("intent was not offered: " + intent);
        }
        String spoken = string(root, "spokenText", rejected);
        if (spoken != null && (spoken.isBlank() || spoken.length() > 220)) {
            rejected.add("spokenText must contain 1-220 characters");
        }
        VocalEmotion emotion = enumValue(root, "emotion", VocalEmotion.class, rejected);
        if (emotion != null && !contract.allowedEmotions().contains(emotion.name())) {
            rejected.add("emotion was not authorized for this turn: " + emotion);
        }
        Optional<ParalinguisticEvent> event = Optional.empty();
        String eventName = string(root, "paralinguisticEvent", rejected);
        if (eventName != null && !eventName.equals("NONE")) {
            try {
                event = Optional.of(ParalinguisticEvent.valueOf(eventName));
            } catch (IllegalArgumentException failure) {
                rejected.add("unknown paralinguisticEvent: " + eventName);
            }
        }
        if (eventName != null
                && !contract.allowedParalinguisticEvents().contains(eventName)) {
            rejected.add("paralinguisticEvent was not authorized for this turn: "
                    + eventName);
        }

        Map<String, LlmToolDefinition> offered = new LinkedHashMap<>();
        contract.offeredTools().forEach(tool -> offered.put(
                normalize(tool.function().name()), tool));
        List<NpcDecisionAction> actions = validateActions(root.get("actions"), npcStableId,
                offered, contract.allowedTargetIds(), rejected);
        List<String> evidence = validateEvidence(root.get("groundingEvidenceRefs"),
                contract.allowedEvidenceRefs(), rejected);
        ActionPromiseGuard.violation(spoken, actions).ifPresent(rejected::add);

        if (!rejected.isEmpty() || responseId == null || npcStableId == null
                || intent == null || spoken == null || emotion == null) {
            return Validation.rejected("SCHEMA_REJECTED", rejected, List.of());
        }
        NpcDecision decision = new NpcDecision(responseId, npcStableId, intent, spoken,
                emotion, event, actions, evidence);
        List<NpcGroundingClaimValidator.ClaimAssessment> claims = grounding.validate(
                spoken, evidence);
        List<String> unsupported = claims.stream().filter(value -> !value.valid())
                .map(NpcGroundingClaimValidator.ClaimAssessment::diagnostic).toList();
        if (!unsupported.isEmpty()) {
            return new Validation(false, "GROUNDING_REJECTED", unsupported, decision,
                    claims.stream().map(
                            NpcGroundingClaimValidator.ClaimAssessment::diagnostic).toList());
        }
        return new Validation(true, "VALID", List.of(), decision,
                claims.stream().map(
                        NpcGroundingClaimValidator.ClaimAssessment::diagnostic).toList());
    }

    private static List<NpcDecisionAction> validateActions(JsonElement element, UUID actor,
            Map<String, LlmToolDefinition> offered, Set<UUID> targets,
            List<String> rejected) {
        if (element == null || !element.isJsonArray()) {
            rejected.add("actions must be an array");
            return List.of();
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() > 1) rejected.add("at most one action is permitted per decision");
        if (offered.isEmpty() && !array.isEmpty()) {
            rejected.add("no actions were offered for this turn");
        }
        List<NpcDecisionAction> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            if (!array.get(index).isJsonObject()) {
                rejected.add("actions[" + index + "] must be an object");
                continue;
            }
            JsonObject action = array.get(index).getAsJsonObject();
            rejectUnknown(action, ACTION_FIELDS, "actions[" + index + "]", rejected);
            requireFields(action, ACTION_FIELDS, "actions[" + index + "]", rejected);
            String id = normalize(string(action, "actionId", rejected));
            LlmToolDefinition tool = offered.get(id);
            if (tool == null) rejected.add("action was not offered: " + id);
            UUID target = uuid(action, "targetStableId", rejected);
            if (target != null && !targets.contains(target)) {
                rejected.add("action targetStableId is not grounded: " + target);
            }
            JsonObject parameters = object(action, "parameters", rejected);
            if (tool != null && parameters != null) validateParameters(parameters,
                    tool.function().parameters(), "actions[" + index + "].parameters", rejected);
            if (tool != null && actor != null && target != null && parameters != null) {
                result.add(new NpcDecisionAction(id, actor, target, parameters));
            }
        }
        return List.copyOf(result);
    }

    private static void validateParameters(JsonObject value, JsonObject schema, String path,
            List<String> rejected) {
        if (schema == null) return;
        JsonObject properties = schema.getAsJsonObject("properties");
        Set<String> allowed = properties == null ? Set.of() : properties.keySet();
        rejectUnknown(value, allowed, path, rejected);
        JsonArray required = schema.getAsJsonArray("required");
        if (required != null) for (JsonElement name : required) {
            String requiredName = name.getAsString();
            if (!value.has(requiredName)) rejected.add(path + " missing " + requiredName);
        }
        if (properties == null) return;
        for (Map.Entry<String, JsonElement> property : value.entrySet()) {
            JsonObject definition = properties.getAsJsonObject(property.getKey());
            String expected = definition == null || !definition.has("type")
                    ? "" : definition.get("type").getAsString();
            if (!matchesType(property.getValue(), expected)) {
                rejected.add(path + "." + property.getKey() + " must be " + expected);
            }
        }
    }

    private static boolean matchesType(JsonElement value, String expected) {
        if (value == null || value.isJsonNull()) return false;
        return switch (expected) {
            case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "integer" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    && Math.rint(value.getAsDouble()) == value.getAsDouble();
            case "object" -> value.isJsonObject();
            case "array" -> value.isJsonArray();
            default -> true;
        };
    }

    private static List<String> validateEvidence(JsonElement element, Set<String> allowed,
            List<String> rejected) {
        if (element == null || !element.isJsonArray()) {
            rejected.add("groundingEvidenceRefs must be an array");
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                rejected.add("groundingEvidenceRefs entries must be strings");
                continue;
            }
            String value = item.getAsString();
            if (!allowed.contains(value)) rejected.add("unrecognized evidence ref: " + value);
            else values.add(value);
        }
        return List.copyOf(values);
    }

    private static void requireFields(JsonObject object, Set<String> required, String path,
            List<String> rejected) {
        required.stream().filter(name -> !object.has(name))
                .forEach(name -> rejected.add(path + " missing " + name));
    }

    private static void rejectUnknown(JsonObject object, Set<String> allowed, String path,
            List<String> rejected) {
        object.keySet().stream().filter(name -> !allowed.contains(name))
                .forEach(name -> rejected.add(path + " contains unknown field " + name));
    }

    private static String string(JsonObject object, String name, List<String> rejected) {
        JsonElement value = object == null ? null : object.get(name);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            rejected.add(name + " must be a string");
            return null;
        }
        return value.getAsString().strip();
    }

    private static JsonObject object(JsonObject parent, String name, List<String> rejected) {
        JsonElement value = parent == null ? null : parent.get(name);
        if (value == null || !value.isJsonObject()) {
            rejected.add(name + " must be an object");
            return null;
        }
        return value.getAsJsonObject();
    }

    private static UUID uuid(JsonObject object, String name, List<String> rejected) {
        String value = string(object, name, rejected);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            rejected.add(name + " must be a UUID");
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonObject object, String name,
            Class<E> type, List<String> rejected) {
        String value = string(object, name, rejected);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            rejected.add("unknown " + name + ": " + value);
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("[\\s-]+", "_")
                .replaceAll("_+", "_").toUpperCase(java.util.Locale.ROOT);
    }

    private static String compact(String value) {
        String text = value == null ? "unknown parse failure"
                : value.replaceAll("\\s+", " ").strip();
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    public record Validation(boolean valid, String result,
            List<String> rejectedFieldsOrActions, NpcDecision decision,
            List<String> groundingValidation) {
        public Validation {
            rejectedFieldsOrActions = List.copyOf(
                    rejectedFieldsOrActions == null ? List.of() : rejectedFieldsOrActions);
            groundingValidation = List.copyOf(
                    groundingValidation == null ? List.of() : groundingValidation);
        }

        static Validation rejected(String result, List<String> reasons,
                List<String> groundingValidation) {
            return new Validation(false, result, reasons, null, groundingValidation);
        }
    }
}
