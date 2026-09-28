package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.progress.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit R081 balance authoring. Asset observations are not claims of connected acceptance. */
public record AuthoredEncounterCatalog(int schemaVersion,String profileId,List<Role> roles) {
    public static final String CAMPAIGN_GOLEM="campaign/golem";
    public record Role(String id,double nativeHealth,double attackReference,String assetSha256,
                       String campaignRegion,Set<MonsterResistanceProfile.Channel> affinities,
                       Set<MonsterResistanceProfile.Channel> hellImmunities) {
        public Role {
            if(id==null||id.isBlank()||!Double.isFinite(nativeHealth)||nativeHealth<=0||!Double.isFinite(attackReference)||attackReference<=0
                    ||assetSha256==null||!assetSha256.matches("[A-F0-9]{64}"))throw new IllegalArgumentException("INVALID_AUDITED_ROLE");
            Objects.requireNonNull(campaignRegion);affinities=Set.copyOf(affinities);hellImmunities=Set.copyOf(hellImmunities);
            if(!affinities.containsAll(hellImmunities))throw new IllegalArgumentException("IMMUNITY_REQUIRES_AUTHORED_AFFINITY");
        }
    }
    public AuthoredEncounterCatalog {
        if(schemaVersion!=1||profileId==null||profileId.isBlank())throw new IllegalArgumentException("AUTHORED_CATALOG_SCHEMA");
        roles=List.copyOf(roles);
        if(roles.isEmpty()||roles.size()>2048||roles.stream().map(Role::id).distinct().count()!=roles.size())throw new IllegalArgumentException("AUTHORED_ROLE_BOUNDS");
    }
    public static AuthoredEncounterCatalog load(){
        try(var stream=AuthoredEncounterCatalog.class.getResourceAsStream("/rpg/progression/authored-encounters.json")){
            if(stream==null)throw new IllegalStateException("AUTHORED_CATALOG_MISSING");
            return new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),AuthoredEncounterCatalog.class);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    /** Activate this specific authored test baseline; retained legacy profile definitions remain historical. */
    public EncounterProfileResolver resolver(WorldDifficultyRegistry worlds){
        var legacy=ProgressionProfiles.load();
        var scalars=new ProgressionProfiles(legacy.schemaVersion(),profileId,legacy.biomeBands(),legacy.difficulties().stream()
                .map(d->new ProgressionProfiles.Difficulty(d.id(),true,d.healthMultiplier(),d.damageMultiplier(),profileId,"")).toList());
        return new EncounterProfileResolver(worlds,scalars,profiles());
    }
    public List<EncounterProfileResolver.Profile> profiles(){
        var result=new ArrayList<EncounterProfileResolver.Profile>();var registry=EnemyRewardRegistry.load();var biomes=registry.biomes();var bands=DifficultyDefinitions.load();
        var roleDefinitions=new ArrayList<>(roles);var explicit=new HashSet<String>();roles.forEach(role->explicit.add(role.id()));
        for(var role:registry.roles())if(explicit.add(role.roleId()))roleDefinitions.add(new Role(role.roleId(),role.nativeHealth(),role.attackReference(),role.assetSha256(),"",Set.of(),Set.of()));
        for(var alias:registry.aliases())if(explicit.add(alias.roleId()))roleDefinitions.add(new Role(alias.roleId(),alias.nativeHealth(),alias.attackReference(),alias.assetSha256(),"",Set.of(),Set.of()));
        for(var role:roleDefinitions)for(var mode:DifficultyId.values()){
            var resistance=new EnumMap<MonsterResistanceProfile.Channel,Double>(MonsterResistanceProfile.Channel.class);
            if(mode!=DifficultyId.NORMAL)for(var channel:role.affinities())resistance.put(channel,mode==DifficultyId.NIGHTMARE?.25:.50);
            var mitigation=new MonsterResistanceProfile(resistance,mode==DifficultyId.HELL?role.hellImmunities():Set.of());
            // Existing binding IDs are accepted explicitly; world UUID/difficulty/scope is never rebound.
            String worldProfile=switch(mode){case NORMAL->"rpg.encounters.r031.pilot";case NIGHTMARE->"rpg.encounters.nightmare.pending";case HELL->"rpg.encounters.hell.pending";};
            if(!role.campaignRegion().isBlank()){
                add(result,role,mode,worldProfile,CAMPAIGN_GOLEM,bands.band(role.campaignRegion(),mode).maximum(),mitigation);
            }else for(var biome:biomes){
                var band=bands.band(biome.rpgBand(),mode);
                add(result,role,mode,worldProfile,biome.key(),(band.minimum()+band.maximum())/2,mitigation);
            }
        }
        return List.copyOf(result);
    }
    private void add(List<EncounterProfileResolver.Profile> result,Role role,DifficultyId mode,String worldProfile,String biome,int level,MonsterResistanceProfile resistance){
        result.add(new EncounterProfileResolver.Profile(profileId+"/"+role.id()+"/"+mode,mode,worldProfile,role.id(),biome,level,
                role.nativeHealth(),role.attackReference(),1,1,resistance,EncounterProfileResolver.Evidence.AUTHORED_BASELINE_CONNECTED_UNVERIFIED));
    }
}
