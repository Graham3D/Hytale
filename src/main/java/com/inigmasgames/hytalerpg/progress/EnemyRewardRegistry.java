package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audited role and biome identities; combat levels/ranks are explicitly RPG-authored, never vanilla claims. */
public record EnemyRewardRegistry(int schemaVersion,String profileId,String serverSha256,String assetsSha256,
                                  List<Role> roles,List<Biome> biomes){
    public record Role(String roleId,String combatIdentity,ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,
                       String assetPath,String assetSha256,String evidence){
        public Role {id(roleId);id(combatIdentity);Objects.requireNonNull(rank);Objects.requireNonNull(rarity);
            asset(assetPath,assetSha256);if(!"ASSET_AUDITED_CONNECTED_UNVERIFIED".equals(evidence))throw new IllegalArgumentException("ROLE_EVIDENCE_NOT_DECLARED");}
    }
    public record Biome(String worldgenName,String zoneId,String biomeId,String rpgBand,int combatLevel,
                        String assetPath,String assetSha256){
        public Biome {id(worldgenName);id(zoneId);id(biomeId);id(rpgBand);asset(assetPath,assetSha256);
            if(combatLevel<1||combatLevel>99)throw new IllegalArgumentException("INVALID_AUTHORED_COMBAT_LEVEL");}
        public String key(){return worldgenName+"/"+zoneId+"/"+biomeId;}
    }
    public enum Origin { WILD_WORLD_SPAWN, SUMMONED, REVIVED, CONVERTED, TRAINING, PLAYER_OWNED, UNKNOWN }
    public record Spawn(UUID world,UUID enemy,String roleId,String combatIdentity,String biomeKey,int level,
                        ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,String registryProfile,long spawnedAtMillis){
        public Spawn {Objects.requireNonNull(world);Objects.requireNonNull(enemy);id(roleId);id(combatIdentity);id(biomeKey);id(registryProfile);
            Objects.requireNonNull(rank);Objects.requireNonNull(rarity);if(level<1||level>99||spawnedAtMillis<0)throw new IllegalArgumentException("INVALID_SPAWN_CONTEXT");}
        public String eventId(){return "enemy-death/"+world+"/"+enemy;}
    }
    public EnemyRewardRegistry {
        if(schemaVersion!=1)throw new IllegalArgumentException("ENEMY_REGISTRY_SCHEMA");id(profileId);hash(serverSha256);hash(assetsSha256);
        roles=List.copyOf(roles);biomes=List.copyOf(biomes);
        if(roles.isEmpty()||roles.size()>2048||biomes.isEmpty()||biomes.size()>2048)throw new IllegalArgumentException("ENEMY_REGISTRY_BOUNDS");
        Set<String> ids=new HashSet<>();
        for(var role:roles)if(!ids.add(role.roleId()))throw new IllegalArgumentException("DUPLICATE_NATIVE_ROLE");
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
        var role=roles.stream().filter(r->r.roleId().equals(nativeRole)).findFirst();
        var biome=biomes.stream().filter(b->b.key().equals(qualifiedBiome)).findFirst();
        if(role.isEmpty()||biome.isEmpty())return Optional.empty();
        var r=role.get();var b=biome.get();return Optional.of(new Spawn(world,enemy,r.roleId(),r.combatIdentity(),b.key(),b.combatLevel(),r.rank(),r.rarity(),profileId,now));
    }
    public static EnemyRewardRegistry load(){try(var stream=EnemyRewardRegistry.class.getResourceAsStream("/rpg/progression/enemy-registry.json")){
        if(stream==null)throw new IllegalStateException("MISSING_ENEMY_REGISTRY");var registry=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),EnemyRewardRegistry.class);
        registry.validateBands(ProgressionProfiles.load());return registry;
    }catch(java.io.IOException error){throw new IllegalStateException("Cannot load enemy registry",error);}}
    private static void id(String text){if(text==null||text.isBlank()||text.length()>256||text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("INVALID_ENEMY_ID");}
    private static void hash(String value){if(value==null||!value.matches("[A-Fa-f0-9]{64}"))throw new IllegalArgumentException("INVALID_ASSET_HASH");}
    private static void asset(String path,String hash){id(path);hash(hash);if(!path.startsWith("Server/")||path.contains("..")||!path.endsWith(".json"))throw new IllegalArgumentException("INVALID_ENEMY_ASSET_PATH");}
}
