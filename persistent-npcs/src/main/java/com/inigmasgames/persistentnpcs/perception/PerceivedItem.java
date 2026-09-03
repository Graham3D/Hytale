package com.inigmasgames.persistentnpcs.perception;

import java.util.UUID;

public record PerceivedItem(
        UUID entityId,
        String itemId,
        String displayName,
        int quantity,
        double durability,
        double maxDurability,
        String metadata,
        double distanceMeters) {

    public String compact() {
        if (itemId == null || itemId.isBlank()) {
            return "none";
        }
        String condition = maxDurability > 0
                ? ", durability=" + Math.round(durability) + "/" + Math.round(maxDurability)
                : "";
        String entity = entityId == null ? "" : ", entityId=" + entityId
                + ", distance=" + "%.1fm".formatted(distanceMeters);
        String name = displayName == null || displayName.isBlank()
                ? itemId : displayName;
        return "itemId=" + itemId + ", displayName=" + name
                + ", quantity=" + quantity + condition + entity
                + (metadata == null || metadata.isBlank() || metadata.equals("{}")
                        ? "" : ", metadata=" + metadata);
    }
}
