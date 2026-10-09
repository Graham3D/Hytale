package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;

/** Operator input only. The durable birth, action and reward owners consume the normalized request. */
public record EnemyQaSpawnRequest(String nativeRoleId, EnemyRarity rarity, DifficultyId era, List<String> affixIds) {
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
        Objects.requireNonNull(era);
        affixIds = List.copyOf(affixIds);
        if (new HashSet<>(affixIds).size() != affixIds.size())
            throw new IllegalArgumentException("Duplicate Master Enemies affix.");
    }

    public static EnemyQaSpawnRequest parse(String role, String family, String era, List<String> aliases) {
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
        DifficultyId difficulty;
        try { difficulty=DifficultyId.valueOf(era.toUpperCase(Locale.ROOT)); }
        catch(RuntimeException invalid){throw new IllegalArgumentException("Era must be normal, nightmare, or hell.");}
        return new EnemyQaSpawnRequest(role, rarity, difficulty, ids);
    }

    /** Legacy offline fixtures remain Normal; the player command always supplies an explicit era. */
    public static EnemyQaSpawnRequest parse(String role,String family,List<String> aliases){
        return parse(role,family,"normal",aliases);
    }

    public static List<String> canonicalAliases() { return CANONICAL; }
    public static List<String> familyTokens() { return List.of("champion", "unique", "superunique"); }
    public static List<String> eraTokens() { return List.of("normal", "nightmare", "hell"); }
    public Optional<SuperUniqueTemplates.Template> authoredSuperUnique(){
        if(rarity!=EnemyRarity.SUPER_UNIQUE)return Optional.empty();
        return SuperUniqueTemplates.canonical().all().stream()
                .filter(template->template.canonicalRoleId().equals(nativeRoleId)).findFirst();
    }
    public int requiredAffixes(EnemyBalance balance){
        int authored=balance.rarity(rarity,false).counts().get(era.ordinal());
        return rarity==EnemyRarity.SUPER_UNIQUE?authoredSuperUnique()
                .map(template->template.affixCount(era)).orElse(Math.min(4,3+authored)):authored;
    }
    public int baseMinions(){return rarity==EnemyRarity.CHAMPION?0:authoredSuperUnique()
            .map(SuperUniqueTemplates.Template::minionCount).orElse(2);}
    public int offeredMembers(){return rarity==EnemyRarity.CHAMPION?1:1+baseMinions()
            +(affixIds.isEmpty()||affixIds.contains("ME-022")?2:0);}
    public void validateExplicitCount(EnemyBalance balance){
        int expected=requiredAffixes(balance);
        if(!affixIds.isEmpty()&&affixIds.size()!=expected){
            String family=rarity==EnemyRarity.SUPER_UNIQUE?"Super Unique":
                    rarity.name().charAt(0)+rarity.name().substring(1).toLowerCase(Locale.ROOT);
            String mode=era.name().charAt(0)+era.name().substring(1).toLowerCase(Locale.ROOT);
            throw new IllegalArgumentException(mode+" "+family+" requires "+expected+" affixes; supplied "+affixIds.size());
        }
    }
}
