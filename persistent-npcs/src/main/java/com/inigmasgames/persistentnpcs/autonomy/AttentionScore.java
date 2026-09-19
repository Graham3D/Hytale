package com.inigmasgames.persistentnpcs.autonomy;

/** Explainable deterministic attention score. */
public record AttentionScore(
        double total,
        double personality,
        double goals,
        double novelty,
        double mood,
        double proximity,
        double obligations,
        double danger,
        double repetitionPenalty) {

    public String compact() {
        return "total=%.2f personality=%.2f goals=%.2f novelty=%.2f mood=%.2f "
                .formatted(total, personality, goals, novelty, mood)
                + "distance=%.2f obligations=%.2f danger=%.2f repetition=-%.2f"
                .formatted(proximity, obligations, danger, repetitionPenalty);
    }
}
