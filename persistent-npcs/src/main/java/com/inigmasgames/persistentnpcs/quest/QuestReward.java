package com.inigmasgames.persistentnpcs.quest;

public record QuestReward(
        String itemId,
        String displayName,
        int quantity,
        int budgetCost,
        String source) {

    public QuestReward normalized() {
        if (itemId == null || itemId.isBlank() || quantity < 1 || budgetCost < 0) {
            throw new IllegalArgumentException("Quest reward must be a validated item and quantity");
        }
        return new QuestReward(itemId.strip(),
                displayName == null || displayName.isBlank() ? itemId.strip() : displayName.strip(),
                quantity, budgetCost, source == null ? "CONFIGURED_POOL" : source.strip());
    }
}
