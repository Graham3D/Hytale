package com.inigmasgames.persistentnpcs.conversation;

import java.util.List;
import java.util.regex.Pattern;

/** Fail-closed lexical guard at the canonical chunk boundary. It never rewrites wording. */
public final class SpokenTextSafetyValidator {
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern COORDINATE_TRIPLE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])[-+]?\\d+(?:\\.\\d+)?\\s*,\\s*[-+]?\\d+(?:\\.\\d+)?\\s*,\\s*[-+]?\\d+(?:\\.\\d+)?(?![\\p{L}\\p{N}])");
    private static final Pattern ISO_TIMESTAMP = Pattern.compile(
            "(?i)\\[?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z(?:\\]|:)?");
    private static final Pattern INTERNAL_ENUM = Pattern.compile(
            "\\b[A-Z][A-Z0-9]+(?:_[A-Z0-9]+)+\\b");
    private static final Pattern FIELD_SYNTAX = Pattern.compile(
            "(?i)\\b(?:responseId|entityId|npcId|worldId|sourceEntityId|beliefId|memoryId|"
                    + "samples|sampleCount|scanRadius|scanDurationMs|snapshotAgeMs|"
                    + "lineOfSight|rayResult|sensorName|blockId|assetId)\\s*[=:]");
    private static final Pattern INTERNAL_NARRATION = Pattern.compile(
            "(?i)(?:player[- ](?:asserted|reported|provided) (?:fact|belief|claim)|"
                    + "belief updates?|grounded decision|selected intent|response plan|"
                    + "private appraisal|relevant memories|grounding evidence|"
                    + "status:\\s*(?:player|npc)|no new actionable request(?: received)?)");
    private static final List<String> INTERNAL_TYPES = List.of(
            "NpcPerceptionSnapshot", "RawPerceptionSnapshot", "SemanticWorldModel",
            "PositionCache", "Blackboard", "EntityRef", "TransformComponent",
            "WorldChunk", "EnvironmentSample", "GroundedNpcDecision", "AgentOperation");

    private SpokenTextSafetyValidator() { }

    public static String requireSafe(String text) {
        String value = text == null ? "" : text.strip();
        String reason = rejectionReason(value);
        if (reason != null) {
            throw new InvalidDialogueException("Rejected implementation/debug leakage: " + reason);
        }
        return value;
    }

    public static boolean isSafe(String text) {
        return rejectionReason(text == null ? "" : text) == null;
    }

    public static String rejectionReason(String text) {
        if (UUID.matcher(text).find()) return "UUID/entity reference";
        if (COORDINATE_TRIPLE.matcher(text).find()) return "raw coordinate tuple";
        if (ISO_TIMESTAMP.matcher(text).find()) return "timestamped internal record";
        if (FIELD_SYNTAX.matcher(text).find()) return "diagnostic field syntax";
        if (INTERNAL_ENUM.matcher(text).find()) return "internal enum identifier";
        if (INTERNAL_NARRATION.matcher(text).find()) return "internal provenance/status narration";
        for (String type : INTERNAL_TYPES) {
            if (text.contains(type)) return "internal class or sensor name";
        }
        return null;
    }
}
