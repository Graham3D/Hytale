package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Typed equipment compatibility derived from the installed Update 6 item asset
 * contract. UNKNOWN/REQUIRES_REVIEW always fail closed at the transaction edge.
 */
public final class NpcEquipmentCompatibilityResolver {
    public enum Status { COMPATIBLE, INCOMPATIBLE, UNKNOWN, REQUIRES_REVIEW }

    public record Verdict(Status status, String reason, String evidence) {
        public boolean compatible() { return status == Status.COMPATIBLE; }
    }

    public Verdict validateArmor(ItemStack stack, short targetSlot) {
        if (ItemStack.isEmpty(stack)) return compatible("EMPTY", "empty stack");
        Item item = stack.getItem();
        if (item == null) return unknown("ITEM_ASSET_MISSING", stack.getItemId());
        if (targetSlot < 0 || targetSlot >= ItemArmorSlot.VALUES.length) {
            return incompatible("ARMOR_SLOT_OUT_OF_RANGE", Short.toString(targetSlot));
        }
        if (item.getArmor() == null || item.getArmor().getArmorSlot() == null) {
            return incompatible("ITEM_HAS_NO_ARMOR_METADATA", item.getId());
        }
        ItemArmorSlot expected = ItemArmorSlot.VALUES[targetSlot];
        ItemArmorSlot actual = item.getArmor().getArmorSlot();
        return actual == expected
                ? compatible("ARMOR_SLOT_MATCH", "ItemArmor.armorSlot=" + actual)
                : incompatible("ARMOR_SLOT_MISMATCH",
                        "ItemArmor.armorSlot=" + actual + ", target=" + expected);
    }

    public Verdict validatePrimaryWeapon(ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return compatible("EMPTY", "empty stack");
        Item item = stack.getItem();
        if (item == null) return unknown("ITEM_ASSET_MISSING", stack.getItemId());
        String family = tag(item, "Family");
        if (equalsAny(family, "Arrow", "Shield")) {
            return incompatible("WEAPON_FAMILY_RESERVED_FOR_OTHER_ENDPOINT",
                    "Tags.Family=" + family);
        }
        if (item.getWeapon() == null) {
            return incompatible("ITEM_HAS_NO_WEAPON_METADATA", item.getId());
        }
        return compatible("WEAPON_METADATA_PRESENT",
                "Item.weapon=true, Tags.Family=" + shown(family));
    }

    public Verdict validateOffhand(ItemStack stack, ItemStack primaryWeapon) {
        if (ItemStack.isEmpty(stack)) return compatible("EMPTY", "empty stack");
        Item item = stack.getItem();
        if (item == null) return unknown("ITEM_ASSET_MISSING", stack.getItemId());
        String family = tag(item, "Family");
        if (family.equalsIgnoreCase("Shield") && item.getUtility() != null
                && item.getUtility().isUsable()) {
            return compatible("SUPPORTED_SHIELD",
                    "Tags.Family=Shield, ItemUtility.usable=true");
        }
        return new Verdict(Status.REQUIRES_REVIEW, "OFFHAND_CATEGORY_UNSUPPORTED",
                "Update 6 exposes no general offhand contract; Tags.Family=" + shown(family));
    }

    public Verdict validateAmmunition(ItemStack ammunition, ItemStack primaryWeapon) {
        if (ItemStack.isEmpty(ammunition)) return compatible("EMPTY", "empty stack");
        Item ammoItem = ammunition.getItem();
        if (ammoItem == null) return unknown("ITEM_ASSET_MISSING", ammunition.getItemId());
        Verdict weapon = validatePrimaryWeapon(primaryWeapon);
        if (!weapon.compatible()) {
            return incompatible("PRIMARY_WEAPON_INVALID", weapon.reason());
        }
        if (!requiresAmmunition(primaryWeapon)) {
            return incompatible("PRIMARY_WEAPON_DOES_NOT_USE_SUPPORTED_AMMUNITION",
                    weapon.evidence());
        }
        String family = tag(ammoItem, "Family");
        return family.equalsIgnoreCase("Arrow")
                ? compatible("SUPPORTED_ARROW_FAMILY", "Tags.Family=Arrow")
                : incompatible("AMMUNITION_FAMILY_MISMATCH",
                        "Tags.Family=" + shown(family) + ", expected=Arrow");
    }

    public boolean isSupportedAmmunition(ItemStack ammunition) {
        return !ItemStack.isEmpty(ammunition) && ammunition.getItem() != null
                && tag(ammunition.getItem(), "Family").equalsIgnoreCase("Arrow");
    }

    public boolean requiresAmmunition(ItemStack weapon) {
        if (ItemStack.isEmpty(weapon) || weapon.getItem() == null) return false;
        String family = tag(weapon.getItem(), "Family");
        return equalsAny(family, "Shortbow", "Crossbow", "Bow");
    }

    private static String tag(Item item, String key) {
        if (item == null || item.getData() == null) return "";
        Map<String, String[]> tags = item.getData().getRawTags();
        if (tags == null) return "";
        for (var entry : tags.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                return Arrays.stream(entry.getValue()).filter(value -> value != null)
                        .map(String::strip).filter(value -> !value.isBlank())
                        .findFirst().orElse("");
            }
        }
        return "";
    }

    private static boolean equalsAny(String value, String... expected) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(expected).anyMatch(candidate ->
                normalized.equals(candidate.toLowerCase(Locale.ROOT)));
    }

    private static Verdict compatible(String reason, String evidence) {
        return new Verdict(Status.COMPATIBLE, reason, evidence);
    }

    private static Verdict incompatible(String reason, String evidence) {
        return new Verdict(Status.INCOMPATIBLE, reason, evidence);
    }

    private static Verdict unknown(String reason, String evidence) {
        return new Verdict(Status.UNKNOWN, reason, evidence);
    }

    private static String shown(String value) {
        return value == null || value.isBlank() ? "<missing>" : value;
    }
}
