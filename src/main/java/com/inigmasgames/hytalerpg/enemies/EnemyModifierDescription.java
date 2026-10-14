package com.inigmasgames.hytalerpg.enemies;

/** Concise mechanical descriptions shared by inspection and the selected-target HUD. */
public final class EnemyModifierDescription {
    private EnemyModifierDescription(){}
    public static String of(EnemyAffixRegistry.Operator operator){
        return switch(operator){
            case EXTRA_FAST -> "Faster movement and attack recovery";
            case EXTRA_STRONG -> "Increased physical damage";
            case MAGIC_RESISTANT -> "Increased elemental resistance";
            case STONE_SKIN -> "Increased defense";
            case FIRE_ENCHANTED -> "Extra Fire damage and Fire resistance";
            case COLD_ENCHANTED -> "Extra Water damage and Water resistance";
            case LIGHTNING_ENCHANTED -> "Extra Lightning damage and resistance";
            case POISON_ENCHANTED -> "Extra Earth damage and Earth resistance";
            case WIND_ENCHANTED -> "Extra Wind damage and Wind resistance";
            case EARTH_ENCHANTED -> "Extra Earth damage and Earth resistance";
            case VOID_ENCHANTED -> "Extra Void damage and Void resistance";
            case SPECTRAL_HIT -> "Extra elemental damage on direct hits";
            case MANA_BURN -> "Direct hits drain mana within victim caps";
            case CURSED -> "Hits may weaken outgoing direct damage";
            case KNOCKBACK -> "Stronger native knockback on eligible hits";
            case VAMPIRIC -> "Heals from dealt Health damage, with a rate cap";
            case UNWAVERING -> "Immune to stun and stagger";
            case UNSTOPPABLE -> "Immune to movement slows";
            case FRENZIED -> "Periodic damage, movement and recovery burst";
            case AVENGER -> "Gains damage and speed when initial minions die";
            case EMPOWERED_MINIONS -> "Initial minions gain Health, damage and speed";
            case HORDE -> "Two additional initial minions; eight-member cap";
            case AURA_ENCHANTED -> "Nearby initial pack shares the selected aura";
            case PACKBOUND -> "Protected until all initial guards are defeated";
            case ARMOR_BREAKER -> "Hits may temporarily reduce defense";
            case REFLECTIVE -> "Reflects eligible Health damage within safety caps";
            case BULWARK -> "Initial shield absorbs damage; does not regenerate";
        };
    }
}
