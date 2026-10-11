package com.inigmasgames.hytalerpg.execution.support;

import static org.junit.jupiter.api.Assertions.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline bridge profile. This exercises active stages, not ECS or a game server. */
class R261FinalCombatLatencyTest {
    @TempDir Path root;
    private static final class Port implements NativeResourcePort {
        float mana=100;
        public double current(ResourceType type){return mana;}
        public double maximum(ResourceType type){return 100;}
        public void setCurrent(ResourceType type,double value){mana=(float)value;}
    }
    private static CycloneBarrierLedger.State paid(CycloneBarrierLedger journal,
            CycloneBarrierLedger.Lease lease,UUID generation,double capacity){
        UUID activation=UUID.randomUUID();
        var tx=new CycloneActivationTransaction(journal);
        tx.begin(lease,activation,generation,capacity,()->true,()->true,()->true);
        tx.manaCommitted(lease,activation,"native-paid",100);
        tx.cooldownCommitted(lease,activation,"cooldown-paid");
        return tx.publish(lease,activation);
    }
    private record Fixture(UUID recipient,UUID world,UUID generation,CycloneBarrierLedger own,
            CycloneBarrierLedger.Lease ownLease,CycloneReflectionJournal reflections,
            CycloneReflectionJournal.Behavior behavior,CycloneSharedAegisLedger shared,
            CycloneSharedAegisLedger.Lease sharedLease,FiniteSupportEffects finite,Port port){}
    private Fixture fixture(int index){
        Path path=root.resolve("actor-"+index);
        UUID recipient=UUID.randomUUID(),caster=UUID.randomUUID(),world=UUID.randomUUID();
        UUID generation=UUID.randomUUID();
        var own=new CycloneBarrierLedger(path.resolve("own"));var ownLease=own.attach(recipient);
        var reflections=new CycloneReflectionJournal(path.resolve("own"));
        var behavior=new CycloneReflectionJournal.Behavior(1,recipient,generation,
                UUID.randomUUID(),"root","instance","correlation",.2,4);
        // The exact activation is shared with the frozen behavior.
        var activation=behavior.activation();var tx=new CycloneActivationTransaction(own);
        tx.begin(ownLease,activation,generation,4,()->true,()->true,()->true);
        tx.manaCommitted(ownLease,activation,"native-paid",100);
        tx.cooldownCommitted(ownLease,activation,"cooldown-paid");
        reflections.install(behavior);tx.publish(ownLease,activation);
        var donor=new CycloneBarrierLedger(path.resolve("donor"));var donorLease=donor.attach(caster);
        var donorState=paid(donor,donorLease,UUID.randomUUID(),4);
        var shared=new CycloneSharedAegisLedger(path.resolve("shared"));
        shared.preload(recipient,(id,gen)->false);var sharedLease=shared.attachPrepared(recipient);
        assertTrue(shared.grant(donorState,recipient,sharedLease));
        var finite=new FiniteSupportEffects();
        finite.restoreIntrinsicShield(new FiniteSupportEffects.IntrinsicShield(
                new FiniteSupportEffects.IntrinsicShieldKey(world,recipient,1,"ME-027"),1,0));
        return new Fixture(recipient,world,generation,own,ownLease,reflections,behavior,
                shared,sharedLease,finite,new Port());
    }
    private static double percentile(long[] sorted,double fraction){
        return sorted[(int)Math.ceil(sorted.length*fraction)-1]/1e6;
    }
    @Test void activeStagesRecordWriteAndLockCostWithoutAcknowledgingUnforcedAbsorption(){
        long[] elapsed=new long[40];long ownForce=0,ownWait=0,sharedForce=0,sharedWait=0,reflectForce=0,reflectWait=0;
        var owner=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        for(int i=0;i<elapsed.length;i++){
            var f=fixture(i);String receipt="native-hit-"+i;
            var beforeOwn=f.own().metrics();var beforeShared=f.shared().metrics();
            long started=System.nanoTime();
            var own=CycloneDamageStage.absorb(f.own(),f.ownLease(),f.generation(),receipt,10);
            var child=f.shared().debit(f.sharedLease(),receipt,own.nativeRemainder());
            assertTrue(f.reflections().claim(f.behavior(),receipt));
            var finite=f.finite().shieldHit(f.world(),f.recipient(),child.nativeRemainder(),false,0,
                    (shield,amount)->false);
            var mana=ManaBeforeLifeStage.apply((float)finite.remainder(),.5,f.recipient(),owner,f.port());
            elapsed[i]=System.nanoTime()-started;
            assertEquals(4,own.debit().absorbed());assertEquals(2,child.absorbed());
            assertEquals(1,finite.absorbed());assertEquals(1.5,mana.manaSpent(),.001);
            assertEquals(1.5,mana.healthDamage(),.001);
            ownForce+=f.own().metrics().totalNanos()-beforeOwn.totalNanos();
            ownWait+=f.own().metrics().debitLockWaitNanos();
            sharedForce+=f.shared().metrics().forcedWriteNanos()-beforeShared.forcedWriteNanos();
            sharedWait+=f.shared().metrics().debitLockWaitNanos();
            reflectForce+=f.reflections().metrics().forceNanos();
            reflectWait+=f.reflections().metrics().claimLockWaitNanos();
        }
        Arrays.sort(elapsed);
        System.out.printf(Locale.ROOT,
                "R261_FINAL_ACTIVE_PROFILE hits=%d p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f worstMs=%.3f ownForceMs=%.3f ownWaitMs=%.3f sharedForceMs=%.3f sharedWaitMs=%.3f reflectForceMs=%.3f reflectWaitMs=%.3f%n",
                elapsed.length,percentile(elapsed,.5),percentile(elapsed,.95),percentile(elapsed,.99),
                elapsed[elapsed.length-1]/1e6,ownForce/1e6,ownWait/1e6,
                sharedForce/1e6,sharedWait/1e6,reflectForce/1e6,reflectWait/1e6);
    }
}
