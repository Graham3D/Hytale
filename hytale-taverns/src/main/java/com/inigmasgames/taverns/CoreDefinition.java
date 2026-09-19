package com.inigmasgames.taverns;

import java.util.Objects;

/** Data-driven rules shared by Core placement, resizing, cost calculation, and future assets. */
public record CoreDefinition(
        CoreType type,
        String itemId,
        String expansionItemId,
        int startingWidth,
        int startingDepth,
        int startingHeight,
        int blocksPerExpansionUnit,
        long maximumVolume) {

    public CoreDefinition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(expansionItemId, "expansionItemId");
        if (startingWidth <= 0 || startingDepth <= 0 || startingHeight <= 0) {
            throw new IllegalArgumentException("Core dimensions must be positive");
        }
        if (blocksPerExpansionUnit <= 0 || maximumVolume <= 0) {
            throw new IllegalArgumentException("Core expansion rules must be positive");
        }
    }

    public long startingVolume() {
        return (long) startingWidth * startingDepth * startingHeight;
    }

    public Cuboid startingBounds(int coreX, int coreY, int coreZ) {
        int halfWidth = startingWidth / 2;
        int halfDepth = startingDepth / 2;
        return Cuboid.normalized(
                coreX - halfWidth, coreY, coreZ - halfDepth,
                coreX + startingWidth - halfWidth - 1,
                coreY + startingHeight - 1,
                coreZ + startingDepth - halfDepth - 1);
    }

    public int expansionUnits(long volume) {
        long added = Math.max(0L, volume - startingVolume());
        return Math.toIntExact((added + blocksPerExpansionUnit - 1L) / blocksPerExpansionUnit);
    }
}
