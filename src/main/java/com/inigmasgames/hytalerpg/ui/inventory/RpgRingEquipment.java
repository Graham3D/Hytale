package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonString;
import java.util.Objects;
import java.util.UUID;

/** Two immutable ring custodians persisted with the spatial bag component. */
public record RpgRingEquipment(Slot left, Slot right) {
    public record Slot(UUID entryId, String payloadJson) {
        public Slot {
            Objects.requireNonNull(entryId);
            Objects.requireNonNull(payloadJson);
            if (!eligible(ItemStack.CODEC.decode(BsonDocument.parse(payloadJson), new ExtraInfo())))
                throw new IllegalArgumentException("Invalid ring payload");
        }
        public ItemStack payload() { return ItemStack.CODEC.decode(BsonDocument.parse(payloadJson), new ExtraInfo()); }
        static Slot of(UUID id, ItemStack item) {
            return new Slot(id, ItemStack.CODEC.encode(item, new ExtraInfo()).asDocument().toJson());
        }
    }
    static boolean eligible(ItemStack stack) {
        return !ItemStack.isEmpty(stack) && stack.getQuantity() == 1
                && "RPG_Ring_Copper".equals(stack.getItemId());
    }
    public Slot get(String side) {
        return switch (side) { case "left" -> left; case "right" -> right;
            default -> throw new IllegalArgumentException("Unknown ring slot"); };
    }
    RpgRingEquipment with(String side, Slot slot) {
        return switch (side) { case "left" -> new RpgRingEquipment(slot, right);
            case "right" -> new RpgRingEquipment(left, slot);
            default -> throw new IllegalArgumentException("Unknown ring slot"); };
    }
    BsonDocument toBson() {
        var root = new BsonDocument();
        if (left != null) root.append("Left", encode(left));
        if (right != null) root.append("Right", encode(right));
        return root;
    }
    static RpgRingEquipment fromBson(BsonDocument root) {
        if (root == null) return new RpgRingEquipment(null, null);
        var result = new RpgRingEquipment(decode(root.getDocument("Left", null)),
                decode(root.getDocument("Right", null)));
        if (result.left != null && result.right != null && result.left.entryId().equals(result.right.entryId()))
            throw new IllegalStateException("Duplicate ring entry");
        return result;
    }
    private static BsonDocument encode(Slot slot) {
        return new BsonDocument("Id", new BsonString(slot.entryId().toString()))
                .append("Payload", BsonDocument.parse(slot.payloadJson()));
    }
    private static Slot decode(BsonDocument value) {
        return value == null ? null : new Slot(UUID.fromString(value.getString("Id").getValue()),
                value.getDocument("Payload").toJson());
    }
}
