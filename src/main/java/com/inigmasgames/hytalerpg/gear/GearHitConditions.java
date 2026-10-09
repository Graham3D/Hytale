package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Pre-hit facts supplied by the native Gather boundary for the twelve authored conditional affixes. */
public final class GearHitConditions {
    private GearHitConditions() {}
    private static final String[] IDS={"WA-041","WA-042","WA-043","WA-044","WA-045","WA-046",
            "WA-047","WA-048","WA-049","WA-050","WA-051","WA-052"};
    public record Context(Vec3 origin,Vec3 target,Vec3 targetForward,double health,double normalMaximum,
                          Set<String> statuses,boolean eliteOrHigher) {
        public Context {statuses=Set.copyOf(statuses);}
    }
    public static boolean present(GearCombatEffects.Hit hit){
        if(hit.itemId()==null)return false;
        var local=hit.snapshot().forItem(hit.itemId());
        for(var id:IDS)if(local.value(id)>0)return true;
        return false;
    }
    public static boolean needsNormalMaximum(GearCombatEffects.Hit hit){
        if(hit.itemId()==null)return false;
        var local=hit.snapshot().forItem(hit.itemId());
        return local.value("WA-044")>0||local.value("WA-045")>0;
    }
    public static double increased(GearCombatEffects.Hit hit,Context facts){
        Objects.requireNonNull(hit);Objects.requireNonNull(facts);
        var local=hit.itemId()==null?GearEffectSnapshot.EMPTY:hit.snapshot().forItem(hit.itemId());
        double result=0;
        for(int id=41;id<=52;id++){
            String affix=IDS[id-41];
            double value=local.percent(affix);if(value==0)continue;
            boolean applies=switch(id){
                case 41->distance(facts)<=4;
                case 42->distance(facts)>=12;
                case 43->rear(facts);
                case 44->healthRatio(facts)<.30;
                case 45->healthRatio(facts)>=.80;
                case 46->facts.statuses().stream().anyMatch(Set.of("STUN","FROZEN","ROOT","FEAR")::contains);
                case 47->facts.statuses().contains("BURN");
                case 48->facts.statuses().contains("CHILL");
                case 49->facts.statuses().contains("ELECTRIFIED");
                case 50->facts.statuses().contains("POISON");
                case 51->facts.statuses().contains("BLEED");
                case 52->facts.eliteOrHigher();
                default->throw new AssertionError(id);
            };
            if(applies)result+=value;
        }
        return result;
    }
    private static double distance(Context facts){
        if(facts.origin()==null||facts.target()==null)throw new IllegalArgumentException("LIVE_HIT_DISTANCE_UNAVAILABLE");
        return Math.sqrt(facts.origin().distanceSquared(facts.target()));
    }
    private static double healthRatio(Context facts){
        if(!Double.isFinite(facts.health())||!Double.isFinite(facts.normalMaximum())||facts.normalMaximum()<=0)
            throw new IllegalArgumentException("LIVE_VICTIM_HEALTH_UNAVAILABLE");
        return facts.health()/facts.normalMaximum();
    }
    private static boolean rear(Context facts){
        if(facts.origin()==null||facts.target()==null||facts.targetForward()==null)
            throw new IllegalArgumentException("LIVE_VICTIM_FACING_UNAVAILABLE");
        var towardSource=facts.origin().subtract(facts.target());
        if(towardSource.lengthSquared()<1e-12||facts.targetForward().lengthSquared()<1e-12)
            throw new IllegalArgumentException("LIVE_VICTIM_FACING_UNAVAILABLE");
        var a=towardSource.normalized();var b=facts.targetForward().normalized();
        return a.x()*b.x()+a.y()*b.y()+a.z()*b.z()<=-.5;
    }
}
