package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.support.CycloneActivationTransaction;
import com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.combat.resource.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CycloneBarrierLedgerTest {
    @TempDir Path root;
    private void repay(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease){
        var resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        var nativeMana=new NativeResourcePort(){
            double current=200;
            public double current(ResourceType type){return current;}
            public double maximum(ResourceType type){return 200;}
            public void setCurrent(ResourceType type,double value){current=value;}
        };
        assertTrue(ledger.paymentRecoveryRequired(lease));
        assertTrue(ledger.reconcilePaymentAfterRestart(lease,resources,nativeMana));
        assertFalse(ledger.paymentRecoveryRequired(lease));
        assertEquals(160,nativeMana.current(ResourceType.MANA));
    }
    private record Active(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease,UUID player,UUID generation,UUID activation){}
    private Active active(){
        var player=UUID.randomUUID();var generation=UUID.randomUUID();var activation=UUID.randomUUID();
        var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);
        assertEquals(CycloneActivationTransaction.Result.PREPARED,
                tx.begin(lease,activation,generation,150,()->true,()->true,()->true));
        tx.manaCommitted(lease,activation,"native-mana-1",40);
        tx.cooldownCommitted(lease,activation,"cooldown-spend-1");
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,tx.publish(lease,activation).phase());
        return new Active(ledger,lease,player,generation,activation);
    }
    @Test void forcedDebitSurvivesRestartWithoutRefill() {
        var a=active();
        var first=a.ledger.debit(a.lease,a.generation,"hit-a",40);
        assertEquals(40,first.absorbed());assertEquals(110,first.remaining());
        var second=a.ledger.debit(a.lease,a.generation,"hit-b",140);
        assertEquals(110,second.absorbed());assertEquals(30,second.damageRemaining());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,a.ledger.state(a.lease).phase());
        a.ledger.detach(a.lease);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(0,restarted.state(lease).remaining());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(lease).phase());
    }
    @Test void duplicateAndStaleReceiptsCannotDebitTwice() {
        var a=active();
        assertEquals(25,a.ledger.debit(a.lease,a.generation,"hit-a",25).absorbed());
        assertTrue(a.ledger.debit(a.lease,a.generation,"hit-a",25).duplicate());
        assertEquals(125,a.ledger.state(a.lease).remaining());
        var lease=a.ledger.attach(a.player);
        assertTrue(a.ledger.debit(lease,a.generation,"hit-a",25).duplicate());
        assertEquals(0,a.ledger.debit(lease,UUID.randomUUID(),"hit-b",30).absorbed());
        assertEquals(125,a.ledger.state(lease).remaining());
        assertThrows(IllegalStateException.class,()->a.ledger.debit(a.lease,a.generation,"stale",1));
    }
    @Test void concurrentHitsAreSerializedAndOverflowIsExact() throws Exception {
        var a=active();
        var pool=Executors.newFixedThreadPool(8);
        try{
            var jobs=new java.util.ArrayList<java.util.concurrent.Future<CycloneBarrierLedger.Debit>>();
            for(int i=0;i<20;i++){final int n=i;jobs.add(pool.submit(()->a.ledger.debit(a.lease,a.generation,"hit-"+n,10)));}
            double absorbed=0;
            for(var job:jobs)absorbed+=job.get(30,TimeUnit.SECONDS).absorbed();
            assertEquals(150,absorbed,1e-9);assertEquals(0,a.ledger.state(a.lease).remaining());
        }finally{pool.shutdownNow();}
    }
    @Test void pendingActivationNeverRestoresAsFreeProtection() {
        var player=UUID.randomUUID();var id=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);
        assertEquals(CycloneActivationTransaction.Result.PREPARED,
                tx.begin(lease,id,UUID.randomUUID(),100,()->true,()->true,()->true));
        tx.manaCommitted(lease,id,"paid-but-no-cooldown",40);
        var restarted=new CycloneBarrierLedger(root);var current=restarted.attach(player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(current).phase());
        assertEquals(0,restarted.state(current).remaining());
        assertEquals(CycloneActivationTransaction.Result.DUPLICATE_INACTIVE,
                new CycloneActivationTransaction(restarted).begin(current,id,UUID.randomUUID(),100,()->true,()->true,()->true));
    }
    @Test void failedAdmissionNeverCreatesJournalAndCompensationIsIdempotent() {
        var player=UUID.randomUUID();var id=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);
        assertThrows(IllegalStateException.class,()->tx.begin(lease,id,UUID.randomUUID(),100,()->true,()->false,()->true));
        assertEquals(CycloneBarrierLedger.Phase.NONE,ledger.state(lease).phase());
        var generation=UUID.randomUUID();
        tx.begin(lease,id,generation,100,()->true,()->true,()->true);
        var refunds=new AtomicInteger();
        var compensation=new CycloneActivationTransaction.Compensation(){
            public void refundCooldown(){refunds.incrementAndGet();}
            public void refundMana(){refunds.incrementAndGet();}
        };
        assertEquals(CycloneActivationTransaction.Result.ABORTED,tx.abort(lease,id,true,true,false,compensation));
        assertEquals(CycloneActivationTransaction.Result.ABORTED,tx.abort(lease,id,true,true,false,compensation));
        assertEquals(2,refunds.get());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,ledger.state(lease).phase());
    }
    @Test void tornTailIsIgnoredButCompletedCorruptionFailsClosed() throws Exception {
        var a=active();a.ledger.debit(a.lease,a.generation,"hit-a",15);
        Path journal=root.resolve(a.player+".journal");
        Files.writeString(journal,"{\"torn\":",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(135,restarted.state(lease).remaining());
        Files.writeString(journal,"{\"bad\":true}\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IllegalStateException.class,()->new CycloneBarrierLedger(root).attach(a.player));
    }
    @Test void oldSaveWithoutSidecarIsEmptyAndDeathPersists() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);
        var empty=ledger.attach(player);assertEquals(CycloneBarrierLedger.Phase.NONE,ledger.state(empty).phase());
        var a=active();a.ledger.clearForDeath(a.lease);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(lease).phase());
        assertEquals(0,restarted.state(lease).remaining());
    }
    @Test void oldActivationIdCannotBeReplayedAfterAnotherGeneration() {
        var a=active();a.ledger.clearForDeath(a.lease);
        var tx=new CycloneActivationTransaction(a.ledger);
        var next=UUID.randomUUID();
        assertEquals(CycloneActivationTransaction.Result.PREPARED,
                tx.begin(a.lease,next,UUID.randomUUID(),80,()->true,()->true,()->true));
        a.ledger.abort(a.lease,next,false);
        var resumed=new CycloneBarrierLedger(root);var lease=resumed.attach(a.player);
        assertEquals(CycloneActivationTransaction.Result.DUPLICATE_INACTIVE,
                new CycloneActivationTransaction(resumed).begin(lease,a.activation,UUID.randomUUID(),150,()->true,()->true,()->true));
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,resumed.state(lease).phase());
    }
    @Test void nativeDamageCanOnlyBeReducedAfterConfirmedForcedWrite() throws Exception {
        var a=active();Path journal=root.resolve(a.player+".journal");Path recovery=root.resolve("saved.journal");
        Files.move(journal,recovery);Files.createDirectory(journal);
        assertThrows(IllegalStateException.class,()->a.ledger.debit(a.lease,a.generation,"hit-unwritten",30));
        assertThrows(IllegalStateException.class,()->a.ledger.state(a.lease));
        Files.delete(journal);Files.move(recovery,journal);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(150,restarted.state(lease).remaining());
        assertEquals(0,restarted.debit(lease,a.generation,"quarantined",30).absorbed());
        repay(restarted,lease);
        assertEquals(30,restarted.debit(lease,a.generation,"hit-retry",30).absorbed());
    }
    @Test void preloadedReconnectDoesNotRefillAndOldLeaseCannotClearNewSession() {
        var a=active();a.ledger.debit(a.lease,a.generation,"first",60);
        a.ledger.detach(a.lease);
        a.ledger.preload(a.player);
        var next=a.ledger.attachPrepared(a.player);
        a.ledger.detach(a.lease); // delayed callback from the old connection
        assertEquals(90,a.ledger.state(next).remaining());
        repay(a.ledger,next);
        assertEquals(10,a.ledger.debit(next,a.generation,"second",10).absorbed());
        var restarted=new CycloneBarrierLedger(root);var restored=restarted.attach(a.player);
        assertEquals(80,restarted.state(restored).remaining());
    }
    @Test void simultaneousCastRequestsProduceOnePendingGeneration() throws Exception {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var pool=Executors.newFixedThreadPool(2);
        try{
            var first=pool.submit(()->tx.begin(lease,UUID.randomUUID(),UUID.randomUUID(),100,()->true,()->true,()->true));
            var second=pool.submit(()->tx.begin(lease,UUID.randomUUID(),UUID.randomUUID(),100,()->true,()->true,()->true));
            int prepared=0,rejected=0;
            for(var future:java.util.List.of(first,second))try{if(future.get(10,TimeUnit.SECONDS)==CycloneActivationTransaction.Result.PREPARED)prepared++;}
                catch(java.util.concurrent.ExecutionException error){rejected++;}
            assertEquals(1,prepared);assertEquals(1,rejected);
            assertEquals(CycloneBarrierLedger.Phase.PENDING,ledger.state(lease).phase());
        }finally{pool.shutdownNow();}
    }
    @Test void cooldownProofAndManaProofAreRequiredBeforeProtection() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var id=UUID.randomUUID();
        tx.begin(lease,id,UUID.randomUUID(),120,()->true,()->true,()->true);
        assertThrows(IllegalStateException.class,()->tx.cooldownCommitted(lease,id,"early"));
        assertThrows(IllegalStateException.class,()->tx.publish(lease,id));
        tx.manaCommitted(lease,id,"paid",40);
        assertThrows(IllegalStateException.class,()->tx.publish(lease,id));
        tx.cooldownCommitted(lease,id,"durable-cooldown");
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,tx.publish(lease,id).phase());
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,tx.publish(lease,id).phase());
        assertEquals(4,ledger.state(lease).revision());
    }
    @Test void uncertainCompensationSettlesConservativelyAndRetryCanProceed() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var id=UUID.randomUUID();
        tx.begin(lease,id,UUID.randomUUID(),120,()->true,()->true,()->true);
        tx.manaCommitted(lease,id,"paid",40);
        assertEquals(CycloneActivationTransaction.Result.RECOVERY_REQUIRED,
                tx.abort(lease,id,true,false,false,new CycloneActivationTransaction.Compensation(){
                    public void refundMana(){throw new IllegalStateException("temporary failure");}
                    public void refundCooldown(){}
                }));
        assertEquals(CycloneBarrierLedger.Phase.RECOVERY_PENDING,ledger.state(lease).phase());
        var recovered=new CycloneBarrierLedger(root);var next=recovered.attach(player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,recovered.state(next).phase());
        assertEquals(CycloneActivationTransaction.Result.PREPARED,
                new CycloneActivationTransaction(recovered).begin(next,UUID.randomUUID(),UUID.randomUUID(),120,
                        ()->true,()->true,()->true));
    }
    @Test void tinyUnrepresentableDamageNeverGetsFreeAbsorption() {
        var a=active();var result=a.ledger.debit(a.lease,a.generation,"tiny",Double.MIN_VALUE);
        assertEquals(0,result.absorbed());assertEquals(Double.MIN_VALUE,result.damageRemaining());
        assertEquals(150,a.ledger.state(a.lease).remaining());
    }
    @Test void nativeFloatNarrowingCannotGrantUnrecordedAbsorption() {
        var a=active();var debit=a.ledger.debit(a.lease,a.generation,"overflow",150.2);
        assertEquals(150,debit.absorbed());
        assertTrue(CycloneBarrierLedger.nativeRemainder(debit.damageRemaining())>=debit.damageRemaining());
        assertThrows(IllegalArgumentException.class,()->CycloneBarrierLedger.nativeRemainder(Double.NaN));
    }
    @Test void duplicateOrOutOfOrderDurableRevisionFailsClosed() throws Exception {
        var a=active();Path journal=root.resolve(a.player+".journal");
        var lines=Files.readAllLines(journal);
        Files.writeString(journal,lines.get(1)+"\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IllegalStateException.class,()->new CycloneBarrierLedger(root).attach(a.player));
    }
    @Test void interruptedActivationAtEveryProofStageCannotGrantFreeBarrier() {
        for(int stage=0;stage<3;stage++){
            var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
            var id=UUID.randomUUID();var tx=new CycloneActivationTransaction(ledger);
            tx.begin(lease,id,UUID.randomUUID(),100,()->true,()->true,()->true);
            if(stage>=1)tx.manaCommitted(lease,id,"native-mana",40);
            if(stage>=2)tx.cooldownCommitted(lease,id,"durable-cooldown");
            var restarted=new CycloneBarrierLedger(root);var next=restarted.attach(player);
            assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(next).phase());
            assertEquals(0,restarted.state(next).remaining());
        }
    }
    @Test void nativeManaAndCooldownAuthorityReceiptsGateActivation() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var id=UUID.randomUUID();
        var resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        class Mana implements NativeResourcePort {
            double current=200;
            public double current(ResourceType type){return current;}
            public double maximum(ResourceType type){return 200;}
            public void setCurrent(ResourceType type,double value){current=value;}
        }
        var nativeMana=new Mana();var cost=resources.reserveCost(player,new ResourceCost(ResourceType.MANA,40),nativeMana);
        var cooldowns=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->0L);
        tx.begin(lease,id,UUID.randomUUID(),100,()->true,
                ()->resources.canAfford(player,cost.cost(),nativeMana),()->cooldowns.canActivate(player,"spirit_shield",1));
        tx.commitNativeMana(lease,id,resources,cost,nativeMana);
        assertEquals(160,nativeMana.current);
        assertThrows(IllegalStateException.class,()->tx.publish(lease,id));
        var spend=cooldowns.spendCharge(player,"spirit_shield",1,20,1,0,null);
        tx.commitNativeCooldown(lease,id,cooldowns,spend);
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,tx.publish(lease,id).phase());
        assertEquals(cost.tokenId().toString(),ledger.state(lease).manaReceipt());
        assertEquals(spend.token().toString(),ledger.state(lease).cooldownReceipt());
    }
    @Test void repeatedHitBenchmarkRecordsBoundedPerPlayerSyncCost() {
        var a=active();long before=System.nanoTime();
        for(int i=0;i<50;i++)assertEquals(1,a.ledger.debit(a.lease,a.generation,"bench-"+i,1).absorbed());
        long elapsed=System.nanoTime()-before;var metrics=a.ledger.metrics();
        assertEquals(54,metrics.writes());assertEquals(0,metrics.failedWrites());
        assertEquals(100,a.ledger.state(a.lease).remaining());
        System.out.printf("CYCLONE_DEBIT_BENCHMARK count=50 avgStorageMs=%.3f maxStorageMs=%.3f avgHandlerMs=%.3f%n",
                (metrics.totalNanos()/1e6)/(double)metrics.writes(),metrics.maximumNanos()/1e6,elapsed/1e6/50);
    }
    @Test void simultaneousHitBenchmarkRecordsLockContention() throws Exception {
        var a=active();var pool=Executors.newFixedThreadPool(8);long started=System.nanoTime();
        try{
            var futures=new java.util.ArrayList<java.util.concurrent.Future<CycloneBarrierLedger.Debit>>();
            for(int i=0;i<80;i++){final int n=i;futures.add(pool.submit(()->a.ledger.debit(a.lease,a.generation,"parallel-"+n,1)));}
            double absorbed=0;for(var future:futures)absorbed+=future.get(30,TimeUnit.SECONDS).absorbed();
            assertEquals(80,absorbed);assertEquals(70,a.ledger.state(a.lease).remaining());
            var metrics=a.ledger.metrics();
            System.out.printf("CYCLONE_PARALLEL_BENCHMARK hits=80 threads=8 avgStorageMs=%.3f maxStorageMs=%.3f wallMs=%.3f%n",
                    metrics.totalNanos()/1e6/metrics.writes(),metrics.maximumNanos()/1e6,(System.nanoTime()-started)/1e6);
        }finally{pool.shutdownNow();}
    }
    @Test void twoSlotCheckpointPreservesDebitsAndReceiptHistory() throws Exception {
        var player=UUID.randomUUID();var generation=UUID.randomUUID();var id=UUID.randomUUID();
        var ledger=new CycloneBarrierLedger(root,8192);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);
        tx.begin(lease,id,generation,200,()->true,()->true,()->true);
        tx.manaCommitted(lease,id,"mana",40);tx.cooldownCommitted(lease,id,"cooldown");tx.publish(lease,id);
        for(int i=0;i<35;i++)assertEquals(1,ledger.debit(lease,generation,"checkpoint-hit-"+i,1).absorbed());
        assertTrue(Files.exists(root.resolve(player+".journal.alt")));
        var restarted=new CycloneBarrierLedger(root,8192);var next=restarted.attach(player);
        assertEquals(165,restarted.state(next).remaining());
        assertTrue(restarted.debit(next,generation,"checkpoint-hit-0",1).duplicate());
        repay(restarted,next);
        assertEquals(1,restarted.debit(next,generation,"after-restart",1).absorbed());
        assertEquals(164,restarted.state(next).remaining());
    }
    @Test void tornInactiveCheckpointCannotRestoreAbsorbedDamage() throws Exception {
        var a=active();a.ledger.debit(a.lease,a.generation,"durable",20);
        Files.writeString(root.resolve(a.player+".journal.alt"),"{\"checkpoint\":",StandardCharsets.UTF_8);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(130,restarted.state(lease).remaining());
    }
    @Test void restartWithUnpayableNativeManaDepletesInsteadOfGrantingProtection() {
        var a=active();a.ledger.debit(a.lease,a.generation,"prior-hit",25);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertTrue(restarted.paymentRecoveryRequired(lease));
        var resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
        var empty=new NativeResourcePort(){
            public double current(ResourceType type){return 0;}
            public double maximum(ResourceType type){return 200;}
            public void setCurrent(ResourceType type,double value){}
        };
        assertFalse(restarted.reconcilePaymentAfterRestart(lease,resources,empty));
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(lease).phase());
        assertEquals(0,restarted.debit(lease,a.generation,"new-hit",20).absorbed());
        var again=new CycloneBarrierLedger(root);var recovered=again.attach(a.player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,again.state(recovered).phase());
    }
    @Test void compensationCanSettleAfterOneFailureWithoutDoubleRefund() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var id=UUID.randomUUID();
        tx.begin(lease,id,UUID.randomUUID(),100,()->true,()->true,()->true);
        tx.manaCommitted(lease,id,"paid",40);
        var tries=new AtomicInteger();
        var compensation=new CycloneActivationTransaction.Compensation(){
            public void refundCooldown(){}
            public void refundMana(){if(tries.incrementAndGet()==1)throw new IllegalStateException("temporary");}
        };
        assertEquals(CycloneActivationTransaction.Result.RECOVERY_REQUIRED,
                tx.abort(lease,id,true,false,false,compensation));
        assertEquals(CycloneBarrierLedger.Phase.RECOVERY_PENDING,ledger.state(lease).phase());
        assertEquals(CycloneActivationTransaction.Result.ABORTED,
                tx.abort(lease,id,true,false,false,compensation));
        assertEquals(CycloneActivationTransaction.Result.ABORTED,
                tx.abort(lease,id,true,false,false,compensation));
        assertEquals(2,tries.get());
    }
    @Test void cooldownPersistenceFailureLeavesNoBarrierAndCanCompensateMana() {
        var player=UUID.randomUUID();var ledger=new CycloneBarrierLedger(root);var lease=ledger.attach(player);
        var tx=new CycloneActivationTransaction(ledger);var id=UUID.randomUUID();
        tx.begin(lease,id,UUID.randomUUID(),100,()->true,()->true,()->true);
        tx.manaCommitted(lease,id,"paid",40);
        var cooldowns=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->0L);
        cooldowns.bindPersistence(new RpgCooldownService.Persistence(){
            public java.util.Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> load(UUID actor){return java.util.Map.of();}
            public void save(UUID actor,java.util.Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> values){
                throw new IllegalStateException("disk failure");
            }
        });
        assertThrows(IllegalStateException.class,()->cooldowns.spendCharge(player,"spirit_shield",1,20,1,0,null));
        var refunds=new AtomicInteger();
        assertEquals(CycloneActivationTransaction.Result.ABORTED,
                tx.abort(lease,id,true,false,false,new CycloneActivationTransaction.Compensation(){
                    public void refundCooldown(){}
                    public void refundMana(){refunds.incrementAndGet();}
                }));
        assertEquals(1,refunds.get());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,ledger.state(lease).phase());
    }
}
