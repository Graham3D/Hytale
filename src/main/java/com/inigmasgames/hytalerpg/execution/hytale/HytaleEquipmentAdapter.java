package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Audits authoritative equipped item IDs and Hytale-authored basic damage data. */
public final class HytaleEquipmentAdapter {
    private static final com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry POWERS=
            com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadCanonical();
    public SkillExecutionPort.Equipment read(Ref<EntityStore> actor, ComponentAccessor<EntityStore> accessor) {
        ItemStack main = InventoryComponent.getItemInHand(accessor, actor);
        InventoryComponent.Utility utility = accessor.getComponent(actor, InventoryComponent.Utility.getComponentType());
        ItemStack off = utility == null ? null : utility.getActiveItem();
        return new SkillExecutionPort.Equipment(item(main), item(off));
    }

    private static SkillExecutionPort.Item item(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isValid()) return null;
        var item = stack.getItem();
        var nativeTags=item == null || item.getData() == null ? Map.<String,String[]>of() : item.getData().getRawTags();
        var registered=POWERS.resolve(stack.getItemId(),nativeTags);
        if(registered.isPresent())return new SkillExecutionPort.Item(stack.getItemId(),registered.get().kind(),registered.get().descriptor());
        // Unknown production items have no power. The old explicitly audited bomb/development registry
        // can still supply a descriptor downstream; no min/max or name heuristic is used here.
        String kind = POWERS.find(stack.getItemId()).isPresent()?"UNKNOWN":nativeKind(nativeTags);
        Set<String> tags = weaponTags(kind);
        return new SkillExecutionPort.Item(stack.getItemId(), kind,
                new ItemPowerDescriptor(stack.getItemId(), tags, null, null));
    }

    public static Set<String> weaponTags(String kind) {
        return switch (kind) {
            case "SWORD", "DAGGER", "BOW", "CROSSBOW", "BOMB", "GUN" -> Set.of("RPG_WEAPON_LIGHT");
            case "LONGSWORD", "MACE", "BATTLEAXE", "SHIELD", "SPEAR" -> Set.of("RPG_WEAPON_HEAVY");
            case "STAFF", "WAND", "SPELLBOOK" -> Set.of("RPG_WEAPON_MAGIC");
            default -> Set.of();
        };
    }
    /** Diagnostic only: the native breakdown may contain charged/signature damage. Not an RPG base. */
    static Double damageSummaryMeanForAudit(com.hypixel.hytale.server.core.asset.type.item.config.Item item) {
        if (item != null && item.getWeapon() != null && item.getWeapon().getBasicDamageBreakdown() != null
                && !item.getWeapon().getBasicDamageBreakdown().entries().isEmpty()) {
            double power = item.getWeapon().getBasicDamageBreakdown().entries().stream()
                    .mapToDouble(entry -> (entry.min() + entry.max()) * 0.5).sum();
            return Double.isFinite(power) && power > 0 ? power : null;
        }
        return null;
    }

    private static final Map<String,String> NATIVE_FAMILIES = Map.ofEntries(
            Map.entry("Sword","SWORD"),Map.entry("Longsword","LONGSWORD"),Map.entry("Dagger","DAGGER"),
            Map.entry("Bow","BOW"),Map.entry("Crossbow","CROSSBOW"),Map.entry("Axe","BATTLEAXE"),
            Map.entry("Mace","MACE"),Map.entry("Shield","SHIELD"),Map.entry("Spear","SPEAR"),
            Map.entry("Gun","GUN"),Map.entry("Bomb","BOMB"),Map.entry("Staff","STAFF"),
            Map.entry("Wand","WAND"),Map.entry("Spellbook","SPELLBOOK"));
    /** Only the resolved installed Family tags are authoritative. Ambiguous/unknown families fail closed. */
    public static String nativeKind(Map<String,String[]> tags) {
        String[] family = tags == null ? null : tags.get("Family");
        if (family == null || family.length != 1 || family[0]==null) return "UNKNOWN";
        return NATIVE_FAMILIES.getOrDefault(family[0], "UNKNOWN");
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
    /** Legacy diagnostic name classifier only; never used to authorize equipped native items. */
    @Deprecated
    public static String kind(String itemId, java.util.Map<String,String[]> rawTags) {
        String[] family = rawTags == null ? null : rawTags.get("Family");
        if (family != null && java.util.Arrays.asList(family).contains("Bomb")) return "BOMB";
        return kind(itemId);
    }
}
