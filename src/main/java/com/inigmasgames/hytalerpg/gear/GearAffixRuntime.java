package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import java.util.*;

/** Typed projections into existing owners. Frozen rolls are never rerolled or multiplied by intrinsic quality. */
public final class GearAffixRuntime {
    private GearAffixRuntime() {}
    public static final Set<String> ENABLED = Set.of("WA-001","WA-002","WA-003","WA-009","WA-012",
            "WA-085","WA-086","WA-087","WA-088","WA-089","WA-090","WA-091","WA-092","WA-093",
            "WA-121","WA-151","WA-154","WA-157","WA-158","GA-159","GA-160");
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final double RECOVERY_CAP=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical().cooldownRecoveryCap;
    public static boolean supported(GearInstance item) {
        return item.affixes().stream().allMatch(a->ENABLED.contains(a.familyId()));
    }
    public static double value(GearInstance item,String family) {
        return item.affixes().stream().filter(a->a.familyId().equals(family)).mapToDouble(GearInstance.AffixRoll::value).sum();
    }
    public static Map<RpgAttribute,Integer> attributes(GearInstance item) {
        var result=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var a:item.affixes()) if(CATALOG.affix(a.familyId()).operator().equals("ATTRIBUTE")) {
            if(a.value()<0 || a.value()!=Math.rint(a.value())) throw new IllegalArgumentException("Nonintegral gear attribute");
            int value=Math.toIntExact((long)a.value());
            if(a.familyId().equals("WA-090")) for(var stat:RpgAttribute.values()) result.merge(stat,value,Math::addExact);
            else result.merge(switch(a.familyId()) {
                case "WA-085"->RpgAttribute.STR;case "WA-086"->RpgAttribute.DEX;case "WA-087"->RpgAttribute.INT;
                case "WA-088"->RpgAttribute.WIS;case "WA-089"->RpgAttribute.LUCK;
                default->throw new IllegalArgumentException("Unknown attribute operator");
            },value,Math::addExact);
        }
        return Map.copyOf(result);
    }
    public record Range(double minimum,double maximum) {}
    public static Range physical(GearInstance item) {
        double flat=value(item,"WA-001"),factor=1+value(item,"WA-002")/100;
        double min=item.intrinsicStats().getOrDefault("physicalMin",0d)+flat+value(item,"WA-157");
        double max=item.intrinsicStats().getOrDefault("physicalMax",0d)+flat+value(item,"WA-158");
        return new Range(min*factor,Math.max(min,max)*factor);
    }
    public static Double magic(GearInstance item) {
        var base=item.intrinsicStats().get("magicPower");return base==null?null:base+value(item,"WA-003");
    }
    public static double protection(GearInstance item) {
        return (item.intrinsicStats().getOrDefault("protectionPoints",0d)+value(item,"GA-159"))*(1+value(item,"GA-160")/100);
    }
    public record Effects(Map<RpgAttribute,Integer> attributes,double health,double mana,double stamina,
                          double castRate,double cooldownRecovery,int allSkillRanks) {
        public static final Effects NONE=new Effects(Map.of(),0,0,0,0,0,0);
        public Effects { attributes=Map.copyOf(attributes); }
        public Map<RpgAttribute,Integer> raw(Map<RpgAttribute,Integer> baseline) {
            var result=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);result.putAll(baseline);
            attributes.forEach((key,value)->result.merge(key,value,Math::addExact));return Map.copyOf(result);
        }
        public DerivedStats derive(DerivedStatService owner,Map<RpgAttribute,Integer> baseline) {
            var d=owner.derive(raw(baseline),health,stamina,mana);
            return new DerivedStats(d.rawAttributes(),d.effectiveAttributes(),d.maxHealth(),d.maxStamina(),d.maxMana(),
                    d.heavyDamageMultiplier(),d.lightDamageMultiplier(),d.magicDamageMultiplier(),d.healingMultiplier(),
                    Math.min(RECOVERY_CAP,d.cooldownRecovery()+cooldownRecovery),d.learnRate(),d.criticalChance(),d.criticalMultiplier(),d.upgradeSuccess(),d.magicFind());
        }
        public double windup(double authored) {return authored/(1+castRate);}
    }
    public static Effects effects(Collection<GearInstance> valid) {
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        double health=0,mana=0,stamina=0,cast=0,recovery=0;int ranks=0;
        for(var item:valid) {
            if(!supported(item))continue;
            attributes(item).forEach((stat,value)->attributes.merge(stat,value,Math::addExact));
            health+=item.intrinsicStats().getOrDefault("health",0d)+value(item,"WA-091");
            mana+=item.intrinsicStats().getOrDefault("mana",0d)+value(item,"WA-092");
            stamina+=item.intrinsicStats().getOrDefault("stamina",0d)+value(item,"WA-093");
            cast+=value(item,"WA-009")/100;recovery+=value(item,"WA-012")/100;
            ranks=Math.addExact(ranks,(int)value(item,"WA-121"));
        }
        return new Effects(attributes,health,mana,stamina,cast,recovery,ranks);
    }
}
