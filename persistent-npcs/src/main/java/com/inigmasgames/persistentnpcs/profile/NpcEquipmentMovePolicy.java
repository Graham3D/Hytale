package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.inigmasgames.persistentnpcs.ui.CustomInventoryTransactionBridge;
import java.util.function.Supplier;

/** Semantic allowlist for A3 storage/equipment native transactions. */
public final class NpcEquipmentMovePolicy
        implements CustomInventoryTransactionBridge.MovePolicy {
    private final NpcEquipmentCompatibilityResolver compatibility =
            new NpcEquipmentCompatibilityResolver();
    private final Supplier<ItemStack> currentPrimary;

    public NpcEquipmentMovePolicy() {
        this(() -> ItemStack.EMPTY);
    }

    public NpcEquipmentMovePolicy(Supplier<ItemStack> currentPrimary) {
        this.currentPrimary = currentPrimary == null ? () -> ItemStack.EMPTY : currentPrimary;
    }

    @Override
    public String invalidReason(CustomInventoryTransactionBridge.Endpoint source,
            ItemStack sourceStack, CustomInventoryTransactionBridge.Endpoint target,
            ItemStack targetStack, boolean swap) {
        GearSlot sourceGear = gearSlot(source);
        GearSlot targetGear = gearSlot(target);
        boolean sourceStorage = isStorage(source);
        boolean targetStorage = isStorage(target);

        if (sourceStorage && targetStorage) return null;
        if (sourceGear != null && targetGear != null) {
            return "DIRECT_EQUIPMENT_TO_EQUIPMENT_NOT_ENABLED";
        }
        if (sourceStorage && targetGear != null) {
            return rejection(validate(targetGear, sourceStack,
                    currentPrimary.get()));
        }
        if (sourceGear != null && targetStorage) {
            return swap ? rejection(validate(sourceGear, targetStack,
                    currentPrimary.get())) : null;
        }
        return "ENDPOINT_GRAPH_NOT_ALLOWED";
    }

    private NpcEquipmentCompatibilityResolver.Verdict validate(
            GearSlot slot, ItemStack stack, ItemStack primary) {
        return switch (slot.kind) {
            case ARMOR -> compatibility.validateArmor(stack, slot.slot);
            case PRIMARY -> compatibility.validatePrimaryWeapon(stack);
            case OFFHAND -> compatibility.validateOffhand(stack, primary);
            case AMMUNITION -> compatibility.validateAmmunition(stack, primary);
        };
    }

    private static String rejection(NpcEquipmentCompatibilityResolver.Verdict verdict) {
        if (verdict.compatible()) return null;
        return verdict.status() + "_" + safe(verdict.reason())
                + "_EVIDENCE_" + safe(verdict.evidence());
    }

    private static boolean isStorage(CustomInventoryTransactionBridge.Endpoint endpoint) {
        return endpoint.role() == CustomInventoryTransactionBridge.SectionRole.PLAYER_STORAGE
                || endpoint.role() == CustomInventoryTransactionBridge.SectionRole.NPC_STORAGE;
    }

    private static GearSlot gearSlot(CustomInventoryTransactionBridge.Endpoint endpoint) {
        return switch (endpoint.role()) {
            case NPC_ARMOR -> endpoint.slotId() >= 0 && endpoint.slotId() < 4
                    ? new GearSlot(GearKind.ARMOR, (short) endpoint.slotId()) : null;
            case NPC_HOTBAR -> endpoint.slotId() == 0
                    ? new GearSlot(GearKind.PRIMARY, (short) 0)
                    : endpoint.slotId() == 1
                            ? new GearSlot(GearKind.AMMUNITION, (short) 1) : null;
            case NPC_UTILITY -> endpoint.slotId() == 0
                    ? new GearSlot(GearKind.OFFHAND, (short) 0) : null;
            default -> null;
        };
    }

    private static String safe(String value) {
        return value == null ? "UNKNOWN"
                : value.replaceAll("[^A-Za-z0-9_.-]+", "_");
    }

    private enum GearKind { ARMOR, PRIMARY, OFFHAND, AMMUNITION }
    private record GearSlot(GearKind kind, short slot) { }
}
