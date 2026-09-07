package com.inigmasgames.hytalerpg.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Deterministic, one-version-at-a-time migration pipeline. */
public final class RpgStateMigrator {
    public MigrationResult migrate(JsonObject source) {
        JsonObject state = source.deepCopy();
        int original = state.has("schemaVersion") ? state.get("schemaVersion").getAsInt() : 1;
        int version = original;
        while (version < RpgPlayerState.CURRENT_SCHEMA) {
            state = switch (version) {
                case 1 -> migrateV1ToV2(state);
                case 2 -> migrateV2ToV3(state);
                default -> throw new IllegalStateException("No migration from RPG schema v" + version);
            };
            version = state.get("schemaVersion").getAsInt();
        }
        if (version > RpgPlayerState.CURRENT_SCHEMA) {
            throw new IllegalStateException("RPG state schema v" + version + " is newer than supported v" + RpgPlayerState.CURRENT_SCHEMA);
        }
        return new MigrationResult(state, original, version, original != version);
    }

    private static JsonObject migrateV1ToV2(JsonObject state) {
        rename(state, "playerUUID", "playerUuid");
        rename(state, "characterLevel", "level");
        rename(state, "totalCharacterXP", "currentXp");
        rename(state, "equippedSkillNodes", "equippedSkills");
        rename(state, "passiveNodes", "equippedPassives");
        if (!state.has("pendingLevelUpPoints")) state.addProperty("pendingLevelUpPoints", 0);
        if (!state.has("joints")) {
            JsonArray joints = new JsonArray(); joints.add("joint01"); joints.add("joint02"); state.add("joints", joints);
        }
        if (!state.has("graphEdges")) state.add("graphEdges", new JsonArray());
        if (!state.has("skillMastery")) state.add("skillMastery", new JsonObject());
        if (!state.has("degradedReasons")) state.add("degradedReasons", new JsonArray());
        state.addProperty("schemaVersion", 2);
        return state;
    }

    /** Removes the obsolete fourth equipped slot without deleting player-owned progression. */
    private static JsonObject migrateV2ToV3(JsonObject state) {
        JsonArray skills = state.has("equippedSkills") && state.get("equippedSkills").isJsonArray()
                ? state.getAsJsonArray("equippedSkills") : new JsonArray();
        String legacySkill = skills.size() > 3 && !skills.get(3).isJsonNull() ? skills.get(3).getAsString() : "";
        JsonArray retained = new JsonArray();
        for (int index = 0; index < 3; index++) retained.add(index < skills.size() ? skills.get(index) : null);
        state.add("equippedSkills", retained);

        int removedEdges = 0;
        JsonArray retainedEdges = new JsonArray();
        if (state.has("graphEdges") && state.get("graphEdges").isJsonArray()) {
            for (JsonElement element : state.getAsJsonArray("graphEdges")) {
                JsonObject edge = element.getAsJsonObject();
                String source = edge.has("sourceNodeId") ? edge.get("sourceNodeId").getAsString() : "";
                String target = edge.has("targetNodeId") ? edge.get("targetNodeId").getAsString() : "";
                if ("skill04".equalsIgnoreCase(source) || "skill04".equalsIgnoreCase(target)) removedEdges++;
                else retainedEdges.add(element);
            }
        }
        state.add("graphEdges", retainedEdges);
        JsonArray reasons = state.has("degradedReasons") && state.get("degradedReasons").isJsonArray()
                ? state.getAsJsonArray("degradedReasons") : new JsonArray();
        String detail = "MIGRATION_V2_TO_V3:removed skill04";
        if (!legacySkill.isBlank()) detail += " equipped=" + legacySkill + " (learned/mastery preserved)";
        detail += " removedEdges=" + removedEdges;
        reasons.add(detail);
        state.add("degradedReasons", reasons);
        state.addProperty("schemaVersion", 3);
        return state;
    }

    private static void rename(JsonObject state, String from, String to) {
        if (!state.has(to) && state.has(from)) {
            JsonElement value = state.remove(from);
            state.add(to, value);
        }
    }

    public record MigrationResult(JsonObject state, int sourceVersion, int targetVersion, boolean migrated) {}
}
