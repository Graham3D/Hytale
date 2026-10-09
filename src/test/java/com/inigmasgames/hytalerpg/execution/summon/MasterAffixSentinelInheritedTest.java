package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelInheritedTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearCombatEffects.Channel[] ELEMENTS={GearCombatEffects.Channel.WIND,
            GearCombatEffects.Channel.WATER,GearCombatEffects.Channel.FIRE,
            GearCombatEffects.Channel.EARTH,GearCombatEffects.Channel.LIGHTNING,
            GearCombatEffects.Channel.VOID};

    static Stream<String> localPhysicalIds(){return Stream.of("WA-001","WA-002","WA-157","WA-158");}
    static Stream<String> attackModifierIds(){return Stream.of("WA-005","WA-006","WA-007");}
    static Stream<String> criticalIds(){return Stream.of("WA-010","WA-011");}
    static Stream<String> elementalIds(){return java.util.stream.IntStream.rangeClosed(17,40)
            .mapToObj(MasterAffixSentinelInheritedTest::wa);}
    static Stream<String> resistanceIds(){return java.util.stream.IntStream.rangeClosed(72,78)
            .mapToObj(MasterAffixSentinelInheritedTest::wa);}
    static Stream<String> defenseIds(){return Stream.of("WA-069","WA-070","WA-071","GA-159","GA-160");}
    private static String wa(int n){return String.format(Locale.ROOT,"WA-%03d",n);}

    @ParameterizedTest(name="boundLocalPhysicalChangesSentinelProjectionAndAttack[{0}]")
    @MethodSource("localPhysicalIds")
    void boundLocalPhysicalChangesSentinelProjectionAndAttack(String id){
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        assertEquals(id,a.boundEffects().sources(CATALOG.affix(id).operator().equals("LOCAL_PHYS_INC")?
                GearEffectSnapshot.Operator.LOCAL_PHYS_INC:
                GearEffectSnapshot.Operator.valueOf(CATALOG.affix(id).operator())).getFirst().affixId());
        boolean maximumOnly=id.equals("WA-158");
        if(maximumOnly)assertTrue(a.sentinelStats().finalPhysicalMax()>b.sentinelStats().finalPhysicalMax(),id);
        else assertTrue(a.sentinelStats().finalPhysicalMin()>b.sentinelStats().finalPhysicalMin(),id);
        double actualPower=maximumOnly?a.sentinelStats().finalPhysicalMax():a.sentinelStats().finalPhysicalMin();
        double controlPower=maximumOnly?b.sentinelStats().finalPhysicalMax():b.sentinelStats().finalPhysicalMin();
        var hit=hit(a,actualPower,0,null,false);var control=hit(b,controlPower,0,null,false);
        assertTrue(hit.amount(GearCombatEffects.Channel.PHYSICAL)>control.amount(GearCombatEffects.Channel.PHYSICAL),id);
    }

    @ParameterizedTest(name="boundGlobalAttackModifierChangesActualHit[{0}]")
    @MethodSource("attackModifierIds")
    void boundGlobalAttackModifierChangesActualHit(String id){
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        boolean element=id.equals("WA-007");
        var channel=element?GearCombatEffects.Channel.FIRE:GearCombatEffects.Channel.PHYSICAL;
        var hit=hit(a,100,element?.5:0,element?channel:null,false);
        var control=hit(b,100,element?.5:0,element?channel:null,false);
        assertTrue(hit.amount(channel)>control.amount(channel),id);
        assertEquals(a.boundEffects().revision(),hit.revision(),id);
    }

    @ParameterizedTest(name="boundCriticalChangesExistingRollOrMultiplier[{0}]")
    @MethodSource("criticalIds")
    void boundCriticalChangesExistingRollOrMultiplier(String id){
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        var hit=GearCombatEffects.attack(a.boundEffects(),a.boundItem().identity(),"critical/"+id,
                100,1,true,false,0,null,0,1.5,true,Vec3.ZERO,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->.1));
        var control=GearCombatEffects.attack(b.boundEffects(),b.boundItem().identity(),"critical/control/"+id,
                100,1,true,false,0,null,0,1.5,true,Vec3.ZERO,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(()->.1));
        if(id.equals("WA-010")){assertTrue(hit.critical());assertFalse(control.critical());
            assertTrue(hit.amount(GearCombatEffects.Channel.PHYSICAL)>control.amount(GearCombatEffects.Channel.PHYSICAL));}
        else {assertEquals(.2,hit.criticalMultiplier()-control.criticalMultiplier(),1e-9);
            assertEquals(control.critical(),hit.critical());}
    }

    @ParameterizedTest(name="boundElementalAffixChangesActualAttackChannel[{0}]")
    @MethodSource("elementalIds")
    void boundElementalAffixChangesActualAttackChannel(String id){
        int n=Integer.parseInt(id.substring(3));int offset=(n-17)%6;
        var channel=ELEMENTS[offset];
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        // Existing attack envelope accepts a physical strike with a pre-existing channel component.
        double preexisting=n>=23&&n<=34?.5:0;
        var hit=hit(a,100,preexisting,preexisting>0?channel:null,false);
        var control=hit(b,100,preexisting,preexisting>0?channel:null,false);
        if(n<=28||n>=35)assertTrue(hit.amount(channel)>control.amount(channel),id);
        else {assertEquals(control.amount(channel),hit.amount(channel),1e-9,id);
            assertEquals(.2,hit.penetration(channel)-control.penetration(channel),1e-9,id);}
        assertEquals(a.boundEffects().revision(),hit.revision(),id);
        assertEquals(a.boundItem().identity(),hit.itemId(),id);
    }

    @ParameterizedTest(name="boundResistanceChangesNativeSentinelIncomingDamage[{0}]")
    @MethodSource("resistanceIds")
    void boundResistanceChangesNativeSentinelIncomingDamage(String id){
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        int n=Integer.parseInt(id.substring(3));
        var causes=n==78?List.of("Wind","Ice","Fire","RPG_Nature","Lightning","RPG_Void"):
                List.of(GearCombatEffects.nativeCause(ELEMENTS[n-72]));
        for(var cause:causes){
            double resisted=HytaleSummonSystem.incomingResistance(a,cause);
            assertEquals(.2,resisted-HytaleSummonSystem.incomingResistance(b,cause),1e-9,id+"/"+cause);
        }
        assertEquals(HytaleSummonSystem.incomingResistance(b,"Physical"),
                HytaleSummonSystem.incomingResistance(a,"Physical"),1e-9,id);
    }

    @ParameterizedTest(name="boundDefenseChangesNativeSentinelProtection[{0}]")
    @MethodSource("defenseIds")
    void boundDefenseChangesNativeSentinelProtection(String id){
        String base=id.startsWith("GA-")?"gm.plate_iron.head.n":"gm.shield_copper.n";
        var a=lease(item(base,id,id.equals("GA-159")?10:20));var b=lease(item(base,null,0));
        assertTrue(HytaleSummonSystem.sentinelDefenseView(a).totalRating()>
                HytaleSummonSystem.sentinelDefenseView(b).totalRating(),id);
        assertTrue(HytaleSummonSystem.incomingResistance(a,"Physical")>
                HytaleSummonSystem.incomingResistance(b,"Physical"),id);
    }

    @ParameterizedTest(name="boundChassisCapacityOrCadenceChangesOnlyIntendedStat[{0}]")
    @org.junit.jupiter.params.provider.ValueSource(strings={"WA-008","WA-091"})
    void boundChassisCapacityOrCadenceChangesOnlyIntendedStat(String id){
        var a=lease(item("gm.sword_iron.n",id,20));var b=lease(item("gm.sword_iron.n",null,0));
        if(id.equals("WA-008")){
            assertEquals(b.sentinelStats().attackInterval()/1.2,a.sentinelStats().attackInterval(),1e-9);
            assertEquals(b.sentinelStats().finalMaxHealth(),a.sentinelStats().finalMaxHealth(),1e-9);
        }else{
            assertEquals(b.sentinelStats().finalMaxHealth()+20,a.sentinelStats().finalMaxHealth(),1e-9);
            assertEquals(b.sentinelStats().attackInterval(),a.sentinelStats().attackInterval(),1e-9);
            assertEquals(50,IronSentinelStatProjection.retainCurrentHealth(50,a.sentinelStats().finalMaxHealth()));
        }
    }

    private static GearCombatEffects.Hit hit(SummonRegistry.Lease lease,double physical,double converted,
                                             GearCombatEffects.Channel destination,boolean canCrit){
        return GearCombatEffects.attack(lease.boundEffects(),lease.boundItem().identity(),
                "sentinel/"+lease.boundItem().identity(),physical,1,true,false,converted,destination,
                0,1.5,canCrit,Vec3.ZERO);
    }
    private static SummonRegistry.Lease lease(GearInstance item){
        IronSentinelAffixes.requireAdapted(item);
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",item,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        return new SummonRegistry().restoreIronSentinel(binding,10);
    }
    private static GearInstance item(String baseId,String id,double value){
        var base=CATALOG.base(baseId);List<GearInstance.AffixRoll> rolls=List.of();
        if(id!=null){var affix=CATALOG.affix(id);
            rolls=List.of(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,value,
                    new GearRequirements.Gate(1,Map.of()),"Bound Sentinel source",affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
}
