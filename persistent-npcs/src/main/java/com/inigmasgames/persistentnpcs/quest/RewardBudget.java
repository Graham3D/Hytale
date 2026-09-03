package com.inigmasgames.persistentnpcs.quest;

public final class RewardBudget {
    private final int basePoints;
    private final int maximumPoints;

    public RewardBudget(int basePoints, int maximumPoints) {
        this.basePoints = Math.max(1, basePoints);
        this.maximumPoints = Math.max(this.basePoints, maximumPoints);
    }

    public int calculate(QuestProposal proposal) {
        QuestProposal value = proposal.normalized();
        int points = basePoints + value.estimatedDifficulty() * 2
                + value.estimatedLength() + value.estimatedDanger() * 2;
        return Math.min(maximumPoints, points);
    }
}
