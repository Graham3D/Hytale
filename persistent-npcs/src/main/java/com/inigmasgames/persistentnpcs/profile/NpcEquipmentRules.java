package com.inigmasgames.persistentnpcs.profile;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/** Compatibility rules derived from the installed Update 6 item metadata. */
public final class NpcEquipmentRules {
    public static final String INFINITE_AMMUNITION_CONFIG =
            "immersive.npcs.infiniteAmmunition.enabled";
    private static final NpcEquipmentCompatibilityResolver RESOLVER =
            new NpcEquipmentCompatibilityResolver();

    private NpcEquipmentRules() { }

    /** Server-owner boundary; authoring still separately requires the Gear permission. */
    public static boolean infiniteAmmunitionFeatureEnabled() {
        return Boolean.parseBoolean(System.getProperty(
                INFINITE_AMMUNITION_CONFIG, "true"));
    }

    public static boolean isPrimaryWeapon(ItemStack stack) {
        return RESOLVER.validatePrimaryWeapon(stack).compatible();
    }

    public static boolean isShieldOrOffhand(ItemStack stack) {
        return RESOLVER.validateOffhand(stack, ItemStack.EMPTY).compatible();
    }

    public static boolean requiresAmmunition(ItemStack weapon) {
        return RESOLVER.requiresAmmunition(weapon);
    }

    public static boolean isCompatibleAmmunition(ItemStack weapon, ItemStack ammunition) {
        return RESOLVER.validateAmmunition(ammunition, weapon).compatible();
    }

    public static boolean isShield(ItemStack stack) {
        return RESOLVER.validateOffhand(stack, ItemStack.EMPTY).compatible();
    }

    public static boolean isArrowAmmunition(ItemStack stack) {
        return RESOLVER.isSupportedAmmunition(stack);
    }
}
