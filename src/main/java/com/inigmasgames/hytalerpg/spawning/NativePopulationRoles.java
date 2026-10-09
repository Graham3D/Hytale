package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact installed native world-spawn IDs; unknown roles keep native population rules. */
public final class NativePopulationRoles {
    public enum Category { HOSTILE, WILDLIFE, AMBIENT_AVIAN, OTHER }
    private final Map<String,Category> roles;
    private final String assetsSha256;
    private NativePopulationRoles(Map<String,Category> roles,String assetsSha256){
        this.roles=Map.copyOf(roles);this.assetsSha256=assetsSha256;
    }
    public static NativePopulationRoles load(){
        try(var stream=NativePopulationRoles.class.getResourceAsStream("/rpg/spawning/native-population-roles-v1.json")){
            if(stream==null)throw new IllegalStateException("NATIVE_POPULATION_ROLE_AUDIT_MISSING");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.get("schemaVersion").getAsInt()!=1)throw new IllegalStateException("NATIVE_POPULATION_ROLE_AUDIT_SCHEMA");
            var hash=json.get("assetsSha256").getAsString();
            if(!hash.matches("[a-f0-9]{64}"))throw new IllegalStateException("NATIVE_POPULATION_ASSET_HASH");
            var result=new TreeMap<String,Category>();
            json.getAsJsonObject("categories").entrySet().forEach(entry->{
                if(!entry.getKey().matches("[A-Za-z0-9_]+"))throw new IllegalStateException("NATIVE_POPULATION_ROLE_ID");
                result.put(entry.getKey(),Category.valueOf(entry.getValue().getAsString()));
            });
            if(result.size()<100)throw new IllegalStateException("NATIVE_POPULATION_ROLE_AUDIT_INCOMPLETE");
            return new NativePopulationRoles(result,hash);
        }catch(java.io.IOException error){throw new IllegalStateException("NATIVE_POPULATION_ROLE_AUDIT_READ",error);}
    }
    public Category category(String nativeRoleId){return roles.getOrDefault(nativeRoleId,Category.OTHER);}
    public boolean audited(String nativeRoleId){return roles.containsKey(nativeRoleId);}
    public Map<String,Category> roles(){return roles;}
    public String assetsSha256(){return assetsSha256;}
}
