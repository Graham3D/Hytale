package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.Locale;

/** Compatibility rules derived from the installed Update 6 item metadata. */
public final class NpcEquipmentRules {
    private NpcEquipmentRules() { }

    public static boolean isPrimaryWeapon(ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return true;
        Item item = stack.getItem();
        return item != null && item.getWeapon() != null
                && !isShield(stack) && !isArrowAmmunition(stack);
    }

    public static boolean isShieldOrOffhand(ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return true;
        Item item = stack.getItem();
        ItemUtility utility = item == null ? null : item.getUtility();
        return isShield(stack) || (utility != null && (utility.isUsable() || utility.isCompatible()));
    }

    public static boolean requiresAmmunition(ItemStack weapon) {
        if (ItemStack.isEmpty(weapon)) return false;
        String animations = weapon.getItem() == null
                ? "" : safe(weapon.getItem().getPlayerAnimationsId());
        return animations.equals("bow") || animations.equals("crossbow");
    }

    public static boolean isCompatibleAmmunition(ItemStack weapon, ItemStack ammunition) {
        if (ItemStack.isEmpty(ammunition)) return true;
        if (!requiresAmmunition(weapon)) return false;
        // The installed Update 6 bow/crossbow interaction assets consume the Arrow family.
        // No public API exposes a multiple-ammunition preference order, so no fallback order
        // is guessed here: this explicitly selected stack is the only authored preference.
        return isArrowAmmunition(ammunition);
    }

    public static boolean isShield(ItemStack stack) {
        if (ItemStack.isEmpty(stack) || stack.getItem() == null) return false;
        return safe(stack.getItem().getPlayerAnimationsId()).equals("shield");
    }

    public static boolean isArrowAmmunition(ItemStack stack) {
        return !ItemStack.isEmpty(stack) && stack.getItemId() != null
                && stack.getItemId().toLowerCase(Locale.ROOT).startsWith("weapon_arrow_");
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
