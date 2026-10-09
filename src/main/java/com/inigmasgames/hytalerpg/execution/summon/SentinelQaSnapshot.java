package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;

/** Read-only projection from the same committed lease used by native attack/defense owners. */
public final class SentinelQaSnapshot {
    private SentinelQaSnapshot() {}
    private static final GearCatalog CATALOG=GearCatalog.load();
    public static Map<String,Object> resolved(SummonRegistry.Lease lease) {
        if(lease==null||!lease.ironSentinel())return Map.of("status","NO_ACTIVE_SENTINEL");
        var item=lease.boundItem();var bound=lease.boundEffects();var stats=lease.sentinelStats();
        var result=new LinkedHashMap<String,Object>();
        result.put("status","LEASE_RESOLVED");result.put("entity",lease.entity());result.put("world",lease.world());
        result.put("owner",lease.owner());result.put("leaseToken",lease.token());result.put("source",Qa159Pack.source(item));
        result.put("boundRevision",bound.revision());result.put("ownerRevision",lease.ownerEffects().revision());
        result.put("ownerSources",lease.ownerEffects().items().stream().map(Qa159Pack::source).toList());
        result.put("chassisAndSource",stats);result.put("maximumHealth",lease.maximumHealth());
        result.put("defense",HytaleSummonSystem.sentinelDefenseView(lease));
        var resistances=new TreeMap<String,Double>();
        for(String cause:List.of("Physical","Wind","Ice","Fire","Earth","Lightning","RPG_Void"))
            resistances.put(cause,HytaleSummonSystem.incomingResistance(lease,cause));
        result.put("resistances",resistances);
        result.put("attackIntervalSeconds",lease.interval());result.put("attacksPerSecond",1/lease.interval());
        result.put("attackCoefficient",lease.coefficient());
        result.put("criticalChance",IronSentinelAffixes.criticalChance(item));
        result.put("criticalMultiplier",IronSentinelAffixes.criticalMultiplier(item));
        result.put("meleeReachMetres",2.5+bound.value("WA-014"));
        // Pure envelope projection, not a submitted attack. Target-dependent conditions and
        // mitigation remain in the observed runtime receipts, never reported as a snapshot PASS.
        var low=GearCombatEffects.attack(bound,item.identity(),"diagnostic",stats.finalPhysicalMin(),lease.coefficient(),true,false,0,null,0,1.5,false);
        var high=GearCombatEffects.attack(bound,item.identity(),"diagnostic",stats.finalPhysicalMax(),lease.coefficient(),true,false,0,null,0,1.5,false);
        result.put("unconditionalNoncriticalDamage",Map.of("minimum",low.amounts(),"maximum",high.amounts(),
                "penetration",low.penetration(),"increased",low.increased(),"boundary","before target conditions, crit, native mitigation and procs"));
        var modifiers=new TreeMap<String,Object>();
        for(var op:GearEffectSnapshot.Operator.values()){
            var sources=bound.sources(op).stream().filter(s->Qa159Pack.disposition(s.affixId()).equals("INHERITED")).toList();
            if(!sources.isEmpty())modifiers.put(op.name(),sources.stream().map(s->Map.of("affixId",s.affixId(),"itemId",s.itemId(),
                    "value",s.value(),"scope",s.definition().scopeContract(),"runtimeContract",s.definition().effectContract())).toList());
        }
        result.put("inheritedModifiers",modifiers);
        result.put("provenance",item.affixes().stream().map(a->{
            var definition=bound.sources(GearEffectSnapshot.Operator.valueOf(CATALOG.affix(a.familyId()).operator()))
                    .stream().filter(s->s.affixId().equals(a.familyId())).findFirst().orElseThrow();
            return Map.of("affixId",a.familyId(),"disposition",Qa159Pack.disposition(a.familyId()),"modifier",definition.operator().name(),
                    "value",a.value(),"resolvedField",resolvedField(definition.operator()),"reason",IronSentinelAffixes.classify(a.familyId()).reason());
        }).toList());
        return result;
    }
    private static String resolvedField(GearEffectSnapshot.Operator op){return switch(op){
        case MAX_HEALTH -> "maximumHealth";
        case LOCAL_PHYS_FLAT,LOCAL_PHYS_INC,LOCAL_PHYS_MIN,LOCAL_PHYS_MAX,LOCAL_ELEMENT_FLAT,ATTACK_DAMAGE,
                PHYSICAL_DAMAGE_GLOBAL,ELEMENT_DAMAGE,ELEMENTAL_DAMAGE,ELEMENT_PENETRATION,CONVERSION -> "unconditionalNoncriticalDamage";
        case LOCAL_ARMOR_FLAT,LOCAL_ARMOR_INCREASED,SHIELD_DEFENSE_FLAT,SHIELD_DEFENSE_INC,GLOBAL_DEFENSE -> "defense";
        case RESIST_WIND,RESIST_WATER,RESIST_FIRE,RESIST_EARTH,RESIST_LIGHTNING,RESIST_VOID,ALL_RESISTANCE -> "resistances";
        case LOCAL_ATTACK_SPEED -> "attackIntervalSeconds";
        case CRIT_CHANCE -> "criticalChance";case CRIT_MULTIPLIER -> "criticalMultiplier";
        case MELEE_REACH -> "meleeReachMetres";case ITEM_AURA -> "activeInheritedAuras";
        default -> "inheritedModifiers/"+op+"; triggered outcome requires runtime receipt";
    };}
}
