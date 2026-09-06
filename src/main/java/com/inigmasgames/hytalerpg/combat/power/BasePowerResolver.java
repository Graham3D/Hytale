package com.inigmasgames.hytalerpg.combat.power;

import java.util.Set;

public final class BasePowerResolver {
    private final ItemPowerRegistry registry;
    public BasePowerResolver(ItemPowerRegistry registry) { this.registry = registry; }

    public Resolution resolve(Request request) {
        if (request.source() == BasePowerSource.NONE)
            return new Resolution(BasePowerSource.NONE, LinkTreeWeaponClass.UTILITY, null, 0.0);
        if (request.source() == BasePowerSource.INNATE) {
            if (request.innateBasePower() == null || request.innateBasePower() < 0.0)
                throw new IllegalArgumentException("Damaging no-weapon skills require non-negative InnateBasePower");
            return new Resolution(BasePowerSource.INNATE, LinkTreeWeaponClass.INNATE, null, request.innateBasePower());
        }
        if (request.item() == null) throw new IllegalArgumentException(request.source() + " requires an audited item");
        ItemPowerDescriptor audited = mergeRegistry(request.item());
        LinkTreeWeaponClass weaponClass = classify(audited.tags());
        if (request.source() == BasePowerSource.MAGIC_WEAPON) {
            if (audited.magicPower() == null) throw new IllegalArgumentException("Item has no authored MagicPower: " + audited.itemId());
            return new Resolution(request.source(), LinkTreeWeaponClass.MAGIC, audited.itemId(), audited.magicPower());
        }
        if (audited.weaponPower() == null) throw new IllegalArgumentException("Item has no audited WeaponPower: " + audited.itemId());
        if (weaponClass == LinkTreeWeaponClass.MAGIC || weaponClass == LinkTreeWeaponClass.UTILITY)
            throw new IllegalArgumentException("Weapon source requires LIGHT or HEAVY classification: " + audited.itemId());
        return new Resolution(request.source(), weaponClass, audited.itemId(), audited.weaponPower());
    }

    private ItemPowerDescriptor mergeRegistry(ItemPowerDescriptor explicit) {
        return registry.find(explicit.itemId()).map(fallback -> new ItemPowerDescriptor(explicit.itemId(),
                explicit.tags().isEmpty() ? fallback.tags() : explicit.tags(),
                explicit.weaponPower() == null ? fallback.weaponPower() : explicit.weaponPower(),
                explicit.magicPower() == null ? fallback.magicPower() : explicit.magicPower())).orElse(explicit);
    }

    public static LinkTreeWeaponClass classify(Set<String> tags) {
        boolean light = hasAny(tags, "RPG_WEAPON_LIGHT", "SWORD", "DAGGER", "BOW", "CROSSBOW", "GUN", "BOMB");
        boolean heavy = hasAny(tags, "RPG_WEAPON_HEAVY", "LONGSWORD", "MACE", "BATTLEAXE", "SPEAR", "SHIELD");
        boolean magic = hasAny(tags, "RPG_WEAPON_MAGIC", "STAFF", "WAND", "SPELLBOOK");
        int matches = (light ? 1 : 0) + (heavy ? 1 : 0) + (magic ? 1 : 0);
        if (matches != 1) throw new IllegalArgumentException("Item must have exactly one explicit RPG weapon classification");
        return light ? LinkTreeWeaponClass.LIGHT : heavy ? LinkTreeWeaponClass.HEAVY : LinkTreeWeaponClass.MAGIC;
    }
    private static boolean hasAny(Set<String> tags, String... candidates) {
        for (String candidate : candidates) if (tags.contains(candidate)) return true;
        return false;
    }

    public record Request(BasePowerSource source, ItemPowerDescriptor item, Double innateBasePower) { }
    public record Resolution(BasePowerSource source, LinkTreeWeaponClass weaponClass, String itemId, double basePower) { }
}
