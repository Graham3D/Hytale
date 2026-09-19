package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.List;
import java.util.UUID;
import org.bson.BsonDocument;

/** Typed, profile-local authoring state for native NPC equipment and storage. */
public record NpcInventoryState(
        int schemaVersion,
        UUID stableNpcId,
        List<PersistedItemStack> armor,
        List<PersistedItemStack> loadout,
        List<PersistedItemStack> inventory,
        boolean infiniteAmmunition,
        boolean hideHelmet,
        boolean hideCuirass,
        boolean hideGauntlets,
        boolean hidePants) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final short ARMOR_CAPACITY = 4;
    public static final short LOADOUT_CAPACITY = 3;
    public static final short INVENTORY_CAPACITY = 40;

    public NpcInventoryState {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        armor = armor == null ? List.of() : List.copyOf(armor);
        loadout = loadout == null ? List.of() : List.copyOf(loadout);
        inventory = inventory == null ? List.of() : List.copyOf(inventory);
        validateSlots(armor, ARMOR_CAPACITY, "armor");
        validateSlots(loadout, LOADOUT_CAPACITY, "loadout");
        validateSlots(inventory, INVENTORY_CAPACITY, "inventory");
    }

    /** Backward-compatible constructor for schema-1 files and callers. */
    public NpcInventoryState(
            int schemaVersion,
            UUID stableNpcId,
            List<PersistedItemStack> armor,
            List<PersistedItemStack> loadout,
            List<PersistedItemStack> inventory,
            boolean infiniteAmmunition) {
        this(schemaVersion, stableNpcId, armor, loadout, inventory, infiniteAmmunition,
                false, false, false, false);
    }

    public static NpcInventoryState empty() {
        return new NpcInventoryState(CURRENT_SCHEMA_VERSION, null,
                List.of(), List.of(), List.of(), false, false, false, false, false);
    }

    public NpcInventoryState withStableNpcId(UUID stableId) {
        return new NpcInventoryState(schemaVersion, stableId, armor, loadout,
                inventory, infiniteAmmunition, hideHelmet, hideCuirass,
                hideGauntlets, hidePants);
    }

    public boolean armorHidden(short slot) {
        return switch (slot) {
            case 0 -> hideHelmet;
            case 1 -> hideCuirass;
            case 2 -> hideGauntlets;
            case 3 -> hidePants;
            default -> throw new IllegalArgumentException("Invalid armor slot: " + slot);
        };
    }

    private static void validateSlots(
            List<PersistedItemStack> values, short capacity, String section) {
        boolean[] occupied = new boolean[capacity];
        for (PersistedItemStack value : values) {
            if (value == null || value.slot() < 0 || value.slot() >= capacity) {
                throw new IllegalArgumentException("Invalid " + section + " slot in NPC inventory state");
            }
            if (occupied[value.slot()]) {
                throw new IllegalArgumentException("Duplicate " + section + " slot in NPC inventory state");
            }
            occupied[value.slot()] = true;
            if (value.itemId() == null || value.itemId().isBlank() || value.quantity() <= 0) {
                throw new IllegalArgumentException("Invalid item stack in NPC " + section);
            }
        }
    }

    /** Lossless ItemStack representation, including instance metadata and durability. */
    public record PersistedItemStack(
            short slot,
            String itemId,
            int quantity,
            double durability,
            double maxDurability,
            int qualityIndex,
            String metadataJson,
            boolean overrideDroppedItemAnimation) {
        public PersistedItemStack {
            metadataJson = canonicalMetadataJson(metadataJson);
        }

        /**
         * Canonical representation used by persistence, hydration and equality.
         * Only semantically empty BSON documents collapse to null; meaningful JSON
         * retains its original serialized representation.
         */
        public static String canonicalMetadataJson(String serialized) {
            if (serialized == null || serialized.isBlank()) return null;
            BsonDocument decoded = BsonDocument.parse(serialized);
            return decoded.isEmpty() ? null : serialized;
        }

        public static PersistedItemStack from(short slot, ItemStack stack) {
            if (ItemStack.isEmpty(stack)) throw new IllegalArgumentException("Cannot persist an empty item stack");
            BsonDocument metadata = stack.getMetadata();
            return new PersistedItemStack(slot, stack.getItemId(), stack.getQuantity(),
                    stack.getDurability(), stack.getMaxDurability(), stack.getQualityIndex(),
                    metadata == null || metadata.isEmpty() ? null : metadata.toJson(),
                    stack.getOverrideDroppedItemAnimation());
        }

        public ItemStack toItemStack() {
            BsonDocument metadata = decodeMetadata(metadataJson);
            ItemStack stack = new ItemStack(itemId, quantity, durability, maxDurability,
                    qualityIndex, metadata);
            stack.setOverrideDroppedItemAnimation(overrideDroppedItemAnimation);
            return stack;
        }

        private static BsonDocument decodeMetadata(String serialized) {
            String canonical = canonicalMetadataJson(serialized);
            return canonical == null ? null : BsonDocument.parse(canonical);
        }
    }
}
