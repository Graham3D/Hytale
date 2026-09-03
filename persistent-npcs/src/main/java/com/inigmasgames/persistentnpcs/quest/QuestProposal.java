package com.inigmasgames.persistentnpcs.quest;

/** Untrusted creative proposal. It intentionally contains no UUID or item-ID authority. */
public record QuestProposal(
        QuestType questType,
        String motivation,
        String storySummary,
        String requestedTarget,
        String requestedRewardKind,
        int estimatedDifficulty,
        int estimatedLength,
        int estimatedDanger) {

    public QuestProposal normalized() {
        if (questType == null) {
            throw new IllegalArgumentException("Quest proposal type is required");
        }
        return new QuestProposal(questType,
                text(motivation, "NPC has a grounded need"),
                text(storySummary, "A local request"),
                requestedTarget == null ? "" : requestedTarget.strip(),
                requestedRewardKind == null ? "" : requestedRewardKind.strip(),
                clamp(estimatedDifficulty), clamp(estimatedLength), clamp(estimatedDanger));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }
}
