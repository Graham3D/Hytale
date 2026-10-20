package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited role and biome identities; combat levels/ranks are explicitly RPG-authored, never vanilla claims. */
public record EnemyRewardRegistry(int schemaVersion,String profileId,String serverSha256,String assetsSha256,
                                  List<Role> roles,List<Alias> aliases,List<Biome> biomes){
    public record Role(String roleId,String combatIdentity,ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,
                       double nativeHealth,double attackReference,String assetPath,String assetSha256,String evidence){
        public Role {id(roleId);id(combatIdentity);Objects.requireNonNull(rank);Objects.requireNonNull(rarity);
            baseline(nativeHealth,attackReference);asset(assetPath,assetSha256);if(!"ASSET_AUDITED_CONNECTED_UNVERIFIED".equals(evidence))throw new IllegalArgumentException("ROLE_EVIDENCE_NOT_DECLARED");}
    }
    public record Alias(String roleId,String canonicalRoleId,double nativeHealth,double attackReference,
                        String assetPath,String assetSha256,String evidence){
        public Alias {id(roleId);id(canonicalRoleId);baseline(nativeHealth,attackReference);asset(assetPath,assetSha256);
            if(!"ASSET_AUDITED_CONNECTED_UNVERIFIED".equals(evidence))throw new IllegalArgumentException("ALIAS_EVIDENCE_NOT_DECLARED");}
    }
    public record ResolvedRole(String runtimeRoleId,Role canonical,double nativeHealth,double attackReference,boolean alias){}
    public record Biome(String worldgenName,String zoneId,String biomeId,String rpgBand,int combatLevel,
                        String assetPath,String assetSha256){
        public Biome {id(worldgenName);id(zoneId);id(biomeId);id(rpgBand);asset(assetPath,assetSha256);
            if(combatLevel<1||combatLevel>99)throw new IllegalArgumentException("INVALID_AUTHORED_COMBAT_LEVEL");}
        public String key(){return worldgenName+"/"+zoneId+"/"+biomeId;}
    }
    public enum Origin { WILD_WORLD_SPAWN, SUMMONED, REVIVED, CONVERTED, TRAINING, PLAYER_OWNED, UNKNOWN }
    public record Spawn(UUID world,UUID enemy,String roleId,String combatIdentity,String biomeKey,int level,
                        ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,String registryProfile,long spawnedAtMillis,
                        com.inigmasgames.hytalerpg.difficulty.GolemEncounter milestone,
                        com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Resolved combat){
        public Spawn(UUID world,UUID enemy,String roleId,String combatIdentity,String biomeKey,int level,ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,String registryProfile,long spawnedAtMillis){
            this(world,enemy,roleId,combatIdentity,biomeKey,level,rank,rarity,registryProfile,spawnedAtMillis,null);
        }
        public Spawn(UUID world,UUID enemy,String roleId,String combatIdentity,String biomeKey,int level,ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,String registryProfile,long spawnedAtMillis,com.inigmasgames.hytalerpg.difficulty.GolemEncounter milestone){
            this(world,enemy,roleId,combatIdentity,biomeKey,level,rank,rarity,registryProfile,spawnedAtMillis,milestone,null);
        }
        public Spawn {Objects.requireNonNull(world);Objects.requireNonNull(enemy);id(roleId);id(combatIdentity);id(biomeKey);id(registryProfile);
            Objects.requireNonNull(rank);Objects.requireNonNull(rarity);if((milestone==null||combat!=null?(level<1||level>99):level!=0)||spawnedAtMillis<0)throw new IllegalArgumentException("INVALID_SPAWN_CONTEXT");
            if(combat!=null&&(!world.equals(combat.worldId())||!enemy.equals(combat.enemyId())||!roleId.equals(combat.roleId())||!biomeKey.equals(combat.biomeKey())
                    ||level!=combat.sourceCombatLevel()||!registryProfile.equals(combat.profileId())||milestone!=null&&milestone.difficulty()!=combat.difficulty()))throw new IllegalArgumentException("FROZEN_COMBAT_SOURCE_MISMATCH");}
        public String eventId(){return "enemy-death/"+world+"/"+enemy;}
        /** Future item generation consumes this frozen source, never the killer's level/current world. */
        public Optional<LootSource> lootSource(){return combat==null?Optional.empty():Optional.of(new LootSource(eventId(),world,enemy,combat.difficulty(),level,roleId,rank,rarity,registryProfile));}
    }
    public record LootSource(String eventId,UUID world,UUID enemy,com.inigmasgames.hytalerpg.difficulty.DifficultyId difficulty,int sourceCombatLevel,String role,ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,String profileRevision){}
    public EnemyRewardRegistry {
        if(schemaVersion!=2)throw new IllegalArgumentException("ENEMY_REGISTRY_SCHEMA");id(profileId);hash(serverSha256);hash(assetsSha256);
        roles=List.copyOf(roles);aliases=List.copyOf(aliases);biomes=List.copyOf(biomes);
        if(roles.isEmpty()||roles.size()+aliases.size()>2048||biomes.isEmpty()||biomes.size()>2048)throw new IllegalArgumentException("ENEMY_REGISTRY_BOUNDS");
        Set<String> ids=new HashSet<>();
        for(var role:roles)if(!ids.add(role.roleId()))throw new IllegalArgumentException("DUPLICATE_NATIVE_ROLE");
        var canonical=Set.copyOf(ids);
        for(var alias:aliases){if(!ids.add(alias.roleId()))throw new IllegalArgumentException("DUPLICATE_NATIVE_ROLE");
            if(!canonical.contains(alias.canonicalRoleId()))throw new IllegalArgumentException("UNKNOWN_CANONICAL_ROLE");}
        ids.clear();for(var biome:biomes)if(!ids.add(biome.key()))throw new IllegalArgumentException("DUPLICATE_NATIVE_BIOME");
    }
    public void validateBands(ProgressionProfiles profiles){
        for(var biome:biomes){var band=profiles.biomeBands().stream().filter(b->b.id().equals(biome.rpgBand())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("UNKNOWN_RPG_BAND"));
            if(!band.contains(biome.combatLevel()))throw new IllegalArgumentException("COMBAT_LEVEL_OUTSIDE_BAND");
            if(!band.verifiedNativeBiomeIds().contains(biome.key()))throw new IllegalArgumentException("UNVERIFIED_QUALIFIED_BIOME");}
    }
    public Optional<Spawn> classify(UUID world,UUID enemy,String nativeRole,String qualifiedBiome,Origin origin,long now){
        if(origin!=Origin.WILD_WORLD_SPAWN)return Optional.empty();
        var role=resolveRole(nativeRole);
        var biome=biomes.stream().filter(b->b.key().equals(qualifiedBiome)).findFirst();
        if(role.isEmpty()||biome.isEmpty())return Optional.empty();
        var r=role.get();var b=biome.get();return Optional.of(new Spawn(world,enemy,r.runtimeRoleId(),r.canonical().combatIdentity(),b.key(),b.combatLevel(),r.canonical().rank(),r.canonical().rarity(),profileId,now));
    }
    public Optional<ResolvedRole> resolveRole(String nativeRole){
        if(nativeRole==null)return Optional.empty();
        for(var role:roles)if(role.roleId().equals(nativeRole))return Optional.of(new ResolvedRole(nativeRole,role,role.nativeHealth(),role.attackReference(),false));
        for(var alias:aliases)if(alias.roleId().equals(nativeRole))for(var role:roles)if(role.roleId().equals(alias.canonicalRoleId()))
            return Optional.of(new ResolvedRole(nativeRole,role,alias.nativeHealth(),alias.attackReference(),true));
        return Optional.empty();
    }
    public static EnemyRewardRegistry load(){try(var stream=EnemyRewardRegistry.class.getResourceAsStream("/rpg/progression/enemy-registry.json")){
        if(stream==null)throw new IllegalStateException("MISSING_ENEMY_REGISTRY");var registry=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),EnemyRewardRegistry.class);
        registry.validateBands(ProgressionProfiles.load());return registry;
    }catch(java.io.IOException error){throw new IllegalStateException("Cannot load enemy registry",error);}}
    private static void id(String text){if(text==null||text.isBlank()||text.length()>256||text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("INVALID_ENEMY_ID");}
    private static void hash(String value){if(value==null||!value.matches("[A-Fa-f0-9]{64}"))throw new IllegalArgumentException("INVALID_ASSET_HASH");}
    private static void baseline(double health,double attack){if(!Double.isFinite(health)||health<=0||!Double.isFinite(attack)||attack<=0)throw new IllegalArgumentException("INVALID_NATIVE_BASELINE");}
    private static void asset(String path,String hash){id(path);hash(hash);if(!path.startsWith("Server/")||path.contains("..")||!path.endsWith(".json"))throw new IllegalArgumentException("INVALID_ENEMY_ASSET_PATH");}
}
