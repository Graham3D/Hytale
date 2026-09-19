package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.cognition.NpcEmotion;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionalState;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.Locale;

/** Profile-driven scoring; there are no NPC-name branches. */
public final class AttentionScorer {
    public AttentionScore score(NpcProfile profile, NpcEmotionalState mood,
            GroundedStimulus fact, boolean repeated, int activeObligations) {
        String identity = (fact.semanticType() + " " + fact.assetId())
                .toLowerCase(Locale.ROOT);
        double curiosity = profile.curiosity() == null ? 0.65 : profile.curiosity();
        double personality = curiosity * 0.28 + match(profile.likes(), identity) * 0.12
                - match(profile.dislikes(), identity) * 0.18;
        double goals = match(profile.goals(), identity) * 0.16
                + match(profile.knowledgeDomains(), identity) * 0.08;
        double novelty = repeated ? 0.0 : 0.18;
        double moodScore = mood == null ? 0.04 : switch (mood.emotion()) {
            case CURIOUS, AMUSED, EXCITED -> 0.10 * (0.5 + mood.intensity());
            case AFRAID, ANGRY, UNEASY -> -0.12 * (0.5 + mood.intensity());
            default -> 0.04;
        };
        double proximity = Math.max(0, 1.0 - fact.distanceMeters() / 12.0) * 0.16;
        double obligationPenalty = Math.min(0.24, activeObligations * 0.08);
        double danger = fact.semanticType().equals("DANGER")
                || fact.semanticType().equals("STORM") ? 0.30 : 0.0;
        double repetition = repeated ? 0.35 : 0.0;
        double total = clamp(0.20 + personality + goals + novelty + moodScore
                + proximity + danger - obligationPenalty - repetition);
        return new AttentionScore(total, personality, goals, novelty, moodScore,
                proximity, obligationPenalty, danger, repetition);
    }

    private static double match(java.util.List<String> values, String identity) {
        if (values == null || values.isEmpty()) return 0;
        return values.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> words(value).stream().anyMatch(identity::contains)) ? 1 : 0;
    }

    private static java.util.Set<String> words(String value) {
        return java.util.Arrays.stream(value.split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 3)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
