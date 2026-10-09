package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelStatusTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    static Stream<String> periodicIds(){return Stream.of("WA-013","WA-065","WA-066","WA-067","WA-068");}

    @ParameterizedTest(name="boundStatusQualityChangesExistingPeriodicOwner[{0}]")
    @MethodSource("periodicIds")
    void boundStatusQualityChangesExistingPeriodicOwner(String id){
        var a=lease(item(id,20));var b=lease(item(null,0));
        var kind=switch(id){case "WA-066"->PeriodicStatusRuntime.Kind.POISON;
            case "WA-067"->PeriodicStatusRuntime.Kind.BLEED;default->PeriodicStatusRuntime.Kind.BURN;};
        var source=new PeriodicStatusRuntime.Source(a.owner(),"accepted-native-status",UUID.randomUUID(),kind);
        var active=new PeriodicStatusRuntime<String,String>();var baseline=new PeriodicStatusRuntime<String,String>();
        var actualTicks=new ArrayList<Double>();var controlTicks=new ArrayList<Double>();
        assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(active,source,"contact","victim",
                10,10,4,1,1,0,port(actualTicks),
                GearStatusRuntime.sourceScoped(a.boundEffects(),a.boundItem().identity())),id);
        assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(baseline,source,"contact","victim",
                10,10,4,1,1,0,port(controlTicks),
                GearStatusRuntime.sourceScoped(b.boundEffects(),b.boundItem().identity())),id);
        var changed=active.sourceView(source,0).orElseThrow();
        var plain=baseline.sourceView(source,0).orElseThrow();
        if(id.equals("WA-068")){
            assertTrue(changed.remainingSeconds()>plain.remainingSeconds(),id);
            assertEquals(plain.coefficientPerSecond(),changed.coefficientPerSecond(),1e-9,id);
        }else{
            assertTrue(changed.coefficientPerSecond()>plain.coefficientPerSecond(),id);
            active.tick(source.owner(),1,port(actualTicks));baseline.tick(source.owner(),1,port(controlTicks));
            assertTrue(actualTicks.getFirst()>controlTicks.getFirst(),id);
            assertEquals(plain.remainingSeconds(),changed.remainingSeconds(),1e-9,id);
        }
        if(id.equals("WA-065"))assertEquals(0,GearStatusRuntime.increasedDps(a.boundEffects(),
                PeriodicStatusRuntime.Kind.POISON),1e-9,id+" wrong status");
    }

    @Test void boundStatusPenetrationChangesActualAdmission_WA064(){
        var a=lease(item("WA-064",20));var b=lease(item(null,0));
        UUID actor=UUID.randomUUID(),victim=UUID.randomUUID();
        var hit=new GearStatusRuntime.AppliedHit(actor,"wa064", "contact",victim,
                true,true,true,false,10,1,attack(a).amounts());
        var control=new GearStatusRuntime.AppliedHit(actor,"control", "contact",victim,
                true,true,true,false,10,1,attack(b).amounts());
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var with=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),hit,
                RpgStatusType.STUN,ControlProfile.NORMAL,a.boundEffects(),GearEffectSnapshot.EMPTY,
                .5,.5,.3,0,null);
        var without=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),control,
                RpgStatusType.STUN,ControlProfile.NORMAL,b.boundEffects(),GearEffectSnapshot.EMPTY,
                .5,.5,.3,0,null);
        assertEquals("ADMITTED",with.gate());
        assertEquals("CHANCE_MISS",without.gate());
        assertEquals(.3,GearStatusRuntime.chance(.5,0,1,.5,a.boundEffects(),
                GearEffectSnapshot.EMPTY).effectiveResistance(),1e-9);
    }

    @Test void boundRendingAdmitsBleedAndSuppressesVictimRegeneration_WA134(){
        var a=lease(item("WA-134",100));var b=lease(item(null,0));
        UUID actor=UUID.randomUUID(),victim=UUID.randomUUID();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var accepted=new GearStatusRuntime.AppliedHit(actor,"rending", "contact",victim,
                true,true,true,false,10,1,attack(a).amounts());
        var plain=new GearStatusRuntime.AppliedHit(actor,"plain", "contact",victim,
                true,true,true,false,10,1,attack(b).amounts());
        assertEquals("ADMITTED",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                accepted,RpgStatusType.BLEED,ControlProfile.NORMAL,a.boundEffects(),
                GearEffectSnapshot.EMPTY,0,0,0,0,(kind,hit,source)->"APPLIED").gate());
        assertEquals(.5,statuses.healthRegenerationFactor(victim),1e-9);
        assertEquals("CHANCE_MISS",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                plain,RpgStatusType.BLEED,ControlProfile.NORMAL,b.boundEffects(),
                GearEffectSnapshot.EMPTY,0,0,0,0,(kind,hit,source)->"APPLIED").gate());
        var child=new GearStatusRuntime.AppliedHit(actor,"no-proc","contact",victim,
                true,true,false,false,10,1,attack(a).amounts());
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                child,RpgStatusType.BLEED,ControlProfile.NORMAL,a.boundEffects(),
                GearEffectSnapshot.EMPTY,0,0,0,0,(kind,hit,source)->"APPLIED").gate());
    }

    private static GearCombatEffects.Hit attack(SummonRegistry.Lease lease){
        return GearCombatEffects.attack(lease.boundEffects(),lease.boundItem().identity(),
                "sentinel/status/"+lease.token(),100,1,true,false,0,null,0,1.5,false,Vec3.ZERO);
    }
    private static PeriodicStatusRuntime.Port<String,String> port(List<Double> ticks){
        return new PeriodicStatusRuntime.Port<>(){
            public boolean tick(PeriodicStatusRuntime.Source source,String context,String victim,int tickIndex,
                                double coefficient,double seconds){ticks.add(coefficient*seconds);return true;}
            public void changed(PeriodicStatusRuntime.Source source,String victim,PeriodicStatusRuntime.View view){}
        };
    }
    private static SummonRegistry.Lease lease(GearInstance item){
        IronSentinelAffixes.requireAdapted(item);
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",item,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        return new SummonRegistry().restoreIronSentinel(binding,10);
    }
    private static GearInstance item(String id,double value){
        var base=CATALOG.base("gm.sword_iron.n");List<GearInstance.AffixRoll> rolls=List.of();
        if(id!=null){var affix=CATALOG.affix(id);
            rolls=List.of(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,value,
                    new GearRequirements.Gate(1,Map.of()),"Bound status",affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
}
