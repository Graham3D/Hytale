package com.inigmasgames.hytalerpg.difficulty;

import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;

/** Resolves one monster from a persistent world and authored encounter, never from an attacking character. */
public final class EncounterProfileResolver {
    public enum Evidence { FIXTURE_ONLY, ASSET_AUDITED_CONNECTED_UNVERIFIED, VERIFIED_CONNECTED, AUTHORED_BASELINE_CONNECTED_UNVERIFIED }
    public enum ScalingMode { AUTHORED_FINAL, GLOBAL_LEVEL_SCALED }
    public record Profile(String id, DifficultyId difficulty, String worldProfileId, String roleId, String biomeKey,
                          int combatLevel, double roleHealth, double roleAttackBasis,
                          double unappliedRankHealth, double unappliedRankDamage,
                          MonsterResistanceProfile resistance, Evidence evidence,int referenceLevel) {
        public Profile(String id,DifficultyId difficulty,String worldProfileId,String roleId,String biomeKey,
                       int combatLevel,double roleHealth,double roleAttackBasis,double unappliedRankHealth,
                       double unappliedRankDamage,MonsterResistanceProfile resistance,Evidence evidence){
            this(id,difficulty,worldProfileId,roleId,biomeKey,combatLevel,roleHealth,roleAttackBasis,
                    unappliedRankHealth,unappliedRankDamage,resistance,evidence,-1);
        }
        public Profile {
            for (String value : List.of(id, worldProfileId, roleId, biomeKey)) if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException("INVALID_ENCOUNTER_PROFILE");
            Objects.requireNonNull(difficulty); Objects.requireNonNull(resistance); Objects.requireNonNull(evidence);
            if (combatLevel < 1 || combatLevel > 99) throw new IllegalArgumentException("INVALID_COMBAT_LEVEL");
            if(referenceLevel< -1||referenceLevel>99)throw new IllegalArgumentException("INVALID_REFERENCE_LEVEL");
            for (double value : new double[]{roleHealth, roleAttackBasis, unappliedRankHealth, unappliedRankDamage})
                if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("MISSING_OR_INVALID_ROLE_BASELINE");
        }
        /** -1 preserves authored final stats; 0 scales the native role chassis directly; positive values opt into a reference ratio. */
        public ScalingMode scalingMode(){return referenceLevel<0?ScalingMode.AUTHORED_FINAL:ScalingMode.GLOBAL_LEVEL_SCALED;}
    }
    /** Optional in old persisted snapshots: absent means retain their original, already resolved values. */
    public record Progression(String revision,int referenceLevel,double referenceHealth,
                              double healthRatio,double damageRatio,double defenseRating,double elementalFloor){
        public Progression{
            // These are frozen numeric inputs; older referenced revisions remain readable after tuning.
            if(revision==null||revision.isBlank()||referenceLevel<0||referenceLevel>99)
                throw new IllegalArgumentException("MONSTER_PROGRESSION_REVISION_OR_REFERENCE");
            for(double value:new double[]{referenceHealth,healthRatio,damageRatio,defenseRating,elementalFloor})
                if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("MONSTER_PROGRESSION_VALUE");
            if(referenceHealth==0||healthRatio==0||damageRatio==0||elementalFloor>.75)
                throw new IllegalArgumentException("MONSTER_PROGRESSION_VALUE");
        }
    }
    /** Immutable spawn snapshot. A caller may persist it; moving an actor never resolves it again. */
    public record Resolved(UUID worldId, UUID enemyId, DifficultyId difficulty, String profileId,
                           String worldProfileId, String roleId, String biomeKey, int sourceCombatLevel,
                           double maxHealth, double attackBasis, double difficultyHealthFactor,
                           double difficultyDamageFactor, MonsterResistanceProfile resistance, Evidence evidence,
                           Progression progression) {
        public Resolved(UUID worldId,UUID enemyId,DifficultyId difficulty,String profileId,String worldProfileId,
                        String roleId,String biomeKey,int sourceCombatLevel,double maxHealth,double attackBasis,
                        double difficultyHealthFactor,double difficultyDamageFactor,
                        MonsterResistanceProfile resistance,Evidence evidence){
            this(worldId,enemyId,difficulty,profileId,worldProfileId,roleId,biomeKey,sourceCombatLevel,maxHealth,
                    attackBasis,difficultyHealthFactor,difficultyDamageFactor,resistance,evidence,null);
        }
        public Resolved {
            Objects.requireNonNull(worldId);Objects.requireNonNull(enemyId);Objects.requireNonNull(difficulty);
            Objects.requireNonNull(resistance);Objects.requireNonNull(evidence);
            for(String id:List.of(profileId,worldProfileId,roleId,biomeKey))if(id.isBlank()||id.length()>256)throw new IllegalArgumentException("INVALID_FROZEN_PROFILE_ID");
            if(sourceCombatLevel<1||sourceCombatLevel>99)throw new IllegalArgumentException("INVALID_FROZEN_LEVEL");
            for(double value:new double[]{maxHealth,attackBasis,difficultyHealthFactor,difficultyDamageFactor})
                if(!Double.isFinite(value)||value<=0||value>Float.MAX_VALUE)throw new IllegalArgumentException("INVALID_FROZEN_STAT");
            if(difficulty!=DifficultyId.HELL&&!resistance.immunities().isEmpty())throw new IllegalArgumentException("IMMUNITY_REQUIRES_HELL");
        }
        public double nativeHealthBaseline(){return progression==null?maxHealth/difficultyHealthFactor:progression.referenceHealth();}
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
        return preview(binding, enemy, role, biome);
    }
    private Resolved preview(WorldDifficultyRegistry.Binding binding, UUID enemy, String role, String biome) {
        UUID world=binding.worldId();
        if (binding.kind() != WorldDifficultyRegistry.Kind.CAMPAIGN) throw new IllegalStateException("SHARED_EVENT_PROFILE_REQUIRED");
        var profile = profiles.get(new Key(binding.difficulty(), binding.profileId(), role, biome));
        if (profile == null) throw new IllegalStateException("UNAUTHORED_ENCOUNTER_PROFILE:" + binding.difficulty() + ":" + role + ":" + biome);
        var factor = scalars.difficulty(binding.difficulty().name());
        Progression progression=null;
        double healthRatio=1,damageRatio=1;
        var resistance=profile.resistance();
        if(profile.scalingMode()==ScalingMode.GLOBAL_LEVEL_SCALED){
            var curve=MonsterProgression.current();
            if(profile.referenceLevel()>0){
                healthRatio=curve.healthRatio(profile.combatLevel(),profile.referenceLevel());
                damageRatio=curve.damageRatio(profile.combatLevel(),profile.referenceLevel());
            }else{
                // Native role amounts are creature-specific chassis coefficients, not level-1 evidence.
                healthRatio=curve.at(profile.combatLevel()).healthGrowth();
                damageRatio=curve.at(profile.combatLevel()).damageGrowth();
            }
            var level=curve.at(profile.combatLevel());
            progression=new Progression(curve.revision(),profile.referenceLevel(),profile.roleHealth(),
                    healthRatio,damageRatio,level.defenseRating(),level.elementalFloor());
            var floor=new EnumMap<MonsterResistanceProfile.Channel,Double>(MonsterResistanceProfile.Channel.class);
            for(var channel:List.of(MonsterResistanceProfile.Channel.WIND,MonsterResistanceProfile.Channel.WATER,
                    MonsterResistanceProfile.Channel.FIRE,MonsterResistanceProfile.Channel.EARTH,
                    MonsterResistanceProfile.Channel.LIGHTNING,MonsterResistanceProfile.Channel.VOID))
                floor.put(channel,level.elementalFloor());
            resistance=resistance.withProviders(floor,Map.of(),Set.of());
        }
        double health = profile.roleHealth() * healthRatio * profile.unappliedRankHealth() * factor.healthMultiplier();
        double attack = profile.roleAttackBasis() * damageRatio * profile.unappliedRankDamage() * factor.damageMultiplier();
        if (!Double.isFinite(health) || !Double.isFinite(attack) || health > Float.MAX_VALUE || attack > Float.MAX_VALUE)
            throw new IllegalArgumentException("NATIVE_STAT_OVERFLOW");
        return new Resolved(world, enemy, binding.difficulty(), profile.id(), binding.profileId(), role, biome,
                profile.combatLevel(), health, attack, factor.healthMultiplier(), factor.damageMultiplier()*damageRatio,
                resistance, profile.evidence(),progression);
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
    /** QA selects an already authored era profile without rebinding the durable world registry. */
    public Resolved resolveAuthoredQa(UUID world,UUID enemy,String role,String biome,DifficultyId era){
        var actual=worlds.require(world);
        if(!actual.enabled()||actual.kind()!=WorldDifficultyRegistry.Kind.CAMPAIGN)
            throw new IllegalStateException("QA_WORLD_DIFFICULTY_UNAVAILABLE");
        var matches=profiles.keySet().stream().filter(k->k.mode()==era&&k.role().equals(role)
                &&k.biome().equals(biome)).toList();
        if(matches.size()!=1)throw new IllegalStateException("QA_ERA_PROFILE_UNAVAILABLE:"+era+":"+role+":"+biome);
        var key=matches.getFirst();
        var qaBinding=new WorldDifficultyRegistry.Binding(world,actual.worldName(),actual.kind(),era,
                key.worldProfileId(),actual.progressionScope(),true);
        var result=preview(qaBinding,enemy,role,biome);
        if(!scalars.difficulty(era.name()).enabled()
                ||result.evidence()!=Evidence.AUTHORED_BASELINE_CONNECTED_UNVERIFIED
                &&result.evidence()!=Evidence.VERIFIED_CONNECTED)
            throw new IllegalStateException("QA_ERA_AUTHORING_GATE_CLOSED:"+era);
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
