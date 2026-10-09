package com.inigmasgames.hytalerpg.enemies;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Presentation projection of a frozen birth. Never contributes to affix or combat selection. */
public record MonsterVisualProfile(EnemyRarity rarity, String skinTint,
        EnemyAffixRegistry.Operator weaponVisualOwner, EnemyAffixRegistry.Operator armorVisualOwner) {
    public enum Classification { WEAPON_TINT, ARMOR_TINT, VFX_ONLY, NO_COLOR }
    private static final List<EnemyAffixRegistry.Operator> WEAPON_PRIORITY=List.of(
            EnemyAffixRegistry.Operator.FIRE_ENCHANTED, EnemyAffixRegistry.Operator.COLD_ENCHANTED,
            EnemyAffixRegistry.Operator.LIGHTNING_ENCHANTED, EnemyAffixRegistry.Operator.POISON_ENCHANTED,
            EnemyAffixRegistry.Operator.WIND_ENCHANTED, EnemyAffixRegistry.Operator.EARTH_ENCHANTED,
            EnemyAffixRegistry.Operator.VOID_ENCHANTED, EnemyAffixRegistry.Operator.SPECTRAL_HIT);
    private static final List<EnemyAffixRegistry.Operator> ARMOR_PRIORITY=List.of(
            EnemyAffixRegistry.Operator.PACKBOUND, EnemyAffixRegistry.Operator.BULWARK,
            EnemyAffixRegistry.Operator.STONE_SKIN, EnemyAffixRegistry.Operator.MAGIC_RESISTANT,
            EnemyAffixRegistry.Operator.REFLECTIVE);

    public static MonsterVisualProfile from(EnemyDescriptor descriptor) {
        Objects.requireNonNull(descriptor);
        return resolve(descriptor.enemyRarity(), descriptor.ownAffixes().stream()
                .map(a -> EnemyAffixRegistry.Operator.values()[Integer.parseInt(a.affixId().substring(3))-1]).toList());
    }
    public static MonsterVisualProfile resolve(EnemyRarity rarity, List<EnemyAffixRegistry.Operator> ownAffixes) {
        Objects.requireNonNull(rarity);Objects.requireNonNull(ownAffixes);
        var present=ownAffixes.isEmpty()?EnumSet.noneOf(EnemyAffixRegistry.Operator.class):EnumSet.copyOf(ownAffixes);
        String skin=switch(rarity) {
            case CHAMPION -> "#1d4dff";case UNIQUE -> "#a000ff";case SUPER_UNIQUE -> "#ff9100";
            default -> null;
        };
        if(skin==null)return new MonsterVisualProfile(rarity,null,null,null);
        return new MonsterVisualProfile(rarity,skin,
                WEAPON_PRIORITY.stream().filter(present::contains).findFirst().orElse(null),
                ARMOR_PRIORITY.stream().filter(present::contains).findFirst().orElse(null));
    }
    public static Classification classification(EnemyAffixRegistry.Operator operator) {
        Objects.requireNonNull(operator);
        if(WEAPON_PRIORITY.contains(operator))return Classification.WEAPON_TINT;
        if(ARMOR_PRIORITY.contains(operator))return Classification.ARMOR_TINT;
        return switch(operator) {
            case AURA_ENCHANTED -> Classification.VFX_ONLY;
            default -> Classification.NO_COLOR;
        };
    }
    public static String display(EnemyAffixRegistry.Operator operator) {
        if(operator==null)return "NONE";
        StringBuilder out=new StringBuilder();
        for(String word:operator.name().split("_")) {
            if(!out.isEmpty())out.append(' ');
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }
}
