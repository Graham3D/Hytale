package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import java.util.*;

/** Typed projections into existing owners. Frozen rolls are never rerolled or multiplied by intrinsic quality. */
public final class GearAffixRuntime {
    private GearAffixRuntime() {}
    // WA-155 awaits the owner-measured metre/native-radius calibration.
    public static final Set<String> ENABLED = Set.of("WA-001","WA-002","WA-003","WA-004","WA-005","WA-006","WA-007","WA-008","WA-009","WA-010",
            "WA-011","WA-012","WA-013","WA-014","WA-015","WA-016","WA-017","WA-018","WA-019","WA-020",
            "WA-021","WA-022","WA-023","WA-024","WA-025","WA-026","WA-027","WA-028","WA-029","WA-030",
            "WA-031","WA-032","WA-033","WA-034","WA-035","WA-036","WA-037","WA-038","WA-039","WA-040",
            "WA-041","WA-042","WA-043","WA-044","WA-045","WA-046","WA-047","WA-048","WA-049","WA-050",
            "WA-051","WA-052","WA-053","WA-054","WA-055","WA-056","WA-057","WA-058","WA-059","WA-060",
            "WA-061","WA-062","WA-063","WA-064","WA-065","WA-066","WA-067","WA-068","WA-069","WA-070",
            "WA-071","WA-072","WA-073","WA-074","WA-075","WA-076","WA-077","WA-078","WA-079","WA-080",
            "WA-081","WA-082","WA-083","WA-084","WA-085","WA-086","WA-087","WA-088","WA-089","WA-090",
            "WA-091","WA-092","WA-093","WA-094","WA-095","WA-096","WA-097","WA-098","WA-099","WA-100",
            "WA-101","WA-102","WA-103","WA-104","WA-105","WA-106","WA-107","WA-108","WA-109","WA-110",
            "WA-111","WA-112","WA-113","WA-114","WA-115","WA-116","WA-117","WA-118","WA-119","WA-120",
            "WA-121","WA-122","WA-123","WA-124","WA-125","WA-126","WA-127","WA-128","WA-129","WA-130",
            "WA-131","WA-132","WA-133","WA-134","WA-135","WA-136","WA-137","WA-138","WA-139","WA-140",
            "WA-141","WA-142","WA-143","WA-144","WA-145","WA-146","WA-147","WA-148","WA-149","WA-150",
            "WA-151","WA-152","WA-153","WA-154","WA-156","WA-157","WA-158","GA-159","GA-160");
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final double RECOVERY_CAP=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical().cooldownRecoveryCap;
    private static final double CRITICAL_CAP=com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical().criticalChanceCap;
    public static boolean supported(GearInstance item) {
        return supported(item,ENABLED);
    }
    static boolean supported(GearInstance item,Set<String> capabilities) {
        return item.affixes().stream().allMatch(a->capabilities.contains(a.familyId()));
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
        return GearCombatEffects.physical(item);
    }
    public static Double magic(GearInstance item) {
        var base=item.intrinsicStats().get("magicPower");return base==null?null:base+value(item,"WA-003");
    }
    public static double protection(GearInstance item) {
        return (item.intrinsicStats().getOrDefault("protectionPoints",0d)+value(item,"GA-159"))*(1+value(item,"GA-160")/100);
    }
    public record Effects(Map<RpgAttribute,Integer> attributes,double health,double mana,double stamina,
                          double castRate,double cooldownRecovery,int allSkillRanks,GearEffectSnapshot snapshot) {
        public static final Effects NONE=new Effects(Map.of(),0,0,0,0,0,0);
        public Effects(Map<RpgAttribute,Integer> attributes,double health,double mana,double stamina,
                       double castRate,double cooldownRecovery,int allSkillRanks) {
            this(attributes,health,mana,stamina,castRate,cooldownRecovery,allSkillRanks,GearEffectSnapshot.EMPTY);
        }
        public Effects { attributes=Map.copyOf(attributes); Objects.requireNonNull(snapshot); }
        public Map<RpgAttribute,Integer> raw(Map<RpgAttribute,Integer> baseline) {
            var result=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);result.putAll(baseline);
            attributes.forEach((key,value)->result.merge(key,value,Math::addExact));return Map.copyOf(result);
        }
        public DerivedStats derive(DerivedStatService owner,Map<RpgAttribute,Integer> baseline) {
            var d=owner.derive(raw(baseline),health,stamina,mana);
            return new DerivedStats(d.rawAttributes(),d.effectiveAttributes(),d.maxHealth(),d.maxStamina(),d.maxMana(),
                    d.heavyDamageMultiplier(),d.lightDamageMultiplier(),d.magicDamageMultiplier(),d.healingMultiplier(),
                    Math.min(RECOVERY_CAP,d.cooldownRecovery()+cooldownRecovery),d.learnRate(),
                    Math.min(CRITICAL_CAP,d.criticalChance()+snapshot.percent(GearEffectSnapshot.Operator.CRIT_CHANCE)),
                    d.criticalMultiplier()+snapshot.percent(GearEffectSnapshot.Operator.CRIT_MULTIPLIER),d.upgradeSuccess(),d.magicFind());
        }
        public double windup(double authored) {return authored/(1+castRate);}
    }
    public static Effects effects(Collection<GearInstance> valid) {
        return effects(valid,ENABLED);
    }
    /** Uses the same candidate policy as equipment admission. */
    static Effects effects(Collection<GearInstance> valid,Set<String> capabilities) {
        valid=valid.stream().filter(item->supported(item,capabilities)).toList();
        var attributes=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        double health=0,mana=0,stamina=0,cast=0,recovery=0;int ranks=0;
        for(var item:valid) {
            attributes(item).forEach((stat,value)->attributes.merge(stat,value,Math::addExact));
            health+=item.intrinsicStats().getOrDefault("health",0d)+value(item,"WA-091");
            mana+=item.intrinsicStats().getOrDefault("mana",0d)+value(item,"WA-092");
            stamina+=item.intrinsicStats().getOrDefault("stamina",0d)+value(item,"WA-093");
            cast+=value(item,"WA-009")/100;recovery+=value(item,"WA-012")/100;
            ranks=Math.addExact(ranks,(int)value(item,"WA-121"));
        }
        return new Effects(attributes,health,mana,stamina,cast,recovery,ranks,new GearEffectSnapshot(valid));
    }
}
