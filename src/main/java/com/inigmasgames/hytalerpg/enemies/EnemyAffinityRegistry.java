package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Exact authored role overlay. Names, textures and substrings never establish affinity. */
public final class EnemyAffinityRegistry {
    public record Affinity(String canonicalRoleId,String channel,List<Double> rawResistanceFloor){
        public Affinity{rawResistanceFloor=List.copyOf(rawResistanceFloor);}
        public double floor(DifficultyId mode){return rawResistanceFloor.get(mode.ordinal());}
    }
    private final String revision;private final Map<String,Affinity> entries;
    public EnemyAffinityRegistry(JsonObject json){
        exact(json,Set.of("schemaVersion","revision","applyToNativeBossEncounter","roleEntries"));
        require(number(json.get("schemaVersion"),true,1)==1,"AFFINITY_SCHEMA");revision=string(json,"revision");
        var boss=json.get("applyToNativeBossEncounter");require(boss.isJsonPrimitive()&&boss.getAsJsonPrimitive().isBoolean()&&!boss.getAsBoolean(),"NO_AUTOMATIC_BOSS_AFFINITY");
        var result=new TreeMap<String,Affinity>();for(var value:json.getAsJsonArray("roleEntries")){
            var entry=value.getAsJsonObject();exact(entry,Set.of("canonicalRoleId","channel","rawResistanceFloor"));
            String role=string(entry,"canonicalRoleId"),channel=string(entry,"channel");require(CHANNELS.subList(1,7).contains(channel),"AFFINITY_ELEMENTAL_CHANNEL");
            require(result.putIfAbsent(role,new Affinity(role,channel,numbers(entry.get("rawResistanceFloor"),false,3,.75)))==null,"DUPLICATE_ROLE_AFFINITY");
        }require(result.size()<=2048,"AFFINITY_REGISTRY_BUDGET");entries=Collections.unmodifiableMap(result);
    }
    public String revision(){return revision;}
    public Collection<Affinity> entries(){return entries.values();}
    public Optional<Affinity> forRole(String canonicalRole,boolean nativeBossEncounter){return nativeBossEncounter?Optional.empty():Optional.ofNullable(entries.get(canonicalRole));}
    public void validateRoles(java.util.function.Predicate<String> exactNativeRole){for(String role:entries.keySet())require(exactNativeRole.test(role),"AFFINITY_NATIVE_ROLE_MISSING:"+role);}
    public static EnemyAffinityRegistry canonical(){return new EnemyAffinityRegistry(resource("affinities-v1.json"));}
}
