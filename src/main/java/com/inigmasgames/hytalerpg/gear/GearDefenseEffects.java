package com.inigmasgames.hytalerpg.gear;

import java.util.Objects;

/** D01 inverse rating bridge for the equipment owner's single managed protection projection. */
public final class GearDefenseEffects {
    private GearDefenseEffects() {}
    public record View(double armorRating,double shieldRating,double totalRating,double effectiveRating,
                       double managedProtection) {}
    public static View resolve(GearEffectSnapshot validEquipment,int combatLevel,double armorProtection,
                               double otherRating,double defenseBreakFraction){
        Objects.requireNonNull(validEquipment);
        if(!Double.isFinite(armorProtection)||armorProtection<0||armorProtection>=1
                ||!Double.isFinite(otherRating)||otherRating<0||!Double.isFinite(defenseBreakFraction)
                ||defenseBreakFraction<0||defenseBreakFraction>1)throw new IllegalArgumentException("Invalid Defense view");
        double k=100+10*Math.clamp(combatLevel,1,99);
        double armor=k*armorProtection/(1-armorProtection),shield=0;
        for(var item:validEquipment.items())if(item.intrinsicStats().containsKey("shieldDefense")){
            double base=item.intrinsicStats().get("shieldDefense");
            shield+=(base+GearAffixRuntime.value(item,"WA-069"))*(1+GearAffixRuntime.value(item,"WA-070")/100);
        }
        double total=Math.max(0,(armor+shield+otherRating)*(1+validEquipment.percent("WA-071")));
        double effective=total*(1-defenseBreakFraction);
        return new View(armor,shield,total,effective,Math.clamp(effective/(k+effective),0,.60));
    }
    /** Re-evaluate a committed rating against a live ArmorBreak fraction at the damage Filter boundary. */
    public static double reduction(View defense,int combatLevel,double armorBreakFraction){
        Objects.requireNonNull(defense);
        if(!Double.isFinite(armorBreakFraction)||armorBreakFraction<0||armorBreakFraction>1)
            throw new IllegalArgumentException("Invalid ArmorBreak fraction");
        double k=100+10*Math.clamp(combatLevel,1,99);
        double effective=defense.totalRating()*(1-armorBreakFraction);
        return Math.clamp(effective/(k+effective),0,.60);
    }
    /** Critical damage taken modifies the bonus over 1x, preserving ordinary hits. */
    public static double criticalTakenMultiplier(GearEffectSnapshot defense,double incomingCriticalMultiplier){
        if(incomingCriticalMultiplier<1||!Double.isFinite(incomingCriticalMultiplier))throw new IllegalArgumentException("Invalid crit multiplier");
        return 1+Math.max(0,incomingCriticalMultiplier-1)*(1-defense.percent(GearEffectSnapshot.Operator.CRIT_DAMAGE_TAKEN));
    }
    public static double criticalAmount(GearEffectSnapshot defense,double ordinary,double critical){
        if(!Double.isFinite(ordinary)||!Double.isFinite(critical)||ordinary<0||critical<ordinary)
            throw new IllegalArgumentException("Invalid frozen critical components");
        return ordinary+(critical-ordinary)*(1-Math.clamp(defense.percent(GearEffectSnapshot.Operator.CRIT_DAMAGE_TAKEN),0,1));
    }
}
