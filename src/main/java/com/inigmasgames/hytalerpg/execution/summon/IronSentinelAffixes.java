package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/** Every authored family has a Sentinel disposition; only live native adapters pass the forge gate. */
public final class IronSentinelAffixes {
    private IronSentinelAffixes() {}
    public enum Disposition { SELF_STAT, ATTACK_PROC, AURA, OWNER_ONLY, UNSUPPORTED }
    public record Decision(Disposition disposition, boolean adapted, String reason) {}

    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final Set<String> ADAPTED=directAdapters();
    private static Set<String> directAdapters(){
        var ids=new java.util.HashSet<>(Set.of("WA-001","WA-002","WA-005","WA-006","WA-007","WA-008",
                "WA-010","WA-011","WA-013","WA-014","WA-015","WA-016",
                "WA-091","WA-094","WA-096",
                "WA-134","WA-135","WA-136","WA-137","WA-138","WA-139","WA-140",
                "WA-142","WA-143","WA-145","WA-146","WA-148","WA-149","WA-150",
                "WA-157","WA-158","GA-159","GA-160",
                "WA-069","WA-070","WA-071",
                "WA-072","WA-073","WA-074","WA-075","WA-076","WA-077","WA-078"));
        // The bound-item GearCombatEffects envelope projects these exact local elemental
        // flat/increased/penetration/conversion operators into native channel submissions.
        for(int n=17;n<=40;n++)ids.add("WA-"+String.format(java.util.Locale.ROOT,"%03d",n));
        // The accepted Sentinel hit freezes its native origin, and Gather resolves
        // each condition against the live victim state and normal Health capacity.
        for(int n=41;n<=52;n++)ids.add("WA-"+String.format(java.util.Locale.ROOT,"%03d",n));
        // The native post-Apply status receipt enters StatusService and its shared
        // periodic owner. Status quality lines act only on an admitted source status.
        for(int n=53;n<=68;n++)ids.add("WA-"+String.format(java.util.Locale.ROOT,"%03d",n));
        ids.addAll(Set.of("WA-079","WA-080","WA-081","WA-082","WA-084","WA-111"));
        return Set.copyOf(ids);
    }
    private static final Map<String,Disposition> D12=dispositions();

    private static Map<String,Disposition> dispositions(){
        var result=new HashMap<String,Disposition>();
        for(int n=1;n<=158;n++)result.put("WA-"+String.format(java.util.Locale.ROOT,"%03d",n),Disposition.SELF_STAT);
        result.put("GA-159",Disposition.SELF_STAT);result.put("GA-160",Disposition.SELF_STAT);
        for(int n:new int[]{3,4,9,12,85,86,87,88,89,90,92,93,95,97,98,99,100,101,102,103,104,105,106,107,108,109,110,112,144,151,152,153,154,155,156})
            result.put("WA-"+String.format(java.util.Locale.ROOT,"%03d",n),Disposition.OWNER_ONLY);
        for(int n=113;n<=133;n++)result.put("WA-"+String.format(java.util.Locale.ROOT,"%03d",n),Disposition.OWNER_ONLY);
        for(int n:new int[]{13,53,54,55,56,57,58,59,60,61,62,63,65,66,67,68,94,96,134,135,136,137,138,139,140,141,142,143,145,146,147})
            result.put("WA-"+String.format(java.util.Locale.ROOT,"%03d",n),Disposition.ATTACK_PROC);
        for(int n=148;n<=150;n++)result.put("WA-"+String.format(java.util.Locale.ROOT,"%03d",n),Disposition.AURA);
        if(result.size()!=160||CATALOG.affixes().stream().anyMatch(a->!result.containsKey(a.id())))
            throw new IllegalStateException("D12_CATALOG_DISPOSITION_MISMATCH");
        return Map.copyOf(result);
    }

    public static Decision classify(String id){
        var affix=CATALOG.affix(id);
        Disposition kind=D12.get(id);
        if(kind==null)throw new IllegalArgumentException("Unknown Sentinel affix "+id);
        if(kind==Disposition.OWNER_ONLY)return new Decision(kind,true,
                "Player-only action, equipment, economy, or skill context; bound Sentinel receives no copy");
        String operator=affix.operator();
        if(ADAPTED.contains(id))return new Decision(kind,true,"Hywind Sentinel native combat adapter");
        String conditional=switch(id){
            case "WA-083","WA-147" ->
                    "INAPPLICABLE on the current unguarded chassis: native successful block and eligible guard source are required; no block is fabricated";
            case "WA-141" -> "INAPPLICABLE on the current single-item chassis: Twin Assault requires two actual distinct daggers; no offhand is fabricated";
            default -> null;
        };
        if(conditional!=null)return new Decision(kind,true,conditional);
        String reason=switch(operator){
            case "ITEM_AURA" -> "Item Aura needs a Sentinel-anchored rank-1 projection and lifecycle ownership";
            case "STATUS_PAYLOAD" -> "Attack status needs a Sentinel-owned proc, native status, and periodic-damage adapter";
            case "LOCAL_ELEMENT_FLAT","ELEMENT_DAMAGE","ELEMENTAL_DAMAGE","CONVERSION" ->
                    "Elemental attack composition and native cause adapter are not installed for Sentinel";
            case "ATTRIBUTE" -> "Sentinel has no audited raw-attribute-to-native-stat profile";
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
                    "LOCAL_ELEMENT_FLAT","CONVERSION","ATTACK_DAMAGE","LOCAL_ATTACK_SPEED",
                    "CRIT_CHANCE","CRIT_MULTIPLIER").contains(operator)
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
        return resistances(new com.inigmasgames.hytalerpg.gear.GearEffectSnapshot(java.util.List.of(item)));
    }
    public static Map<String,Double> resistances(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot item){
        double all=item.percent("WA-078");
        double earth=clamp(all+item.percent("WA-075"));
        return Map.of("Wind",clamp(all+item.percent("WA-072")),
                "Ice",clamp(all+item.percent("WA-073")),
                "Fire",clamp(all+item.percent("WA-074")),
                "Earth",earth,"RPG_Nature",earth,
                "Lightning",clamp(all+item.percent("WA-076")),
                "RPG_Void",clamp(all+item.percent("WA-077")));
    }
    private static double clamp(double resistance){
        if(!Double.isFinite(resistance)||resistance<0)throw new IllegalArgumentException("Invalid bound resistance");
        return Math.min(.75,resistance);
    }
}
