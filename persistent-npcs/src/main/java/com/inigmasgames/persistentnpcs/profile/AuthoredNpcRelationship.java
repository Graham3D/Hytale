package com.inigmasgames.persistentnpcs.profile;

import java.util.Locale;

/** Optional profile-authored NPC-to-NPC relationship metadata. */
public record AuthoredNpcRelationship(
        String targetId,
        String targetName,
        String relationship,
        Double familiarity,
        Double affection,
        Double trust,
        Double respect,
        Double fear,
        Double resentment,
        Double obligation,
        String description) {

    public AuthoredNpcRelationship normalized() {
        return new AuthoredNpcRelationship(clean(targetId, 80), clean(targetName, 80),
                clean(relationship, 60).toUpperCase(Locale.ROOT), bounded(familiarity),
                bounded(affection), bounded(trust), bounded(respect), bounded(fear),
                bounded(resentment), bounded(obligation), clean(description, 600));
    }

    public boolean identifiesTarget() {
        return !targetId.isBlank() || !targetName.isBlank();
    }

    private static Double bounded(Double value) {
        if (value == null || !Double.isFinite(value)) return null;
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
