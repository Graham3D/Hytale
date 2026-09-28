package com.inigmasgames.hytalerpg.difficulty;

/** Stable persisted identifiers; recommended levels are never admission gates. */
public enum DifficultyId {
    NORMAL(1), NIGHTMARE(40), HELL(60);
    private final int recommendedLevel;
    DifficultyId(int recommendedLevel) { this.recommendedLevel = recommendedLevel; }
    public int recommendedLevel() { return recommendedLevel; }
}
