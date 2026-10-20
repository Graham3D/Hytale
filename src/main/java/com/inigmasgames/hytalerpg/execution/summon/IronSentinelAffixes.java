package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.util.Map;
import java.util.Set;

/** Every authored family has a Sentinel disposition; only live native adapters pass the forge gate. */
public final class IronSentinelAffixes {
    private IronSentinelAffixes() {}
    public enum Disposition { SELF_STAT, ATTACK_PROC, AURA, OWNER_ONLY, UNSUPPORTED }
    public record Decision(Disposition disposition, boolean adapted, String reason) {}

    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final Set<String> ADAPTED=Set.of("WA-001","WA-002","WA-005","WA-006","WA-008",
            "WA-010","WA-011","WA-091","WA-157","WA-158","GA-159","GA-160",
            "WA-072","WA-074","WA-076","WA-077");
    private static final Set<String> OWNER_ONLY=Set.of("WA-119","WA-120","WA-151","WA-152","WA-153",
            "WA-154","WA-156","WA-121","WA-122","WA-123","WA-124","WA-125","WA-126",
            "WA-127","WA-128","WA-129","WA-130","WA-131","WA-132","WA-133");

    public static Decision classify(String id){
        var affix=CATALOG.affix(id);
        if(OWNER_ONLY.contains(id))return new Decision(Disposition.OWNER_ONLY,true,
                "Wielder utility, requirement, skill-rank, or finite-summon modifier; bound Sentinel gains no effect");
        String operator=affix.operator();
        Disposition kind=switch(operator){
            case "ITEM_AURA" -> Disposition.AURA;
            case "STATUS_PAYLOAD","LIFE_ON_HIT","MANA_ON_HIT","LIFE_LEECH","MANA_LEECH",
                    "LIFE_ON_KILL","CRUSHING_BLOW","DOUBLE_HIT_DAMAGE","IMPALE","ARMOR_BREAK",
                    "CULL","FORTIFYING_HIT","DUAL_STRIKE","THORNS","KILL_BURST","ITEM_TRIGGER" -> Disposition.ATTACK_PROC;
            case "LOCAL_PHYS_FLAT","LOCAL_PHYS_INC","LOCAL_PHYS_MIN","LOCAL_PHYS_MAX",
                    "LOCAL_ARMOR_FLAT","LOCAL_ARMOR_INCREASED","ATTACK_DAMAGE","PHYSICAL_DAMAGE_GLOBAL",
                    "LOCAL_ATTACK_SPEED","CRIT_CHANCE","CRIT_MULTIPLIER","MAX_HEALTH","MAX_MANA",
                    "MAX_STAMINA","ATTRIBUTE","RESIST_WIND","RESIST_WATER","RESIST_FIRE",
                    "RESIST_EARTH","RESIST_LIGHTNING","RESIST_VOID","ALL_RESISTANCE",
                    "STATUS_RESISTANCE","CONTROL_DURATION_TAKEN","SLOW_EFFECT_TAKEN",
                    "KNOCKBACK_TAKEN","GLOBAL_DEFENSE","MINION_MOVEMENT","MINION_DAMAGE",
                    "MINION_HEALTH","MINION_DEFENSE","MINION_RESISTANCE","MINION_ATTACK_SPEED",
                    "LOCAL_ELEMENT_FLAT","LOCAL_MAGIC_FLAT","ELEMENT_DAMAGE","ELEMENTAL_DAMAGE",
                    "CONVERSION","CONDITIONAL_DAMAGE","DOT_DAMAGE","STATUS_PENETRATION",
                    "STATUS_POTENCY","DAMAGING_STATUS_DURATION","HEALING_RECEIVED","MANA_REGEN",
                    "STAMINA_REGEN","HEALING_POWER","HEALING_DONE","BARRIER_STRENGTH",
                    "LIGHT_RADIUS","SHIELD_DEFENSE_FLAT","SHIELD_DEFENSE_INC" -> Disposition.SELF_STAT;
            default -> Disposition.UNSUPPORTED;
        };
        if(ADAPTED.contains(id))return new Decision(kind,true,"Hywind Sentinel native combat adapter");
        String reason=switch(operator){
            case "ITEM_AURA" -> "Item Aura needs a Sentinel-anchored rank-1 projection and lifecycle ownership";
            case "STATUS_PAYLOAD" -> "Attack status needs a Sentinel-owned proc, native status, and periodic-damage adapter";
            case "LOCAL_ELEMENT_FLAT","ELEMENT_DAMAGE","ELEMENTAL_DAMAGE","CONVERSION" ->
                    "Elemental attack composition and native cause adapter are not installed for Sentinel";
            case "ATTRIBUTE" -> "Sentinel has no audited raw-attribute-to-native-stat profile";
            case "RESIST_WATER","RESIST_EARTH","ALL_RESISTANCE" ->
                    "Water/Earth native damage-cause mapping is not audited for Sentinel mitigation";
            case "STATUS_RESISTANCE","CONTROL_DURATION_TAKEN","SLOW_EFFECT_TAKEN" ->
                    "Incoming Sentinel status admission/resistance adapter is not installed";
            case "MINION_MOVEMENT" -> "Native Sentinel pathfinding speed modifier is not audited";
            case "ITEM_TRIGGER" -> "Triggered skill needs a dedicated NoProc child and shared cooldown";
            default -> "No Sentinel adapter for "+operator+" ("+affix.featureDisposition()+")";
        };
        return new Decision(kind,false,reason);
    }
    public static void requireAdapted(GearInstance item){
        for(var affix:item.affixes()){
            Decision decision=classify(affix.familyId());
            if(!decision.adapted())throw new IllegalArgumentException("Iron Sentinel cannot forge "+affix.familyId()+
                    " ["+decision.disposition()+"]: "+decision.reason());
            if(affix.value()<0)throw new IllegalArgumentException("Iron Sentinel cannot forge negative "+affix.familyId());
            String operator=CATALOG.affix(affix.familyId()).operator();
            if(Set.of("LOCAL_PHYS_FLAT","LOCAL_PHYS_INC","LOCAL_PHYS_MIN","LOCAL_PHYS_MAX",
                    "ATTACK_DAMAGE","LOCAL_ATTACK_SPEED","CRIT_CHANCE","CRIT_MULTIPLIER").contains(operator)
                    &&item.category()!=GearCatalog.Category.HELD)
                throw new IllegalArgumentException("Iron Sentinel cannot forge "+affix.familyId()+
                        ": weapon-local property on non-weapon source");
            if(Set.of("LOCAL_ARMOR_FLAT","LOCAL_ARMOR_INCREASED").contains(operator)
                    &&item.category()!=GearCatalog.Category.ARMOR)
                throw new IllegalArgumentException("Iron Sentinel cannot forge "+affix.familyId()+
                        ": armor-local property on non-armor source");
        }
    }
    /** Global increased lines share one bucket; local Enhanced Damage is already in the weapon range. */
    public static ModifierBuckets physicalModifiers(GearInstance item){
        var result=ModifierBuckets.NONE;
        if(item.category()==GearCatalog.Category.HELD){
            double attack=GearAffixRuntime.value(item,"WA-005")/100;
            if(attack>0)result=result.withIncreased(attack);
        }
        double physical=GearAffixRuntime.value(item,"WA-006")/100;
        if(physical>0)result=result.withIncreased(physical);
        return result;
    }
    public static double criticalChance(GearInstance item){return Math.min(.75,GearAffixRuntime.value(item,"WA-010")/100);}
    public static double criticalMultiplier(GearInstance item){return 1.5+GearAffixRuntime.value(item,"WA-011")/100;}
    public static Map<String,Double> resistances(GearInstance item){
        return Map.of("Wind",clamp(GearAffixRuntime.value(item,"WA-072")/100),
                "Fire",clamp(GearAffixRuntime.value(item,"WA-074")/100),
                "Lightning",clamp(GearAffixRuntime.value(item,"WA-076")/100),
                "RPG_Void",clamp(GearAffixRuntime.value(item,"WA-077")/100));
    }
    private static double clamp(double resistance){
        if(!Double.isFinite(resistance)||resistance<0)throw new IllegalArgumentException("Invalid bound resistance");
        return Math.min(.75,resistance);
    }
}
