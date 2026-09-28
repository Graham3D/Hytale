package com.inigmasgames.hytalerpg.difficulty;

import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;

/** Resolves one monster from a persistent world and authored encounter, never from an attacking character. */
public final class EncounterProfileResolver {
    public enum Evidence { FIXTURE_ONLY, ASSET_AUDITED_CONNECTED_UNVERIFIED, VERIFIED_CONNECTED, AUTHORED_BASELINE_CONNECTED_UNVERIFIED }
    public record Profile(String id, DifficultyId difficulty, String worldProfileId, String roleId, String biomeKey,
                          int combatLevel, double roleHealth, double roleAttackBasis,
                          double unappliedRankHealth, double unappliedRankDamage,
                          MonsterResistanceProfile resistance, Evidence evidence) {
        public Profile {
            for (String value : List.of(id, worldProfileId, roleId, biomeKey)) if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException("INVALID_ENCOUNTER_PROFILE");
            Objects.requireNonNull(difficulty); Objects.requireNonNull(resistance); Objects.requireNonNull(evidence);
            if (combatLevel < 1 || combatLevel > 99) throw new IllegalArgumentException("INVALID_COMBAT_LEVEL");
            for (double value : new double[]{roleHealth, roleAttackBasis, unappliedRankHealth, unappliedRankDamage})
                if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("MISSING_OR_INVALID_ROLE_BASELINE");
        }
    }
    /** Immutable spawn snapshot. A caller may persist it; moving an actor never resolves it again. */
    public record Resolved(UUID worldId, UUID enemyId, DifficultyId difficulty, String profileId,
                           String worldProfileId, String roleId, String biomeKey, int sourceCombatLevel,
                           double maxHealth, double attackBasis, double difficultyHealthFactor,
                           double difficultyDamageFactor, MonsterResistanceProfile resistance, Evidence evidence) {
        public Resolved {
            Objects.requireNonNull(worldId);Objects.requireNonNull(enemyId);Objects.requireNonNull(difficulty);
            Objects.requireNonNull(resistance);Objects.requireNonNull(evidence);
            for(String id:List.of(profileId,worldProfileId,roleId,biomeKey))if(id.isBlank()||id.length()>256)throw new IllegalArgumentException("INVALID_FROZEN_PROFILE_ID");
            if(sourceCombatLevel<1||sourceCombatLevel>99)throw new IllegalArgumentException("INVALID_FROZEN_LEVEL");
            for(double value:new double[]{maxHealth,attackBasis,difficultyHealthFactor,difficultyDamageFactor})
                if(!Double.isFinite(value)||value<=0||value>Float.MAX_VALUE)throw new IllegalArgumentException("INVALID_FROZEN_STAT");
            if(difficulty!=DifficultyId.HELL&&!resistance.immunities().isEmpty())throw new IllegalArgumentException("IMMUNITY_REQUIRES_HELL");
        }
        public double nativeHealthBaseline(){return maxHealth/difficultyHealthFactor;}
    }
    private record Key(DifficultyId mode, String worldProfileId, String role, String biome) {}
    private final WorldDifficultyRegistry worlds;
    private final ProgressionProfiles scalars;
    private final Map<Key, Profile> profiles;
    public EncounterProfileResolver(WorldDifficultyRegistry worlds, ProgressionProfiles scalars, List<Profile> authored) {
        this.worlds = Objects.requireNonNull(worlds); this.scalars = Objects.requireNonNull(scalars);
        var index = new HashMap<Key, Profile>();
        for (var p : authored) if (index.put(new Key(p.difficulty(), p.worldProfileId(), p.roleId(), p.biomeKey()), p) != null)
            throw new IllegalArgumentException("AMBIGUOUS_ENCOUNTER_PROFILE");
        profiles = Map.copyOf(index);
    }
    public Resolved preview(UUID world, UUID enemy, String role, String biome) {
        Objects.requireNonNull(enemy);
        var binding = worlds.require(world);
        if (binding.kind() != WorldDifficultyRegistry.Kind.CAMPAIGN) throw new IllegalStateException("SHARED_EVENT_PROFILE_REQUIRED");
        var profile = profiles.get(new Key(binding.difficulty(), binding.profileId(), role, biome));
        if (profile == null) throw new IllegalStateException("UNAUTHORED_ENCOUNTER_PROFILE:" + binding.difficulty() + ":" + role + ":" + biome);
        var factor = scalars.difficulty(binding.difficulty().name());
        double health = profile.roleHealth() * profile.unappliedRankHealth() * factor.healthMultiplier();
        double attack = profile.roleAttackBasis() * profile.unappliedRankDamage() * factor.damageMultiplier();
        if (!Double.isFinite(health) || !Double.isFinite(attack) || health > Float.MAX_VALUE || attack > Float.MAX_VALUE)
            throw new IllegalArgumentException("NATIVE_STAT_OVERFLOW");
        return new Resolved(world, enemy, binding.difficulty(), profile.id(), binding.profileId(), role, biome,
                profile.combatLevel(), health, attack, factor.healthMultiplier(), factor.damageMultiplier(), profile.resistance(), profile.evidence());
    }
    public Resolved resolveProduction(UUID world, UUID enemy, String role, String biome) {
        var result = preview(world, enemy, role, biome);
        if (!worlds.require(world).enabled() || !scalars.difficulty(result.difficulty().name()).enabled() || result.evidence() != Evidence.VERIFIED_CONNECTED)
            throw new IllegalStateException("ENCOUNTER_PRODUCTION_GATE_CLOSED");
        return result;
    }
    /** Explicitly authored balance rollout, separate from the stricter connected-production evidence gate. */
    public Resolved resolveAuthored(UUID world,UUID enemy,String role,String biome){
        var result=preview(world,enemy,role,biome);
        if(!worlds.require(world).enabled()||!scalars.difficulty(result.difficulty().name()).enabled()
                ||result.evidence()!=Evidence.AUTHORED_BASELINE_CONNECTED_UNVERIFIED&&result.evidence()!=Evidence.VERIFIED_CONNECTED)
            throw new IllegalStateException("ENCOUNTER_AUTHORING_GATE_CLOSED");
        return result;
    }
    public Optional<EnemyRewardRegistry.Spawn> classifyAuthored(EnemyRewardRegistry registry,UUID world,UUID enemy,String role,String biome,EnemyRewardRegistry.Origin origin,long now){
        var legacy=registry.classify(world,enemy,role,biome,origin,now);
        if(legacy.isEmpty())return Optional.empty();
        return author(legacy.get(),biome);
    }
    public Optional<EnemyRewardRegistry.Spawn> author(EnemyRewardRegistry.Spawn spawn,String profileBiome){
        var binding=worlds.find(spawn.world());
        if(binding.isEmpty()||!binding.get().enabled()||binding.get().kind()!=WorldDifficultyRegistry.Kind.CAMPAIGN
                ||!profiles.containsKey(new Key(binding.get().difficulty(),binding.get().profileId(),spawn.roleId(),profileBiome)))return Optional.empty();
        var resolved=resolveAuthored(spawn.world(),spawn.enemy(),spawn.roleId(),profileBiome);
        return Optional.of(new EnemyRewardRegistry.Spawn(spawn.world(),spawn.enemy(),spawn.roleId(),spawn.combatIdentity(),profileBiome,resolved.sourceCombatLevel(),
                spawn.rank(),spawn.rarity(),resolved.profileId(),spawn.spawnedAtMillis(),spawn.milestone(),resolved));
    }
    /** Retains R031 frozen rewards. Unknown/non-Normal worlds cannot accidentally use Normal rewards. */
    public Optional<EnemyRewardRegistry.Spawn> classifyLegacyNormal(EnemyRewardRegistry registry, UUID world, UUID enemy,
            String role, String biome, EnemyRewardRegistry.Origin origin, long now) {
        var binding = worlds.find(world);
        if (binding.isEmpty() || !binding.get().enabled() || binding.get().kind() != WorldDifficultyRegistry.Kind.CAMPAIGN
                || binding.get().difficulty() != DifficultyId.NORMAL || !binding.get().profileId().equals(registry.profileId())) return Optional.empty();
        return registry.classify(world, enemy, role, biome, origin, now);
    }
}
