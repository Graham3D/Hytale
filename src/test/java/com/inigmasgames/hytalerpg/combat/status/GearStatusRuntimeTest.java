package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearRarity;
import com.inigmasgames.hytalerpg.gear.MasterAffixTestEquipment;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearStatusRuntimeTest {
    private static GearEffectSnapshot gear(String... ids) { return ControlledGearSnapshot.with(ids); }
    private static GearInstance unrolled(GearInstance item) {
        var base=GearCatalog.load().base(item.baseId());
        return GearInstance.authoredQa(base,UUID.randomUUID(),item.itemLevel(),1000,
                GearRarity.COMMON,List.of(),java.math.BigDecimal.ZERO);
    }
    private static GearEffectSnapshot admitted(GearInstance item) {
        return MasterAffixTestEquipment.accepted(item).snapshot();
    }
    private static PeriodicStatusRuntime.Port<String,String> port(List<Double> ticks) {
        return new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source source,String context,String target,int index,
                                double coefficient,double seconds) {ticks.add(coefficient);return true;}
            public void changed(PeriodicStatusRuntime.Source source,String target,PeriodicStatusRuntime.View view) { }
        };
    }
    @Test void periodicOwnerReceivesFrozenAdditiveDpsAndExtendedDuration() {
        var runtime=new PeriodicStatusRuntime<String,String>();
        var owner=UUID.randomUUID();var target=UUID.randomUUID();
        var source=new PeriodicStatusRuntime.Source(owner,"fireball",target,PeriodicStatusRuntime.Kind.BURN);
        var ticks=new ArrayList<Double>();
        PeriodicStatusRuntime.Port<String,String> port=new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source s,String c,String t,int i,double coefficient,double seconds) {
                ticks.add(coefficient);return true;
            }
            public void changed(PeriodicStatusRuntime.Source s,String t,PeriodicStatusRuntime.View view) { }
        };
        var equipped=gear("WA-013","WA-065","WA-068");
        assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(runtime,source,"committed", "victim",10,10,4,1,1,0,port,equipped));
        assertEquals(5,runtime.sourceView(source,0).orElseThrow().remainingSeconds());
        assertEquals(15.4,runtime.sourceView(source,0).orElseThrow().coefficientPerSecond(),1e-9);
        runtime.tick(owner,1,port);runtime.tick(owner,2,port);runtime.tick(owner,3,port);
        runtime.tick(owner,4,port);runtime.tick(owner,5,port);
        assertEquals(77,ticks.stream().mapToDouble(Double::doubleValue).sum(),1e-9);
        assertEquals(0,runtime.size());

        var control=new PeriodicStatusRuntime<String,String>();var plain=new ArrayList<Double>();
        PeriodicStatusRuntime.Port<String,String> plainPort=new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source s,String c,String t,int i,double coefficient,double seconds){plain.add(coefficient);return true;}
            public void changed(PeriodicStatusRuntime.Source s,String t,PeriodicStatusRuntime.View view) { }
        };
        GearStatusRuntime.applyPeriodic(control,source,"committed","victim",10,10,4,1,1,0,plainPort,GearEffectSnapshot.EMPTY);
        for(int t=1;t<=4;t++)control.tick(owner,t,plainPort);
        assertEquals(40,plain.stream().mapToDouble(Double::doubleValue).sum(),1e-9);
        assertEquals(0,GearStatusRuntime.increasedDps(equipped,PeriodicStatusRuntime.Kind.POISON)-.30,1e-9);
        assertEquals(0,GearStatusRuntime.increasedDps(GearStatusRuntime.sourceScoped(equipped,UUID.randomUUID()),
                PeriodicStatusRuntime.Kind.BURN),1e-9);
    }
    @Test void d04MergeKeepsSkillChanceAndGearSourceFlags() {
        var chance=GearStatusRuntime.chance(.25,.20,.5,.48,gear("WA-064"),gear("WA-079"));
        assertEquals(.50,chance.effectiveResistance(),1e-9);
        assertEquals(.125,chance.skillOnly(),1e-9);
        assertEquals(.0125,chance.both(),1e-9);
        assertEquals(.1625,chance.any(),1e-9);
        assertTrue(chance.skillSucceeded(.01));assertTrue(chance.gearSucceeded(.01));
        assertTrue(chance.skillSucceeded(.10));assertFalse(chance.gearSucceeded(.10));
        assertFalse(chance.skillSucceeded(.14));assertTrue(chance.gearSucceeded(.14));
        assertFalse(chance.succeeded(.20));
        var plain=GearStatusRuntime.chance(.25,0,1,.40,GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY);
        assertEquals(.15,plain.any(),1e-9);
        assertFalse(plain.gearSucceeded(.1));
    }
    @Test void wa013IncreasesAllPeriodicTicksWithoutChangingDirectHitOrSourceCaps() {
        var item=MasterAffixTestEquipment.fixture("WA-013",false);
        var equipped=admitted(item);var empty=admitted(unrolled(item));
        for(var kind:PeriodicStatusRuntime.Kind.values()) {
            var source=new PeriodicStatusRuntime.Source(UUID.randomUUID(),"committed",UUID.randomUUID(),kind);
            var changed=new PeriodicStatusRuntime<String,String>();var control=new PeriodicStatusRuntime<String,String>();
            var ticks=new ArrayList<Double>();var ordinary=new ArrayList<Double>();
            GearStatusRuntime.applyPeriodic(changed,source,"hit","victim",10,10,4,1,1,0,port(ticks),equipped);
            GearStatusRuntime.applyPeriodic(control,source,"hit","victim",10,10,4,1,1,0,port(ordinary),empty);
            assertEquals(control.sourceView(source,0).orElseThrow().remainingSeconds(),
                    changed.sourceView(source,0).orElseThrow().remainingSeconds(),1e-9);
            changed.tick(source.owner(),1,port(ticks));control.tick(source.owner(),1,port(ordinary));
            assertTrue(ticks.getFirst()>ordinary.getFirst(),kind.name());
            assertEquals(1,changed.size());
            assertEquals(0,GearStatusRuntime.increasedDps(GearStatusRuntime.sourceScoped(equipped,UUID.randomUUID()),kind));
        }
        assertEquals(100,com.inigmasgames.hytalerpg.gear.GearCombatEffects.attack(equipped,item.identity(),
                "direct",100,1,true,false,0,null,0,1.5,false)
                .amount(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL),1e-9);
    }
    @Test void wa065BurnOnlyReplacesWeakerSourceWithoutRaisingCapOrOtherStatus() {
        var item=MasterAffixTestEquipment.fixture("WA-065",false);
        var idle=unrolled(item);var equipped=admitted(item);
        var boosted=GearStatusRuntime.sourceScoped(equipped,item.identity());
        var plain=GearStatusRuntime.sourceScoped(admitted(idle),idle.identity());
        assertEquals(0,plain.percent("WA-065"),1e-9);
        assertTrue(GearStatusRuntime.sourceScoped(equipped,idle.identity()).empty());
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var burn=new PeriodicStatusRuntime.Source(actor,"burn",victim,PeriodicStatusRuntime.Kind.BURN);
        var other=new PeriodicStatusRuntime.Source(UUID.randomUUID(),"other",victim,PeriodicStatusRuntime.Kind.BURN);
        var runtime=new PeriodicStatusRuntime<String,String>();var ticks=new ArrayList<Double>();var port=port(ticks);
        assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(runtime,burn,"weak","target",10,10,
                4,1,1,0,port,plain));
        assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(runtime,other,"other","target",10,10,
                4,1,1,0,port,plain));
        assertEquals("REFRESHED",GearStatusRuntime.applyPeriodic(runtime,burn,"strong","target",10,10,
                4,1,1,.5,port,boosted));
        var replaced=runtime.sourceView(burn,.5).orElseThrow();
        assertEquals(1,replaced.stacks());assertEquals(1,replaced.sourceCap());
        assertEquals(10*(1+boosted.percent("WA-065")),replaced.coefficientPerSecond(),1e-9);
        assertEquals(4,replaced.remainingSeconds(),1e-9);
        assertEquals(10,runtime.sourceView(other,.5).orElseThrow().coefficientPerSecond(),1e-9);
        runtime.tick(actor,1,port);
        assertEquals("REFRESHED",GearStatusRuntime.applyPeriodic(runtime,burn,"weak-again","target",10,10,
                4,1,1,1,port,plain));
        var retained=runtime.sourceView(burn,1).orElseThrow();
        assertEquals(replaced.coefficientPerSecond(),retained.coefficientPerSecond(),1e-9);
        assertEquals(1,retained.stacks());assertEquals(1,retained.sourceCap());
        assertEquals(4,retained.remainingSeconds(),1e-9);
        assertEquals(10,runtime.sourceView(other,1).orElseThrow().coefficientPerSecond(),1e-9);
        for(var kind:List.of(PeriodicStatusRuntime.Kind.POISON,PeriodicStatusRuntime.Kind.BLEED)) {
            var wrong=new PeriodicStatusRuntime.Source(actor,"wrong-"+kind,victim,kind);
            assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(runtime,wrong,"wrong","target",10,10,
                    4,1,1,1,port,boosted));
            assertEquals(10,runtime.sourceView(wrong,1).orElseThrow().coefficientPerSecond(),1e-9);
        }
        assertEquals(0,GearStatusRuntime.increasedDps(plain,PeriodicStatusRuntime.Kind.BURN),1e-9);
    }

    @Test void wa068PoisonAndBleedRefreshExpiryAndTickMagnitudeKeepSourceCaps() {
        var item=MasterAffixTestEquipment.fixture("WA-068",false);
        var boosted=admitted(item);var plain=admitted(unrolled(item));
        for(var kind:List.of(PeriodicStatusRuntime.Kind.POISON,PeriodicStatusRuntime.Kind.BLEED)) {
            int cap=kind==PeriodicStatusRuntime.Kind.POISON?12:1;
            var actor=UUID.randomUUID();var victim=UUID.randomUUID();
            var source=new PeriodicStatusRuntime.Source(actor,"qualified",victim,kind);
            var extended=new PeriodicStatusRuntime<String,String>();var control=new PeriodicStatusRuntime<String,String>();
            var longerTicks=new ArrayList<Double>();var plainTicks=new ArrayList<Double>();
            var longerPort=port(longerTicks);var plainPort=port(plainTicks);
            assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(extended,source,"rolled","victim",10,10,
                    4,cap,cap,0,longerPort,boosted));
            assertEquals("APPLIED",GearStatusRuntime.applyPeriodic(control,source,"unrolled","victim",10,10,
                    4,cap,cap,0,plainPort,plain));
            assertEquals(cap,extended.sourceView(source,0).orElseThrow().stacks(),kind.name());
            assertEquals(cap,extended.sourceView(source,0).orElseThrow().sourceCap(),kind.name());
            assertEquals(10,extended.sourceView(source,0).orElseThrow().coefficientPerSecond(),1e-9,kind.name());
            double extendedSeconds=4*(1+boosted.percent("WA-068"));
            assertTrue(extendedSeconds>4,kind.name());
            assertEquals(extendedSeconds,extended.sourceView(source,0).orElseThrow().remainingSeconds(),1e-9,kind.name());
            assertEquals(4,control.sourceView(source,0).orElseThrow().remainingSeconds(),1e-9,kind.name());
            extended.tick(actor,1,longerPort);control.tick(actor,1,plainPort);
            assertEquals(plainTicks,longerTicks,kind.name());
            assertEquals("REFRESHED",GearStatusRuntime.applyPeriodic(extended,source,"rolled","victim",10,10,
                    4,1,cap,1,longerPort,boosted));
            assertEquals("REFRESHED",GearStatusRuntime.applyPeriodic(control,source,"unrolled","victim",10,10,
                    4,1,cap,1,plainPort,plain));
            assertEquals(cap,extended.sourceView(source,1).orElseThrow().stacks(),kind.name());
            assertEquals(cap,extended.sourceView(source,1).orElseThrow().sourceCap(),kind.name());
            assertEquals(10,extended.sourceView(source,1).orElseThrow().coefficientPerSecond(),1e-9,kind.name());
            assertEquals(extendedSeconds,extended.sourceView(source,1).orElseThrow().remainingSeconds(),1e-9,kind.name());
            assertEquals(4,control.sourceView(source,1).orElseThrow().remainingSeconds(),1e-9,kind.name());
            for(int second=2;second<=5;second++) {
                extended.tick(actor,second,longerPort);control.tick(actor,second,plainPort);
            }
            assertTrue(control.sourceView(source,5).isEmpty(),kind.name());
            assertEquals(extendedSeconds-4,extended.sourceView(source,5).orElseThrow().remainingSeconds(),1e-9,kind.name());
            assertEquals(plainTicks,longerTicks.subList(0,plainTicks.size()),kind.name());
            extended.tick(actor,6,longerPort);
            assertTrue(extended.sourceView(source,6).isEmpty(),kind.name());
            assertEquals(cap*10*(extendedSeconds-4),longerTicks.getLast(),1e-9,kind.name());
            assertEquals(plainTicks.size()+1,longerTicks.size(),kind.name());
        }
    }
    @Test void admittedControlDurationAndStrongestSlowUseTargetGear() {
        var status=new StatusService(com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),()->0);
        var target=UUID.randomUUID();
        assertEquals(1.7,status.apply(target,RpgStatusType.ROOT,ControlProfile.NORMAL,2,gear("WA-080")).remainingSeconds(),1e-9);
        status.applySlow(target,"strong",.4,5);status.applySlow(target,"weak",.1,5);
        assertEquals(.4,status.strongestSlow(target).magnitude(),1e-9);
        assertEquals(.32,status.strongestSlow(target,gear("WA-081")).magnitude(),1e-9);
        assertEquals(0,status.strongestSlow(UUID.randomUUID(),gear("WA-081")).magnitude(),1e-9);
    }
    @Test void eligibleChillUsesCanonicalThresholdOwnerAndOneContact() {
        var statuses=new StatusService(com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),()->0);
        var contacts=new GearStatusRuntime.Contacts();var actor=UUID.randomUUID();var target=UUID.randomUUID();
        var hit=new GearStatusRuntime.ChillHit(actor,"root","strike",target,true,true,true,false,10,8,1);
        var equipped=gear("WA-056");
        var applied=GearStatusRuntime.applyChill(statuses,contacts,hit,ControlProfile.NORMAL,equipped,
                GearEffectSnapshot.EMPTY,0,0,1,.1);
        assertEquals("ADMITTED",applied.gate());assertTrue(applied.gearSucceeded());
        assertEquals(1,statuses.inspect(target).active().get(RpgStatusType.CHILL).stacks());
        assertEquals("DUPLICATE_CONTACT",GearStatusRuntime.applyChill(statuses,contacts,hit,
                ControlProfile.NORMAL,equipped,GearEffectSnapshot.EMPTY,0,0,1,0).gate());
        assertEquals(1,statuses.inspect(target).active().get(RpgStatusType.CHILL).stacks());
        var noWater=new GearStatusRuntime.ChillHit(actor,"root","dry",UUID.randomUUID(),true,true,true,false,10,0,1);
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.applyChill(statuses,contacts,noWater,
                ControlProfile.NORMAL,equipped,GearEffectSnapshot.EMPTY,0,0,1,0).gate());
        var control=new GearStatusRuntime.ChillHit(actor,"root","plain",UUID.randomUUID(),true,true,true,false,10,8,1);
        assertEquals("CHANCE_MISS",GearStatusRuntime.applyChill(statuses,contacts,control,
                ControlProfile.NORMAL,GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,0,0,1,.1).gate());
    }
}
