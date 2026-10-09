package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact installed native biome identities joined to already accepted campaign region bands. */
public record CampaignBiomes(String revision,List<Biome> biomes,List<Exclusion> excluded) {
    public record Source(String asset,String sha256){}
    public record Biome(String key,String region,List<Source> sources){}
    public record Exclusion(String asset,String reason){}
    private static final CampaignBiomes INSTANCE=load();
    private static final Map<String,Biome> INDEX=INSTANCE.biomes().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Biome::key,b->b));
    public static CampaignBiomes current(){return INSTANCE;}
    private static CampaignBiomes load(){
        try(var input=CampaignBiomes.class.getResourceAsStream("/rpg/progression/campaign-biomes-v1.json")){
            if(input==null)throw new IllegalStateException("CAMPAIGN_BIOME_MANIFEST_MISSING");
            return new Gson().fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),CampaignBiomes.class);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public Optional<Biome> find(String key){return Optional.ofNullable(INDEX.get(key));}
}
