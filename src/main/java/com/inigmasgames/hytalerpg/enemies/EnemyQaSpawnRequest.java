package com.inigmasgames.hytalerpg.enemies;

import java.util.*;

/** Operator input only. The durable birth, action and reward owners consume the normalized request. */
public record EnemyQaSpawnRequest(String nativeRoleId, EnemyRarity rarity, List<String> affixIds) {
    private static final List<String> CANONICAL = List.of(
            "extrafast", "extrastrong", "magicresistant", "stoneskin", "fireenchanted",
            "coldenchanted", "lightningenchanted", "poisonenchanted", "windenchanted",
            "earthenchanted", "voidenchanted", "spectralhit", "manaburn", "cursed",
            "knockback", "vampiric", "unwavering", "unstoppable", "frenzied", "avenger",
            "empoweredminions", "horde", "auraenchanted", "packbound", "armorbreaker",
            "reflective", "bulwark");
    private static final Map<String, String> SYNONYMS = Map.of(
            "fire", "fireenchanted", "cold", "coldenchanted", "lightning", "lightningenchanted",
            "poison", "poisonenchanted", "wind", "windenchanted", "earth", "earthenchanted",
            "void", "voidenchanted", "aura", "auraenchanted");

    public EnemyQaSpawnRequest {
        if (nativeRoleId == null || nativeRoleId.isBlank()) throw new IllegalArgumentException("Monster role is required.");
        Objects.requireNonNull(rarity);
        affixIds = List.copyOf(affixIds);
        if (new HashSet<>(affixIds).size() != affixIds.size())
            throw new IllegalArgumentException("Duplicate Master Enemies affix.");
    }

    public static EnemyQaSpawnRequest parse(String role, String family, List<String> aliases) {
        EnemyRarity rarity = switch (family == null ? "" : family.toLowerCase(Locale.ROOT)) {
            case "champion" -> EnemyRarity.CHAMPION;
            case "unique" -> EnemyRarity.UNIQUE;
            case "superunique" -> EnemyRarity.SUPER_UNIQUE;
            default -> throw new IllegalArgumentException("Family must be champion, unique, or superunique.");
        };
        var ids = new ArrayList<String>();
        for (String input : aliases) {
            String alias = input == null ? "" : input.toLowerCase(Locale.ROOT);
            alias = SYNONYMS.getOrDefault(alias, alias);
            int index = CANONICAL.indexOf(alias);
            if (index < 0) throw new IllegalArgumentException("Unknown affix '" + input + "'. Valid: " + String.join(", ", CANONICAL));
            String id = "ME-%03d".formatted(index + 1);
            if (ids.contains(id)) throw new IllegalArgumentException("Duplicate affix: " + alias);
            ids.add(id);
        }
        return new EnemyQaSpawnRequest(role, rarity, ids);
    }

    public static List<String> canonicalAliases() { return CANONICAL; }
    public static List<String> familyTokens() { return List.of("champion", "unique", "superunique"); }
}
