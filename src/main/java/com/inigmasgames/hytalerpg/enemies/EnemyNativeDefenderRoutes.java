package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned incoming routes, separate from attack/selector/projectile certification.
 * ME-026: completed original Health loss -> NativeEnemyReflectiveReaction -> later Gather.
 * ME-017: EnemyAffixSnapshot -> HytaleDifficultyCombat.statusImmune -> shared status admission.
 * Neither route grants outgoing damage, movement, impulse, or an additional native attack.
 */
final class EnemyNativeDefenderRoutes {
    private record Route(String path,String sha,Set<String> affixes){}
    private static final Map<String,Route> ROUTES=load();
    private EnemyNativeDefenderRoutes(){}
    static void initialize(){} // Force immutable resource loading during binding setup, before birth work.
    private static Map<String,Route> load(){
        try(var stream=EnemyNativeDefenderRoutes.class.getResourceAsStream("/rpg/enemies/defender-routes-0.7.1.json")){
            if(stream==null)throw new IllegalStateException("ENEMY_DEFENDER_ROUTES_MISSING");
            var result=new TreeMap<String,Route>();
            for(var value:JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()){
                var row=value.getAsJsonObject();
                if(!row.keySet().equals(Set.of("roleId","sourcePath","sourceSha256","affixes")))
                    throw new IllegalArgumentException("ENEMY_DEFENDER_ROUTE_SCHEMA");
                var affixes=new HashSet<String>();
                for(var id:row.getAsJsonArray("affixes"))
                    if(!Set.of("ME-017","ME-026").contains(id.getAsString())||!affixes.add(id.getAsString()))
                        throw new IllegalArgumentException("ENEMY_DEFENDER_ROUTE_OPERATOR");
                String role=row.get("roleId").getAsString(),path=row.get("sourcePath").getAsString(),sha=row.get("sourceSha256").getAsString();
                if(role.isBlank()||!path.startsWith("Server/NPC/Roles/")||!sha.matches("[a-f0-9]{64}")||affixes.isEmpty()
                        ||result.putIfAbsent(role,new Route(path,sha,Set.copyOf(affixes)))!=null)
                    throw new IllegalArgumentException("ENEMY_DEFENDER_ROUTE_IDENTITY");
            }
            return Map.copyOf(result);
        }catch(IOException failure){throw new IllegalStateException(failure);}
    }
    static Set<String> certified(EnemyNativeBindings.Role role){
        var supported=new HashSet<>(Set.of("ME-017","ME-026"));
        for(var id:role.nativeRoleIds()){
            var route=ROUTES.get(id);
            if(route==null||!route.path().equals(role.sourceAssetPath())||!route.sha().equals(role.sourceAssetSha256()))return Set.of();
            supported.retainAll(route.affixes());
        }
        if(!role.capabilities().contains(EnemyAffixRegistry.Capability.APPLIED_HIT_RECEIPTS))supported.remove("ME-026");
        if(role.nativeStunStaggerImmune())supported.remove("ME-017");
        return Set.copyOf(supported);
    }
}
