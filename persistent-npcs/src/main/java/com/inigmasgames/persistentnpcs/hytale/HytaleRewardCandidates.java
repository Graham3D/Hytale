package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.inigmasgames.persistentnpcs.quest.RewardCandidate;
import java.util.Comparator;
import java.util.List;

/** Builds a bounded reward pool solely from loaded Hytale item assets. */
public final class HytaleRewardCandidates {
    private HytaleRewardCandidates() { }

    public static List<RewardCandidate> loadedPool() {
        try {
            return Item.getAssetMap().getAssetMap().values().stream()
                    .filter(item -> item != null && item != Item.UNKNOWN && !item.isVariant()
                            && item.getId() != null && !item.getId().isBlank()
                            && (item.isConsumable() || item.getMaxStack() > 1))
                    .sorted(Comparator.comparing(Item::getId))
                    .limit(32)
                    .map(item -> new RewardCandidate(item.getId(), item.getId(),
                            Math.max(1, Math.min(20, item.getItemLevel() + 1)),
                            Math.max(1, Math.min(3, item.getMaxStack())),
                            "HYTALE_ITEM_REGISTRY_POOL", false))
                    .toList();
        } catch (RuntimeException | LinkageError failure) {
            return List.of();
        }
    }
}
