package com.inigmasgames.hytalerpg.combat.resource;

import com.inigmasgames.hytalerpg.combat.status.ControlledGearSnapshot;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearRecoveryRuntimeTest {
    private static GearEffectSnapshot gear(String... ids) { return ControlledGearSnapshot.with(ids); }
    private static final class Native implements NativeResourcePort,GearRecoveryRuntime.Credit {
        double health=900,mana=400;
        public double current(ResourceType type){return type==ResourceType.HEALTH?health:mana;}
        public double maximum(ResourceType type){return type==ResourceType.HEALTH?1000:500;}
        public void setCurrent(ResourceType type,double amount){if(type==ResourceType.HEALTH)health=amount;else mana=amount;}
        public double admit(GearRecoveryRuntime.Pool pool,double requested,double currentMaximum) {
            ResourceType resource=pool==GearRecoveryRuntime.Pool.HIT_MANA?ResourceType.MANA:ResourceType.HEALTH;
            return restoreResourceAtMost(resource,requested,currentMaximum);
        }
    }
    @Test void actualHealthLossFlatRootDedupAndSharedWindowsUseNativeCredits() {
        var runtime=new GearRecoveryRuntime(id->true);var nativePort=new Native();
        var actor=UUID.randomUUID();var victim=UUID.randomUUID();
        var equipped=gear("WA-094","WA-095","WA-096","WA-097");
        var first=new GearRecoveryRuntime.Receipt(actor,"world","root","contact1",victim,50,0,true,true,false,false,false);
        runtime.onAttack(first,equipped,1000,500,0);
        runtime.onAttack(first,equipped,1000,500,0);
        assertEquals(5,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,0,nativePort).amount(),1e-9);
        assertEquals(3,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_MANA,500,0,nativePort).amount(),1e-9);
        assertEquals(905,nativePort.health,1e-9);assertEquals(403,nativePort.mana,1e-9);
        var second=new GearRecoveryRuntime.Receipt(actor,"world","root","contact2",UUID.randomUUID(),120,0,true,true,false,false,false);
        runtime.onAttack(second,equipped,1000,500,.1);
        for(int i=0;i<5;i++)runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"world","root","other"+i,
                UUID.randomUUID(),1000,0,true,true,false,false,false),equipped,1000,500,.1);
        assertEquals(35,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,.1,nativePort).amount(),1e-9);
        assertEquals(7,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_MANA,500,.1,nativePort).amount(),1e-9);
        assertEquals(40,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,1.1,nativePort).amount(),1e-9);
        assertEquals(10,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_MANA,500,1.1,nativePort).amount(),1e-9);
        assertEquals(0,runtime.pay(actor,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,4,nativePort).amount(),1e-9);
    }
    @Test void barrierReflectionAndDeathIdentityCannotCreateRecovery() {
        var runtime=new GearRecoveryRuntime(id->true);var nativePort=new Native();var actor=UUID.randomUUID();var target=UUID.randomUUID();
        var gear=gear("WA-094","WA-096");var killGear=gear("WA-098");
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","r","barrier",target,100,100,true,true,false,false,false),gear,1000,500,0);
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","r","reflect",target,100,80,true,true,false,true,false),gear,1000,500,0);
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,0,nativePort).amount());
        runtime.onKill(actor,"w","reward",true,killGear,1000,0);
        runtime.onKill(actor,"w","reward",true,killGear,1000,0);
        runtime.onKill(actor,"w","dummy",false,killGear,1000,0);
        assertEquals(20,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,0,nativePort).amount());
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,0,nativePort).amount());
    }
    @Test void explicitDisabledCapabilityPolicyRejectsRecovery() {
        var runtime=new GearRecoveryRuntime(id->false);var nativePort=new Native();var actor=UUID.randomUUID();
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","r","c",UUID.randomUUID(),100,50,
                true,true,false,false,false),gear("WA-094","WA-095","WA-096","WA-097"),1000,500,0);
        runtime.onKill(actor,"w","reward",true,gear("WA-098"),1000,0);
        for(var pool:GearRecoveryRuntime.Pool.values())
            assertEquals(0,runtime.pay(actor,"w",pool,1000,0,nativePort).amount());
    }
    @Test void cancelCannotResetRootOrRollingCapAndExpiredRootsFreeCapacity() {
        var runtime=new GearRecoveryRuntime(id->true);var port=new Native();var actor=UUID.randomUUID();
        var receipt=new GearRecoveryRuntime.Receipt(actor,"w","r","c",UUID.randomUUID(),100,50,
                true,true,false,false,false);
        var source=gear("WA-094","WA-096");
        runtime.onAttack(receipt,source,1000,500,0);
        assertEquals(5,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,0,port).amount());
        runtime.cancel(actor);
        runtime.onAttack(receipt,source,1000,500,.1);
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,.1,port).amount());
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","new","c",UUID.randomUUID(),1000,0,
                true,true,false,false,false),source,1000,500,.2);
        assertEquals(35,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,.2,port).amount());
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,.3,port).amount());
        runtime.maintain(31);
        runtime.onAttack(receipt,source,1000,500,31);
        assertTrue(runtime.hasPending(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH));
    }
    @Test void reservationAwareManaCapAndSentinelSelfOnly() {
        var runtime=new GearRecoveryRuntime(id->true);var port=new Native();var actor=UUID.randomUUID();
        var reservations=new ReservationService();
        var resources=new RpgResourceService(
                com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),reservations);
        reservations.addFixed(actor,"aura",300,port);
        port.mana=100;
        var source=gear("WA-094","WA-095","WA-096","WA-097");
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","r","c",UUID.randomUUID(),100,0,
                true,true,false,false,false),source,1000,resources.spendableMaximum(actor,ResourceType.MANA,port),0);
        double spendable=resources.spendableMaximum(actor,ResourceType.MANA,port);
        assertEquals(200,spendable);
        assertEquals(4,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_MANA,spendable,0,
                (pool,amount,maximum)->port.restoreResourceAtMost(ResourceType.MANA,amount,maximum)).amount());
        assertEquals(104,port.mana);
        var sentinel=UUID.randomUUID();
        runtime.onAttack(new GearRecoveryRuntime.Receipt(sentinel,"w","s","c",UUID.randomUUID(),100,0,
                true,true,false,false,false),source,1000,0,0,true);
        assertFalse(runtime.hasPending(sentinel,"w",GearRecoveryRuntime.Pool.HIT_MANA));
        assertTrue(runtime.hasPending(sentinel,"w",GearRecoveryRuntime.Pool.HIT_HEALTH));
    }
    @Test void productionPayoutCoordinatorUsesConfirmedHealthAndReservationAwareMana() {
        var runtime=new GearRecoveryRuntime(id->true);var port=new Native();var actor=UUID.randomUUID();
        var reservations=new ReservationService();
        var resources=new RpgResourceService(
                com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical(),reservations);
        var payout=new GearRecoveryPayout(runtime,resources);
        reservations.addFixed(actor,"aura",300,port);
        port.mana=196;
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","r","c",UUID.randomUUID(),100,0,
                true,true,false,false,false),gear("WA-094","WA-095","WA-096","WA-097"),1000,200,0);
        var first=payout.pay(actor,"w",1000,0,port,(requested,maximum)->
                port.restoreResourceAtMost(ResourceType.HEALTH,requested,maximum));
        assertEquals(7,first.hitHealth(),1e-9);
        assertEquals(4,first.hitMana(),1e-9);
        assertEquals(907,port.health,1e-9);
        assertEquals(200,port.mana,1e-9);
        var full=payout.pay(actor,"w",1000,.1,port,(requested,maximum)->
                port.restoreResourceAtMost(ResourceType.HEALTH,requested,maximum));
        assertEquals(0,full.hitMana());
        assertEquals(0,full.hitHealth());
    }
    @Test void creditedDeathCallbackPaysOnlyOncePerDurableRewardIdentity() {
        var runtime=new GearRecoveryRuntime(id->true);var port=new Native();var actor=UUID.randomUUID();
        var callback=new com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery(runtime);
        var credit=new com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery.Credit(
                actor,"w","durable-event",gear("WA-098"),1000);
        callback.accepted(credit);callback.accepted(credit);
        double now=System.nanoTime()/1e9;
        assertEquals(20,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,now,port).amount(),1e-9);
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,now,port).amount());
        runtime.cancel(actor);
        callback.accepted(credit);
        assertEquals(0,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,now,port).amount());
    }
    @Test void boundedLedgersRejectAtCapacityThenReopenAfterTimedExpiry() {
        var runtime=new GearRecoveryRuntime(id->true,2,2);var actor=UUID.randomUUID();
        var source=gear("WA-094");var killSource=gear("WA-098");
        for(int i=0;i<3;i++)runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","root"+i,"c",
                UUID.randomUUID(),10,0,true,true,false,false,false),source,1000,500,0);
        assertEquals(6,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH,1000,0,new Native()).amount());
        runtime.maintain(31);
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","later","c",UUID.randomUUID(),10,0,
                true,true,false,false,false),source,1000,500,31);
        assertTrue(runtime.hasPending(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH));
        runtime.onKill(actor,"w","one",true,killSource,1000,31);
        runtime.onKill(actor,"w","two",true,killSource,1000,31);
        runtime.onKill(actor,"w","three",true,killSource,1000,31);
        assertEquals(40,runtime.pay(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH,1000,31,new Native()).amount());
        runtime.maintain(3632);
        runtime.onKill(actor,"w","later",true,killSource,1000,3632);
        assertTrue(runtime.hasPending(actor,"w",GearRecoveryRuntime.Pool.KILL_HEALTH));
    }
    @Test void cancelledStatusSummonAndNonhostileReceiptsNeverPay() {
        var runtime=new GearRecoveryRuntime(id->true);var actor=UUID.randomUUID();var source=gear("WA-094","WA-096");
        var target=UUID.randomUUID();
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","cancel","c",target,50,0,
                true,true,true,false,false),source,1000,500,0);
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","status","c",target,50,0,
                true,false,false,false,false),source,1000,500,0);
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","summon","c",target,50,0,
                true,true,false,false,true),source,1000,500,0);
        runtime.onAttack(new GearRecoveryRuntime.Receipt(actor,"w","friendly","c",target,50,0,
                false,true,false,false,false),source,1000,500,0);
        assertFalse(runtime.hasPending(actor,"w",GearRecoveryRuntime.Pool.HIT_HEALTH));
    }
}
