package com.inigmasgames.persistentnpcs.quest;

public record RewardCandidate(
        String itemId,
        String displayName,
        int unitValue,
        int maximumQuantity,
        String source,
        boolean issuerActuallyOwnsItem) {

    public RewardCandidate normalized() {
        if (itemId == null || itemId.isBlank() || unitValue < 1 || maximumQuantity < 1) {
            throw new IllegalArgumentException("Invalid configured reward candidate");
        }
        return new RewardCandidate(itemId.strip(),
                displayName == null || displayName.isBlank() ? itemId.strip() : displayName.strip(),
                unitValue, maximumQuantity,
                source == null ? "CONFIGURED_POOL" : source.strip(), issuerActuallyOwnsItem);
    }
}
