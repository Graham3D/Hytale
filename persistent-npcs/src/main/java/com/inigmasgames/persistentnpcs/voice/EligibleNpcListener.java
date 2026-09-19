package com.inigmasgames.persistentnpcs.voice;

import java.util.UUID;

/** Semantic listener result. Raw entity IDs and coordinates never enter dialogue prompts. */
public record EligibleNpcListener(
        UUID npcId,
        String npcName,
        double distanceMeters,
        String distanceBand,
        String directionFromPlayer,
        UtteranceRangeClass rangeClass,
        boolean directAddress,
        boolean activeConversationPartner,
        double attentionScore) {

    public EligibleNpcListener {
        npcName = clean(npcName, 80, "unknown NPC");
        distanceMeters = Math.max(0.0, distanceMeters);
        distanceBand = clean(distanceBand, 80, "nearby");
        directionFromPlayer = clean(directionFromPlayer, 40, "nearby");
        rangeClass = rangeClass == null ? UtteranceRangeClass.ORDINARY : rangeClass;
        attentionScore = Double.isFinite(attentionScore) ? attentionScore : 0.0;
    }

    private static String clean(String value, int maximum, String fallback) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        if (text.isBlank()) text = fallback;
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
