package com.inigmasgames.hytalerpg.execution;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.FoundationModifiers;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolve once before validation/commit, never inside a target or tick loop. Only the
 * explicit component fields below are transformed; authored profiles remain immutable.
 * Canonical record constructors validate the result just as they validate asset data.
 */
public final class CompiledProfileResolver {
    private static final Gson JSON=new Gson();
    private static final int CAPACITY=1024;
    private final Map<Key,Stage04SkillProfile> cache=new LinkedHashMap<>(32,.75f,true);
    public synchronized Stage04SkillProfile resolve(Stage04SkillProfile authored,CompiledSkillPlan plan){
        if(!authored.skillId().equals(plan.skillId().value())||plan.degraded()||plan.schemaVersion()!=CompiledSkillPlan.CURRENT_SCHEMA)
            throw new IllegalArgumentException("Profile requires a current matching compiled plan");
        var modifiers=plan.foundationModifiers();
        if(!modifiers.longReach()&&!modifiers.rapidInvocation())return authored;
        var key=new Key(authored,modifiers);
        var prior=cache.get(key);if(prior!=null)return prior;
        JsonObject resolved=JSON.toJsonTree(authored).getAsJsonObject();
        resolved.addProperty("windupSeconds",modifiers.windup(authored.windupSeconds()));
        double reach=modifiers.rangeFactor();
        if(modifiers.longReach()){
            // RADIUS strike geometry uses range as its radius; it is not a reach field.
            if(authored.strike()!=null&&authored.strike().geometry()!=Stage04SkillProfile.Geometry.RADIUS)scale(resolved,"strike",reach,"range");
            scale(resolved,"movement",reach,"maxDistance");
            scale(resolved,"projectile",reach,"maxDistance");
            scale(resolved,"area",reach,"placementRange");
            // A sector stores cone reach in radius; rectangular wall length is a footprint, not reach.
            if(authored.area()!=null&&authored.area().geometry()==com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Kind.SECTOR)
                scale(resolved,"area",reach,"radius");
            if(authored.connection()==null||authored.connection().kind()!=com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.ORBIT)
                scale(resolved,"connection",reach,"range");
            // A traveling wave's lifetime is derived from reach/speed, not a lingering effect.
            if(authored.connection()!=null&&authored.connection().kind()==com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.WAVE)
                scale(resolved,"connection",reach,"lifetimeSeconds");
            scale(resolved,"support",reach,"range");
            scale(resolved,"summon",reach,"range");
            scale(resolved,"summonAction",reach,"range");
            scale(resolved,"conversion",reach,"range");
            scale(resolved,"cage",reach,"range");
        }
        var effective=JSON.fromJson(resolved,Stage04SkillProfile.class);
        if(cache.size()>=CAPACITY)cache.remove(cache.keySet().iterator().next());
        cache.put(key,effective);return effective;
    }
    private static void scale(JsonObject root,String component,double factor,String... fields){
        if(!root.has(component)||root.get(component).isJsonNull())return;
        JsonObject value=root.getAsJsonObject(component);
        for(String field:fields)if(value.has(field))value.addProperty(field,value.get(field).getAsDouble()*factor);
    }
    public synchronized int cachedProfiles(){return cache.size();}
    private record Key(Stage04SkillProfile authored,FoundationModifiers modifiers){}
}
