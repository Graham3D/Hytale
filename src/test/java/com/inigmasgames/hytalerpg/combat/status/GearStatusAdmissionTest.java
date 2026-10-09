package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Calls the production admission and PeriodicStatusRuntime owner with controlled legal equipment. */
class GearStatusAdmissionTest {
    private static GearStatusRuntime.AppliedHit hit(UUID actor,UUID victim,String strike,Channel channel,
                                                      boolean canProc) {
        return new GearStatusRuntime.AppliedHit(actor,"root",strike,victim,true,true,canProc,false,10,1,
                Map.of(channel,10d));
    }
    @Test void rendingCreatesOneBleedPackageAndOneTimedRegenerationReduction() {
        var clock=new AtomicLong();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),clock::get);
        var contacts=new GearStatusRuntime.Contacts();
        var runtime=new PeriodicStatusRuntime<String,String>();
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var gear=ControlledGearSnapshot.with("WA-134");
        var delivered=new ArrayList<Double>();
        PeriodicStatusRuntime.Port<String,String> port=new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source source,String context,String target,int index,
                                double coefficient,double seconds){delivered.add(coefficient);return true;}
            public void changed(PeriodicStatusRuntime.Source source,String target,PeriodicStatusRuntime.View view){
                statuses.projectPeriodic(source.victim(),RpgStatusType.BLEED,view.stacks(),view.remainingSeconds());
            }
        };
        GearStatusRuntime.PeriodicAdmission producer=(kind,applied,source)->GearStatusRuntime.applyPeriodic(
                runtime,new PeriodicStatusRuntime.Source(actor,"gear/root",victim,kind),"committed","victim",
                10,10,4,1,1,clock.get()/1e9,port,source);
        var first=GearStatusRuntime.admit(statuses,contacts,hit(actor,victim,"strike",Channel.PHYSICAL,true),
                RpgStatusType.BLEED,ControlProfile.NORMAL,gear,GearEffectSnapshot.EMPTY,0,0,.01,.01,producer);
        assertEquals("APPLIED",first.periodicResult());
        assertEquals(1,runtime.size());
        assertEquals(.5,statuses.healthRegenerationFactor(victim));
        assertEquals("DUPLICATE_CONTACT",GearStatusRuntime.admit(statuses,contacts,
                hit(actor,victim,"strike",Channel.PHYSICAL,true),RpgStatusType.BLEED,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer).gate());
        runtime.tick(actor,1,port);assertEquals(10,delivered.stream().mapToDouble(Double::doubleValue).sum(),1e-9);
        assertEquals("CHANCE_MISS",GearStatusRuntime.admit(statuses,contacts,
                new GearStatusRuntime.AppliedHit(actor,"miss-root","miss",victim,true,true,true,false,10,1,
                        Map.of(Channel.PHYSICAL,10d)),RpgStatusType.BLEED,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,0,.99,0,producer).gate());
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,
                hit(actor,victim,"fire",Channel.FIRE,true),RpgStatusType.BLEED,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer).gate());
        assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,
                hit(actor,victim,"child",Channel.PHYSICAL,false),RpgStatusType.BLEED,ControlProfile.NORMAL,
                gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer).gate());
        clock.set(4_000_000_000L);assertEquals(1,statuses.healthRegenerationFactor(victim));
    }
    @Test void serrationIgnitionAndVenomEnterTheExistingPeriodicOwnerOnce() {
        String[] ids={"WA-053","WA-054","WA-055"};
        RpgStatusType[] types={RpgStatusType.BLEED,RpgStatusType.BURN,RpgStatusType.POISON};
        Channel[] channels={Channel.PHYSICAL,Channel.FIRE,Channel.EARTH};
        for(int i=0;i<ids.length;i++){
            var runtime=new PeriodicStatusRuntime<String,String>();
            var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
            var contacts=new GearStatusRuntime.Contacts();
            var actor=UUID.randomUUID();var victim=UUID.randomUUID();
            var ticks=new ArrayList<Double>();
            PeriodicStatusRuntime.Port<String,String> port=new PeriodicStatusRuntime.Port<>() {
                public boolean tick(PeriodicStatusRuntime.Source source,String context,String target,int index,
                                    double coefficient,double seconds){ticks.add(coefficient);return true;}
                public void changed(PeriodicStatusRuntime.Source source,String target,PeriodicStatusRuntime.View view){
                    statuses.projectPeriodic(source.victim(),RpgStatusType.valueOf(source.kind().name()),
                            view.stacks(),view.remainingSeconds());
                }
            };
            GearStatusRuntime.PeriodicAdmission producer=(kind,applied,source)->GearStatusRuntime.applyPeriodic(
                    runtime,new PeriodicStatusRuntime.Source(actor,"item/root",victim,kind),
                    "committed","victim",10,10,4,1,kind==PeriodicStatusRuntime.Kind.POISON?12:1,
                    0,port,source);
            var gear=ControlledGearSnapshot.with(ids[i]);
            var positive=GearStatusRuntime.admit(statuses,contacts,hit(actor,victim,"positive",channels[i],true),
                    types[i],ControlProfile.NORMAL,gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer);
            assertEquals("APPLIED",positive.periodicResult(),ids[i]);
            assertEquals(1,runtime.size(),ids[i]);
            assertEquals("DUPLICATE_CONTACT",GearStatusRuntime.admit(statuses,contacts,
                    hit(actor,victim,"positive",channels[i],true),types[i],ControlProfile.NORMAL,
                    gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer).gate(),ids[i]);
            assertEquals("INELIGIBLE_HIT",GearStatusRuntime.admit(statuses,contacts,
                    hit(actor,victim,"wrong",Channel.WATER,true),types[i],ControlProfile.NORMAL,
                    gear,GearEffectSnapshot.EMPTY,0,0,0,0,producer).gate(),ids[i]);
            runtime.tick(actor,1,port);
            assertEquals(10,ticks.stream().mapToDouble(Double::doubleValue).sum(),1e-9,ids[i]);
        }
    }
    @Test void controllerAdmissionsKeepImmunityAndFiveStackSlow() {
        var clock=new AtomicLong();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),clock::get);
        var contacts=new GearStatusRuntime.Contacts();
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var slow=ControlledGearSnapshot.with("WA-058");
        for(int i=0;i<5;i++){
            clock.set(i*750_000_000L);
            assertEquals("ADMITTED",GearStatusRuntime.admit(statuses,contacts,
                    hit(UUID.randomUUID(),victim,"slow"+i,Channel.PHYSICAL,true),
                    RpgStatusType.SLOW,ControlProfile.NORMAL,slow,GearEffectSnapshot.EMPTY,
                    0,0,0,0,null).gate());
        }
        assertEquals(5,statuses.inspect(victim).active().get(RpgStatusType.SLOW).stacks());
        assertEquals(.5,statuses.strongestSlow(victim).magnitude(),1e-9);
        assertEquals("CONTROL_REJECTED",GearStatusRuntime.admit(statuses,contacts,
                hit(actor,victim,"at-cap",Channel.PHYSICAL,true),RpgStatusType.SLOW,ControlProfile.NORMAL,
                slow,GearEffectSnapshot.EMPTY,0,0,0,0,null).gate());
        assertEquals("CONTROL_REJECTED",GearStatusRuntime.admit(statuses,contacts,
                hit(actor,UUID.randomUUID(),"boss",Channel.PHYSICAL,true),RpgStatusType.STUN,
                new ControlProfile(false,true,false),ControlledGearSnapshot.with("WA-059"),
                GearEffectSnapshot.EMPTY,0,0,0,0,null).gate());
        var blind=GearStatusRuntime.admit(statuses,contacts,hit(actor,victim,"blind",Channel.PHYSICAL,true),
                RpgStatusType.BLIND,ControlProfile.NORMAL,ControlledGearSnapshot.with("WA-061"),
                GearEffectSnapshot.EMPTY,0,0,0,0,null);
        assertEquals("ADMITTED",blind.gate());
        assertEquals(.5,statuses.physicalMissChance(victim),1e-9);
        clock.set(9_000_000_000L);
        assertEquals(0,statuses.strongestSlow(victim).magnitude(),1e-9);
        assertEquals(0,statuses.physicalMissChance(victim),1e-9);
    }
    @Test void slowLocksAndIndependentExpiryPreserveOtherSources() {
        var clock=new AtomicLong();
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),clock::get);
        var target=UUID.randomUUID();var first=UUID.randomUUID();var second=UUID.randomUUID();
        assertEquals(StatusService.Outcome.APPLIED,
                statuses.applyStackingSlow(first,"GEAR_BASIC_ATTACK",target,ControlProfile.NORMAL).outcome());
        clock.set(500_000_000L);
        assertEquals(StatusService.Outcome.REJECTED,
                statuses.applyStackingSlow(second,"GEAR_BASIC_ATTACK",target,ControlProfile.NORMAL).outcome());
        clock.set(750_000_000L);
        assertEquals(StatusService.Outcome.REJECTED,
                statuses.applyStackingSlow(first,"GEAR_BASIC_ATTACK",target,ControlProfile.NORMAL).outcome());
        assertEquals(StatusService.Outcome.APPLIED,
                statuses.applyStackingSlow(second,"GEAR_BASIC_ATTACK",target,ControlProfile.NORMAL).outcome());
        clock.set(5_000_000_000L);
        assertEquals(1,statuses.inspect(target).active().get(RpgStatusType.SLOW).stacks());
        assertEquals(.1,statuses.strongestSlow(target).magnitude(),1e-9);
        clock.set(5_750_000_000L);
        assertFalse(statuses.inspect(target).active().containsKey(RpgStatusType.SLOW));
    }
    @Test void idleItemDoesNotProcButGlobalStatusPenetrationStillContributes() {
        var proc=ControlledGearSnapshot.with("WA-053").items().getFirst();
        var penetration=ControlledGearSnapshot.with("WA-064").items().getFirst();
        var equipped=new GearEffectSnapshot(java.util.List.of(proc,penetration));
        var actor=UUID.randomUUID();var target=UUID.randomUUID();
        var active=GearStatusRuntime.sourceScoped(equipped,proc.identity());
        var idle=GearStatusRuntime.sourceScoped(equipped,penetration.identity());
        assertEquals(0,idle.percent("WA-053"),1e-9);
        var runtime=new PeriodicStatusRuntime<String,String>();
        PeriodicStatusRuntime.Port<String,String> port=new PeriodicStatusRuntime.Port<>() {
            public boolean tick(PeriodicStatusRuntime.Source s,String c,String t,int i,double v,double seconds){return true;}
            public void changed(PeriodicStatusRuntime.Source s,String t,PeriodicStatusRuntime.View view){ }
        };
        GearStatusRuntime.PeriodicAdmission producer=(kind,h,g)->GearStatusRuntime.applyPeriodic(runtime,
                new PeriodicStatusRuntime.Source(actor,"root",target,kind),"committed","target",
                10,10,4,1,1,0,port,g);
        var hit=hit(actor,target,"physical",Channel.PHYSICAL,true);
        assertEquals("ADMITTED",GearStatusRuntime.admit(new StatusService(
                CombatBalanceProfile.loadCanonical(),()->0),new GearStatusRuntime.Contacts(),hit,
                RpgStatusType.BLEED,ControlProfile.NORMAL,active,equipped,GearEffectSnapshot.EMPTY,
                .4,0,.16,0,producer).gate());
        assertEquals("CHANCE_MISS",GearStatusRuntime.admit(new StatusService(
                CombatBalanceProfile.loadCanonical(),()->0),new GearStatusRuntime.Contacts(),hit,
                RpgStatusType.BLEED,ControlProfile.NORMAL,active,active,GearEffectSnapshot.EMPTY,
                .4,0,.16,0,producer).gate());
        assertEquals("CHANCE_MISS",GearStatusRuntime.admit(new StatusService(
                CombatBalanceProfile.loadCanonical(),()->0),new GearStatusRuntime.Contacts(),hit,
                RpgStatusType.BLEED,ControlProfile.NORMAL,idle,equipped,GearEffectSnapshot.EMPTY,
                0,0,0,0,producer).gate());
    }
    @Test void fearRemainsForTriggeringRootThenBreaksOnLaterDirectRoot() {
        var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var admission=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                hit(actor,victim,"fear",Channel.PHYSICAL,true),RpgStatusType.FEAR,ControlProfile.NORMAL,
                ControlledGearSnapshot.with("WA-062"),GearEffectSnapshot.EMPTY,0,0,0,0,null);
        assertEquals("ADMITTED",admission.gate());
        assertEquals(actor,statuses.fearSource(victim));
        assertFalse(statuses.breakFearOnDamage(victim,"root"));
        assertTrue(statuses.breakFearOnDamage(victim,"later"));
        assertNull(statuses.fearSource(victim));
    }
    @Test void staticStunSilenceAndRootUseCanonicalStatusAdmission() {
        String[] ids={"WA-057","WA-059","WA-060","WA-063"};
        RpgStatusType[] types={RpgStatusType.ELECTRIFIED,RpgStatusType.STUN,
                RpgStatusType.SILENCE,RpgStatusType.ROOT};
        Channel[] channels={Channel.LIGHTNING,Channel.PHYSICAL,Channel.PHYSICAL,Channel.PHYSICAL};
        double[] durations={6,.6,1.5,1};
        for(int i=0;i<ids.length;i++){
            var statuses=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
            var actor=UUID.randomUUID();var victim=UUID.randomUUID();
            var result=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                    hit(actor,victim,"positive",channels[i],true),types[i],ControlProfile.NORMAL,
                    ControlledGearSnapshot.with(ids[i]),GearEffectSnapshot.EMPTY,0,0,0,0,null);
            assertEquals("ADMITTED",result.gate(),ids[i]);
            assertEquals(durations[i],statuses.inspect(victim).active().get(types[i]).remainingSeconds(),1e-9,ids[i]);
            var protectedResult=GearStatusRuntime.admit(statuses,new GearStatusRuntime.Contacts(),
                    hit(actor,UUID.randomUUID(),"protected",channels[i],true),types[i],
                    new ControlProfile(true,false,false),ControlledGearSnapshot.with(ids[i]),
                    GearEffectSnapshot.EMPTY,0,0,0,0,null);
            assertEquals("CONTROL_REJECTED",protectedResult.gate(),ids[i]);
        }
    }
}
