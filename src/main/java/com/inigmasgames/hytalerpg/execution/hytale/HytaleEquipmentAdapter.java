package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import java.util.Locale;
import java.util.Set;

/** Audits authoritative equipped item IDs and Hytale-authored basic damage data. */
public final class HytaleEquipmentAdapter {
    public SkillExecutionPort.Equipment read(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor) {
        ItemStack main = InventoryComponent.getItemInHand(accessor, actor);
        InventoryComponent.Utility utility = accessor.getComponent(actor, InventoryComponent.Utility.getComponentType());
        ItemStack off = utility == null ? null : utility.getActiveItem();
        return new SkillExecutionPort.Equipment(item(main), item(off));
    }

    private static SkillExecutionPort.Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isValid()) return null;
        String kind = kind(stack.getItemId());
        Set<String> tags = switch (kind) {
            case "SWORD", "DAGGER", "BOW", "CROSSBOW" -> Set.of("RPG_WEAPON_LIGHT");
            case "LONGSWORD", "MACE", "BATTLEAXE", "SHIELD" -> Set.of("RPG_WEAPON_HEAVY");
            case "STAFF", "WAND", "SPELLBOOK" -> Set.of("RPG_WEAPON_MAGIC");
            default -> Set.of();
        };
        Double power = null;
        var item = stack.getItem();
        if (item != null && item.getWeapon() != null && item.getWeapon().getBasicDamageBreakdown() != null
                && !item.getWeapon().getBasicDamageBreakdown().entries().isEmpty()) {
            power = item.getWeapon().getBasicDamageBreakdown().entries().stream()
                    .mapToDouble(entry -> (entry.min() + entry.max()) * 0.5).sum();
        }
        boolean magic = kind.equals("STAFF") || kind.equals("WAND") || kind.equals("SPELLBOOK");
        return new SkillExecutionPort.Item(stack.getItemId(), kind,
                new ItemPowerDescriptor(stack.getItemId(), tags, magic ? null : power, magic ? power : null));
    }

    static String kind(String itemId) {
        if (itemId == null) return "UNKNOWN";
        String id = itemId.toUpperCase(Locale.ROOT);
        if (id.contains("LONGSWORD")) return "LONGSWORD";
        if (id.contains("CROSSBOW")) return "CROSSBOW";
        if (id.contains("SHORTBOW") || id.contains("BOW")) return "BOW";
        if (id.contains("BATTLEAXE")) return "BATTLEAXE";
        if (id.contains("SPELLBOOK")) return "SPELLBOOK";
        if (id.contains("STAFF")) return "STAFF";
        if (id.contains("WAND")) return "WAND";
        if (id.contains("DAGGER")) return "DAGGER";
        if (id.contains("SHIELD")) return "SHIELD";
        if (id.contains("MACE")) return "MACE";
        if (id.contains("SWORD")) return "SWORD";
        return "UNKNOWN";
    }
}
