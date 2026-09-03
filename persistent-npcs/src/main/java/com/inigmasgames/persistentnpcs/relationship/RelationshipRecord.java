package com.inigmasgames.persistentnpcs.relationship;

import java.time.Instant;
import java.util.UUID;

public record RelationshipRecord(
        UUID npcId,
        UUID playerId,
        int disposition,
        long interactionCount,
        Instant lastInteraction,
        Integer familiarity,
        Integer trust,
        Integer affection,
        Integer respect,
        Integer fear,
        Integer hostility,
        Integer obligation,
        String relationshipType,
        String description) {

    public RelationshipRecord(
            UUID npcId, UUID playerId, int disposition, long interactionCount,
            Instant lastInteraction, Integer familiarity, Integer trust,
            Integer affection, Integer respect, Integer fear, Integer hostility,
            Integer obligation) {
        this(npcId, playerId, disposition, interactionCount, lastInteraction, familiarity,
                trust, affection, respect, fear, hostility, obligation, "", "");
    }

    public RelationshipRecord(
            UUID npcId,
            UUID playerId,
            int disposition,
            long interactionCount,
            Instant lastInteraction) {
        this(npcId, playerId, disposition, interactionCount, lastInteraction,
                0, disposition, 0, 0, 0, Math.max(0, -disposition), 0);
    }

    public RelationshipRecord normalized() {
        return new RelationshipRecord(npcId, playerId, clamp(disposition),
                Math.max(0, interactionCount), lastInteraction,
                clamp(value(familiarity)), clamp(value(trust)), clamp(value(affection)),
                clamp(value(respect)), clamp(value(fear)), clamp(value(hostility)),
                clamp(value(obligation)), clean(relationshipType, 60),
                clean(description, 600));
    }

    public String naturalSummary(String entityName) {
        String familiarityText = value(familiarity) >= 50 ? "knows well"
                : value(familiarity) >= 15 ? "knows somewhat" : "barely knows";
        String kind = relationshipType == null || relationshipType.isBlank() ? ""
                : " (" + relationshipType.toLowerCase(java.util.Locale.ROOT)
                        .replace('_', ' ') + ")";
        return familiarityText + " " + entityName + kind + "; trust=" + value(trust)
                + ", affection=" + value(affection) + ", respect=" + value(respect)
                + ", fear=" + value(fear) + ", hostility=" + value(hostility)
                + ", obligation=" + value(obligation) + ".";
    }

    public boolean knowsEntity() {
        return value(familiarity) >= 15 || interactionCount > 0
                || Math.abs(value(trust)) >= 10 || Math.abs(value(affection)) >= 10
                || Math.abs(value(respect)) >= 10 || Math.abs(value(fear)) >= 10
                || Math.abs(value(hostility)) >= 10
                || relationshipType != null && !relationshipType.isBlank();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static int clamp(int value) {
        return Math.max(-100, Math.min(100, value));
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
