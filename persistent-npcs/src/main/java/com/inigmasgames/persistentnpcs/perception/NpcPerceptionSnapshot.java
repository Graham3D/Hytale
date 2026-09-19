package com.inigmasgames.persistentnpcs.perception;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NpcPerceptionSnapshot(
        UUID npcId,
        UUID npcEntityId,
        UUID worldId,
        LocalDateTime gameTime,
        double x,
        double y,
        double z,
        List<PerceivedEntity> nearbyPlayers,
        List<PerceivedEntity> nearbyNpcs,
        List<PerceivedEntity> nearbyHostiles,
        List<PerceivedItem> nearbyItems,
        List<PerceivedEntity> nearbyInteractables,
        List<PerceivedEntity> nearbyCraftingStations,
        Integer focusedPlayerHotbarSlot,
        PerceivedItem focusedPlayerHeldItem,
        List<PerceivedItem> npcInventory,
        EnvironmentSnapshot environment) {

    /** Backward-compatible constructor for detached tests and API consumers. */
    public NpcPerceptionSnapshot(
            UUID npcId,
            UUID npcEntityId,
            UUID worldId,
            LocalDateTime gameTime,
            double x,
            double y,
            double z,
            List<PerceivedEntity> nearbyPlayers,
            List<PerceivedEntity> nearbyNpcs,
            List<PerceivedEntity> nearbyHostiles,
            List<PerceivedItem> nearbyItems,
            List<PerceivedEntity> nearbyInteractables,
            List<PerceivedEntity> nearbyCraftingStations,
            Integer focusedPlayerHotbarSlot,
            PerceivedItem focusedPlayerHeldItem,
            List<PerceivedItem> npcInventory) {
        this(npcId, npcEntityId, worldId, gameTime, x, y, z, nearbyPlayers, nearbyNpcs,
                nearbyHostiles, nearbyItems, nearbyInteractables, nearbyCraftingStations,
                focusedPlayerHotbarSlot, focusedPlayerHeldItem, npcInventory,
                EnvironmentSnapshot.unavailable(worldId, x, y, z));
    }

    public static NpcPerceptionSnapshot unavailable(UUID npcId) {
        return new NpcPerceptionSnapshot(npcId, null, null, null, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, List.of(), EnvironmentSnapshot.unavailable(null, 0, 0, 0));
    }

    public String heldItemFacts() {
        if (focusedPlayerHeldItem == null) {
            return "HELD_ITEM: NONE";
        }
        return """
                HELD_ITEM:
                  id=%s
                  displayName=%s
                  quantity=%d
                """.formatted(focusedPlayerHeldItem.itemId(),
                focusedPlayerHeldItem.displayName(), focusedPlayerHeldItem.quantity()).strip();
    }

    public String heldItemContextId() {
        return focusedPlayerHeldItem == null ? "NONE" : focusedPlayerHeldItem.itemId();
    }

    public String facts() {
        return facts(true);
    }

    public String facts(boolean includeHeldItem) {
        String time = gameTime == null ? "unknown" : "%02d:%02d".formatted(
                gameTime.getHour(), gameTime.getMinute());
        String environmentFacts = environment == null
                ? EnvironmentSnapshot.unavailable(worldId, x, y, z).semanticBlock()
                : environment.semanticBlock();
        return """
                %s

                CURRENT ENTITY/ITEM PERCEPTION (server-authoritative facts):
                - Game time: %s
                - NPC position: %.1f, %.1f, %.1f
                - Nearby players: %s
                - Nearby NPCs: %s
                - Nearby hostiles: %s
                - Nearby dropped items: %s
                - Nearby interactables: %s
                - Nearby crafting stations: %s
                - Selected hotbar slot: %s
                - Focused player's held ItemStack: %s
                - NPC inventory: %s
                """.formatted(environmentFacts, time, x, y, z,
                entities(nearbyPlayers), entities(nearbyNpcs),
                entities(nearbyHostiles), items(nearbyItems), entities(nearbyInteractables),
                entities(nearbyCraftingStations),
                focusedPlayerHotbarSlot == null ? "unknown" : focusedPlayerHotbarSlot,
                !includeHeldItem ? "omitted as irrelevant to this turn"
                        : focusedPlayerHeldItem == null ? "none"
                        : focusedPlayerHeldItem.compact(),
                items(npcInventory));
    }

    private static String entities(List<PerceivedEntity> values) {
        return values.isEmpty() ? "none" : values.stream()
                .map(value -> value.name() + " [" + value.kind() + ", "
                        + "%.1fm".formatted(value.distanceMeters()) + "]")
                .toList().toString();
    }

    private static String items(List<PerceivedItem> values) {
        return values.isEmpty() ? "none" : values.stream()
                .map(PerceivedItem::compact).toList().toString();
    }
}
