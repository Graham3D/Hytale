package com.inigmasgames.hytalerpg.progress;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** RPG-authored bands are not evidence of vanilla levels or native biome IDs. */
public record ProgressionProfiles(int schemaVersion,String profileId,List<BiomeBand> biomeBands,List<Difficulty> difficulties){
    public record BiomeBand(String id,int minimumLevel,int maximumLevel,List<String> verifiedNativeBiomeIds){
        public BiomeBand{
            if(id==null||id.isBlank()||minimumLevel<1||maximumLevel>99||minimumLevel>maximumLevel)throw new IllegalArgumentException("INVALID_BIOME_BAND");
            verifiedNativeBiomeIds=List.copyOf(verifiedNativeBiomeIds);
            if(verifiedNativeBiomeIds.stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("INVALID_NATIVE_BIOME_ID");
        }
        public boolean contains(int level){return level>=minimumLevel&&level<=maximumLevel;}
    }
    public record Difficulty(String id,boolean enabled,double healthMultiplier,double damageMultiplier,String authoredEncounterRegistry,String disabledReason){
        public Difficulty{
            if(id==null||id.isBlank()||!Double.isFinite(healthMultiplier)||healthMultiplier<=0||!Double.isFinite(damageMultiplier)||damageMultiplier<=0||disabledReason==null||authoredEncounterRegistry==null)
                throw new IllegalArgumentException("INVALID_DIFFICULTY_PROFILE");
            if(!enabled&&disabledReason.isBlank())throw new IllegalArgumentException("DISABLED_DIFFICULTY_REQUIRES_REASON");
            if(enabled&&!id.equals("NORMAL")&&authoredEncounterRegistry.isBlank())throw new IllegalArgumentException("UNAUTHORED_DIFFICULTY_CANNOT_ENABLE");
        }
    }
    public ProgressionProfiles{
        biomeBands=List.copyOf(biomeBands);difficulties=List.copyOf(difficulties);
        if(schemaVersion!=1||profileId==null||profileId.isBlank()||biomeBands.size()!=5||difficulties.size()!=3)throw new IllegalArgumentException("INVALID_PROGRESSION_PROFILE");
        int expected=1;Set<String> ids=new HashSet<>(),nativeIds=new HashSet<>();
        for(var band:biomeBands){if(band.minimumLevel()!=expected||!ids.add(band.id()))throw new IllegalArgumentException("NONCONTIGUOUS_BIOME_BANDS");expected=band.maximumLevel()+1;
            for(var nativeId:band.verifiedNativeBiomeIds())if(!nativeIds.add(nativeId))throw new IllegalArgumentException("AMBIGUOUS_NATIVE_BIOME_ASSIGNMENT");}
        if(expected!=100)throw new IllegalArgumentException("INCOMPLETE_COMBAT_LEVEL_BANDS");
        var modes=difficulties.stream().map(Difficulty::id).collect(java.util.stream.Collectors.toSet());if(!modes.equals(Set.of("NORMAL","NIGHTMARE","HELL")))throw new IllegalArgumentException("INVALID_DIFFICULTY_IDS");
    }
    public static ProgressionProfiles load(){try(var stream=ProgressionProfiles.class.getResourceAsStream("/rpg/progression/profiles.json")){
        if(stream==null)throw new IllegalStateException("MISSING_PROGRESSION_PROFILES");return new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),ProgressionProfiles.class);
    }catch(java.io.IOException error){throw new IllegalStateException("Cannot load progression profiles",error);}}
    public Optional<BiomeBand> nativeBiome(String id){return biomeBands.stream().filter(b->b.verifiedNativeBiomeIds().contains(id)).findFirst();}
    public Difficulty difficulty(String id){return difficulties.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("UNKNOWN_DIFFICULTY"));}
}
