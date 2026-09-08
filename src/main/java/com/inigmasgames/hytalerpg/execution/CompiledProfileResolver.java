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
        if(plan.zones().mobileDomain()&&!ProfileComponentPolicy.mobileZone(authored))
            throw new IllegalArgumentException("MOBILE_FINITE_ZONE_COMPONENT_REQUIRED");
        var modifiers=plan.foundationModifiers();var geometry=plan.geometry();var pulses=plan.pulses();
        if(!modifiers.longReach()&&!modifiers.rapidInvocation()&&!modifiers.concentration()&&!modifiers.lingering()&&!modifiers.reversal()&&!geometry.active()&&!pulses.rapidPulse())return authored;
        var key=new Key(authored,modifiers,geometry,pulses);
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
        if(modifiers.concentration())concentrate(authored,resolved);
        if(modifiers.lingering())linger(authored,resolved);
        if(modifiers.reversal()){
            if(authored.reaction()==null)throw new IllegalArgumentException("NO_REACTION_WINDOW_COMPONENT");
            scale(resolved,"reaction",1.3,"windowSeconds");
        }
        if(geometry.impactForce())impact(authored,resolved);
        if(geometry.widening()||geometry.focusedChannel()){
            if(!ProfileComponentPolicy.resolvingWidth(authored))throw new IllegalArgumentException("NO_RESOLVING_WIDTH_COMPONENT");
            var connection=resolved.getAsJsonObject("connection");
            connection.addProperty("width",geometry.width(connection.get("width").getAsDouble()));
            if(geometry.widening())scale(resolved,"connection",.85,"coefficient");
        }
        if(pulses.rapidPulse())rapidPulse(authored,resolved);
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
    private static void rapidPulse(Stage04SkillProfile authored,JsonObject root){
        if(!ProfileComponentPolicy.periodicPulse(authored))throw new IllegalArgumentException("PERIODIC_PULSE_COMPONENT_REQUIRED");
        // Start from already resolved duration/geometry. Do not compensate .80 Less away.
        if(authored.area()!=null){
            var a=JSON.fromJson(root.get("area"),com.inigmasgames.hytalerpg.execution.area.AreaSkillProfile.class);
            var value=root.getAsJsonObject("area");double interval=a.intervalSeconds()*.7;
            int before=a.periodic()?(int)Math.ceil(a.lifetimeSeconds()/a.intervalSeconds()):a.impactCount();
            int after=a.periodic()?(int)Math.floor(a.lifetimeSeconds()/interval+1e-9)
                    :(int)Math.ceil((a.lifetimeSeconds()-a.firstImpactSeconds())/interval-1e-9);
            if(interval<.05||after<1||after>(a.periodic()?256:48))throw new IllegalArgumentException("RAPID_PULSE_EVENT_BUDGET");
            value.addProperty("intervalSeconds",interval);
            if(!a.periodic())value.addProperty("impactCount",after);
            if(a.perTargetHitCap()==before)value.addProperty("perTargetHitCap",after);
            // Integrated areas store DPS: new slice * (old/new interval) * .80 snapshot = .80 old pulse.
            if(a.periodic())scale(root,"area",1/.7,"coefficient","innerCoefficient");
            scale(root,"area",a.periodic()?.8/.7:.8,"displacement","pullSpeed");
            if(a.status().equals("STAGGER"))scale(root,"area",.8,"statusSeconds","statusInnerSeconds");
        }
        if(authored.connection()!=null){
            scale(root,"connection",.7,"intervalSeconds");
            if(authored.connection().channel())scale(root,"connection",1/.7,"coefficient");
        }
        scale(root,"support",.7,"damageInterval","chillInterval");
    }
    private static void impact(Stage04SkillProfile p,JsonObject root){
        if(!ProfileComponentPolicy.impact(p))throw new IllegalArgumentException("NO_IMPACT_COMPONENT");
        if(p.strike()!=null){scale(root,"strike",.90,"coefficient");if(p.strike().statusId().equals("STAGGER"))scale(root,"strike",1.75,"statusSeconds");}
        if(p.projectile()!=null){
            scale(root,"projectile",.90,"coefficient");scale(root,"projectile",1.75,"knockbackDistance");
            if(p.projectile().statusId().equals("STAGGER"))scale(root,"projectile",1.75,"statusSeconds");
        }
        if(p.area()!=null){
            if(!p.area().periodic())scale(root,"area",.90,"coefficient","innerCoefficient","finalCoefficient");
            scale(root,"area",1.75,"displacement");
            if(p.area().status().equals("STAGGER"))scale(root,"area",1.75,"statusSeconds","statusInnerSeconds");
        }
        if(p.connection()!=null){
            if(!p.connection().channel())scale(root,"connection",.90,"coefficient");
            if(p.connection().details().status().equals("STAGGER")){
                var detail=root.getAsJsonObject("connection").getAsJsonObject("details");detail.addProperty("statusSeconds",detail.get("statusSeconds").getAsDouble()*1.75);
            }
        }
    }
    private static void concentrate(Stage04SkillProfile p,JsonObject root){
        if(p.strike()!=null)switch(p.strike().geometry()){
            case ARC,ASSIST_CONE->scale(root,"strike",.7,"angleDegrees");
            case LINE->scale(root,"strike",.7,"lineHalfWidth");
            case RADIUS->scale(root,"strike",.7,"range");
        }
        scale(root,"movement",.7,"landingRadius");
        if(p.area()!=null)switch(p.area().geometry()){
            case DISC->scale(root,"area",.7,"radius","innerRadius","impactRadius","statusInnerRadius","pullCoreRadius","visualCoreRadius");
            case SECTOR->scale(root,"area",.7,"angleDegrees");
            case RECTANGLE->scale(root,"area",.7,"width");
        }
        if(p.connection()!=null)switch(p.connection().kind()){
            case ORBIT->scale(root,"connection",.7,"range"); // Contact collision radius is unchanged.
            case ORB->scale(root,"connection",.7,"radius");
            default->scale(root,"connection",.7,"width");
        }
        scale(root,"support",.7,"radius");scale(root,"summonAction",.7,"radius");scale(root,"cage",.7,"radius");
    }
    private static void linger(Stage04SkillProfile p,JsonObject root){
        if(!ProfileComponentPolicy.finiteDuration(p))throw new IllegalArgumentException("NO_FINITE_EFFECT_DURATION_COMPONENT");
        scale(root,"summon",1.4,"lifetime");scale(root,"summonAction",1.4,"duration");scale(root,"cage",1.4,"duration");
        if(p.support()!=null&&p.support().kind()!=com.inigmasgames.hytalerpg.execution.support.SupportProfile.Kind.FEAR
                &&p.support().kind()!=com.inigmasgames.hytalerpg.execution.support.SupportProfile.Kind.TAUNT)scale(root,"support",1.4,"durationSeconds");
        if(p.connection()!=null&&java.util.Set.of(com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.ORB,
                com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.ORBIT).contains(p.connection().kind()))scale(root,"connection",1.4,"lifetimeSeconds");
        if(p.strike()!=null&&ProfileComponentPolicy.periodicStatus(p.strike().statusId()))scale(root,"strike",1.4,"statusSeconds");
        if(p.projectile()!=null&&p.projectile().hasPeriodicStatus()){
            scale(root,"projectile",1.4,"statusSeconds");
            // Runtime integrates duration and DPS, including the fractional final slice.
            root.getAsJsonObject("projectile").addProperty("periodicTicks",(int)Math.ceil(p.projectile().statusSeconds()*1.4/p.projectile().periodicIntervalSeconds()));
        }
        if(p.area()!=null){
            var a=p.area();var value=root.getAsJsonObject("area");
            if(ProfileComponentPolicy.periodicStatus(a.status()))scale(root,"area",1.4,"statusSeconds","statusInnerSeconds");
            if(a.lifetimeSeconds()>0){
                scale(root,"area",1.4,"lifetimeSeconds");
                // Preserve cadence. Schedule-derived caps expand, explicitly smaller per-cast caps do not.
                int before=a.periodic()?(int)Math.ceil(a.lifetimeSeconds()/a.intervalSeconds()):a.impactCount();
                int after=a.periodic()?(int)Math.ceil(a.lifetimeSeconds()*1.4/a.intervalSeconds()):a.impactCount();
                if(a.impactCount()>1){
                    after=(int)Math.ceil((a.lifetimeSeconds()*1.4-a.firstImpactSeconds())/a.intervalSeconds()-1e-9);
                    if(after>48)throw new IllegalArgumentException("LINGERING_IMPACT_BUDGET");
                    value.addProperty("impactCount",after);
                }
                if(after>256)throw new IllegalArgumentException("LINGERING_TICK_BUDGET");
                if(!a.trap()&&a.perTargetHitCap()==before)value.addProperty("perTargetHitCap",after);
            }
        }
    }
    private record Key(Stage04SkillProfile authored,FoundationModifiers modifiers,com.inigmasgames.hytalerpg.domain.GeometryModifiers geometry,com.inigmasgames.hytalerpg.domain.PulseModifiers pulses){}
}
