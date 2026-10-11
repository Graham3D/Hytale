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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CycloneBarrierLedgerTest {
    @TempDir Path root;
    private void invalidate(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease){
        assertTrue(ledger.paymentRecoveryRequired(lease));
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,ledger.invalidateUncertainPayment(lease).phase());
        assertFalse(ledger.paymentRecoveryRequired(lease));
        assertEquals(0,ledger.state(lease).remaining());
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
        invalidate(restarted,lease);
        assertEquals(0,restarted.debit(lease,a.generation,"hit-retry",30).absorbed());
    }
    @Test void preloadedReconnectDoesNotRefillAndOldLeaseCannotClearNewSession() {
        var a=active();a.ledger.debit(a.lease,a.generation,"first",60);
        a.ledger.detach(a.lease);
        a.ledger.preload(a.player);
        var next=a.ledger.attachPrepared(a.player);
        a.ledger.detach(a.lease); // delayed callback from the old connection
        assertEquals(90,a.ledger.state(next).remaining());
        invalidate(a.ledger,next);
        assertEquals(0,a.ledger.debit(next,a.generation,"second",10).absorbed());
        var restarted=new CycloneBarrierLedger(root);var restored=restarted.attach(a.player);
        assertEquals(0,restarted.state(restored).remaining());
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
    @Test void multiPlayerForcedDebitLatencyProfile() throws Exception {
        profileDebits("repeated-one-player",1,240,false);
        profileDebits("concurrent-one-player",8,30,false);
        profileDebits("concurrent-eight-players",8,30,true);
    }
    private void profileDebits(String label,int threads,int hitsPerThread,boolean separatePlayers) throws Exception {
        var ledger=new CycloneBarrierLedger(root.resolve(label));
        var players=new ArrayList<Active>();
        for(int i=0;i<(separatePlayers?threads:1);i++){
            var player=UUID.randomUUID();var generation=UUID.randomUUID();var activation=UUID.randomUUID();
            var lease=ledger.attach(player);var tx=new CycloneActivationTransaction(ledger);
            tx.begin(lease,activation,generation,500,()->true,()->true,()->true);
            tx.manaCommitted(lease,activation,"mana-"+i,40);
            tx.cooldownCommitted(lease,activation,"cooldown-"+i);tx.publish(lease,activation);
            players.add(new Active(ledger,lease,player,generation,activation));
        }
        var pool=Executors.newFixedThreadPool(threads);
        var start=new CountDownLatch(1);var ready=new CountDownLatch(threads);
        try{
            var jobs=new ArrayList<java.util.concurrent.Future<long[]>>();
            for(int t=0;t<threads;t++){
                final int task=t;final var actor=players.get(separatePlayers?t:0);
                jobs.add(pool.submit(()->{
                    var samples=new long[hitsPerThread];ready.countDown();start.await();
                    for(int i=0;i<hitsPerThread;i++){
                        long before=System.nanoTime();
                        var debit=ledger.debit(actor.lease,actor.generation,label+"-"+task+"-"+i,1);
                        samples[i]=System.nanoTime()-before;
                        if(debit.absorbed()!=1)throw new AssertionError("Unconfirmed or lost benchmark debit");
                    }
                    return samples;
                }));
            }
            assertTrue(ready.await(10,TimeUnit.SECONDS));long wallStart=System.nanoTime();start.countDown();
            var durations=new long[threads*hitsPerThread];int offset=0;
            for(var job:jobs){var samples=job.get(45,TimeUnit.SECONDS);System.arraycopy(samples,0,durations,offset,samples.length);offset+=samples.length;}
            long wall=System.nanoTime()-wallStart;Arrays.sort(durations);
            var metrics=ledger.metrics();assertEquals(0,metrics.failedWrites());
            for(var actor:players)assertEquals(500-(separatePlayers?hitsPerThread:threads*hitsPerThread),ledger.state(actor.lease).remaining());
            System.out.printf("CYCLONE_FORCE_PROFILE scenario=%s hits=%d threads=%d p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f worstMs=%.3f wallMs=%.3f meanAppendMs=%.3f maxAppendMs=%.3f%n",
                    label,durations.length,threads,percentile(durations,50),percentile(durations,95),percentile(durations,99),durations[durations.length-1]/1e6,
                    wall/1e6,metrics.totalNanos()/1e6/metrics.writes(),metrics.maximumNanos()/1e6);
        }finally{start.countDown();pool.shutdownNow();}
    }
    private static double percentile(long[] sorted,int percent){
        return sorted[(int)Math.ceil(sorted.length*percent/100.0)-1]/1e6;
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
        invalidate(restarted,next);
        assertEquals(0,restarted.debit(next,generation,"after-restart",1).absorbed());
        assertEquals(0,restarted.state(next).remaining());
    }
    @Test void tornInactiveCheckpointCannotRestoreAbsorbedDamage() throws Exception {
        var a=active();a.ledger.debit(a.lease,a.generation,"durable",20);
        Files.writeString(root.resolve(a.player+".journal.alt"),"{\"checkpoint\":",StandardCharsets.UTF_8);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertEquals(130,restarted.state(lease).remaining());
    }
    @Test void restartWithUncertainNativeManaDepletesInsteadOfChargingAgain() {
        var a=active();a.ledger.debit(a.lease,a.generation,"prior-hit",25);
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertTrue(restarted.paymentRecoveryRequired(lease));
        invalidate(restarted,lease);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.state(lease).phase());
        assertEquals(0,restarted.debit(lease,a.generation,"new-hit",20).absorbed());
        var again=new CycloneBarrierLedger(root);var recovered=again.attach(a.player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,again.state(recovered).phase());
    }
    @Test void nativePaidGenerationReconcilesOnlyRemainingProtectionAfterRestart(){
        var a=active();
        assertEquals(35,a.ledger.debit(a.lease,a.generation,"first-hit",35).absorbed());
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        assertTrue(restarted.paymentRecoveryRequired(lease));
        assertEquals(0,restarted.debit(lease,a.generation,"held-hit",10).absorbed());
        assertThrows(IllegalStateException.class,()->restarted.reconcileNativePayment(lease,UUID.randomUUID()));
        assertTrue(restarted.paymentRecoveryRequired(lease));
        var recovered=restarted.reconcileNativePayment(lease,a.generation);
        assertEquals(115,recovered.remaining());
        assertFalse(restarted.paymentRecoveryRequired(lease));
        assertEquals(0,restarted.debit(lease,a.generation,"first-hit",35).absorbed());
        assertEquals(40,restarted.debit(lease,a.generation,"second-hit",40).absorbed());
        assertEquals(75,restarted.state(lease).remaining());
        assertThrows(IllegalStateException.class,()->restarted.reconcileNativePayment(a.lease,a.generation));
    }
    @Test void nativeManaRollbackAndPersistedManaBothForfeitWithoutAnotherCharge() {
        for(double nativeMana:List.of(200.0,160.0)){
            var a=active();var restarted=new CycloneBarrierLedger(root);
            restarted.preload(a.player);var lease=restarted.attachPrepared(a.player);
            assertEquals(CycloneBarrierLedger.Phase.ACTIVE,restarted.state(lease).phase());
            assertEquals(0,restarted.debit(lease,a.generation,"before-recovery",10).absorbed());
            class Mana implements NativeResourcePort {
                double current=nativeMana;
                public double current(ResourceType type){return current;}
                public double maximum(ResourceType type){return 200;}
                public void setCurrent(ResourceType type,double value){current=value;}
            }
            var actual=new Mana(); // Native authority may have saved before or after the first debit.
            assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.invalidateUncertainPayment(lease).phase());
            assertEquals(nativeMana,actual.current(ResourceType.MANA)); // Recovery has no resource port.
            assertEquals(0,restarted.debit(lease,a.generation,"after-recovery",10).absorbed());
            assertEquals(CycloneBarrierLedger.Phase.DEPLETED,restarted.invalidateUncertainPayment(lease).phase());
            assertEquals(5,restarted.state(lease).revision());
            assertTrue(restarted.activationSeen(lease,a.activation));
            assertEquals(CycloneActivationTransaction.Result.DUPLICATE_INACTIVE,
                    new CycloneActivationTransaction(restarted).begin(lease,a.activation,UUID.randomUUID(),100,
                            ()->true,()->true,()->true));
        }
    }
    @Test void recoveredCooldownDebtSurvivesUncertainBarrierInvalidation() {
        var a=active();
        var saved=new java.util.HashMap<UUID,java.util.Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown>>();
        var persistence=new RpgCooldownService.Persistence(){
            public java.util.Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> load(UUID actor){
                return saved.getOrDefault(actor,java.util.Map.of());
            }
            public void save(UUID actor,java.util.Map<String,com.inigmasgames.hytalerpg.combat.cooldown.SavedCooldown> values){
                saved.put(actor,java.util.Map.copyOf(values));
            }
        };
        var before=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->0L);before.bindPersistence(persistence);
        var spend=before.spendCharge(a.player,"spirit_shield",1,20,1,0,null);
        assertFalse(before.canActivate(a.player,"spirit_shield",1));
        var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        var after=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->0L);after.bindPersistence(persistence);
        after.restore(a.player);
        assertFalse(after.canActivate(a.player,"spirit_shield",1));
        invalidate(restarted,lease);
        assertFalse(after.canActivate(a.player,"spirit_shield",1));
        assertTrue(saved.get(a.player).containsKey("spirit_shield"));
        assertNotNull(spend.token());
    }
    @Test void uncertainRecoveryWriteFailureNeverPublishesProtection() throws Exception {
        var a=active();var restarted=new CycloneBarrierLedger(root);var lease=restarted.attach(a.player);
        Path journal=root.resolve(a.player+".journal");Path saved=root.resolve("saved-"+a.player);
        Files.move(journal,saved);Files.createDirectory(journal);
        try{
            assertThrows(IllegalStateException.class,()->restarted.invalidateUncertainPayment(lease));
            assertThrows(IllegalStateException.class,()->restarted.state(lease));
            assertThrows(IllegalStateException.class,()->restarted.debit(lease,a.generation,"blocked",20));
        }finally{Files.delete(journal);Files.move(saved,journal);}
        var next=new CycloneBarrierLedger(root);var recovered=next.attach(a.player);
        assertEquals(0,next.debit(recovered,a.generation,"still-quarantined",20).absorbed());
        invalidate(next,recovered);
    }
    @Test void delayedOldSessionCannotInvalidateNewBarrierOrSession() {
        var a=active();a.ledger.detach(a.lease);a.ledger.preload(a.player);
        var next=a.ledger.attachPrepared(a.player);
        assertThrows(IllegalStateException.class,()->a.ledger.invalidateUncertainPayment(a.lease));
        invalidate(a.ledger,next);
        var newActivation=UUID.randomUUID();var tx=new CycloneActivationTransaction(a.ledger);
        tx.begin(next,newActivation,UUID.randomUUID(),100,()->true,()->true,()->true);
        tx.manaCommitted(next,newActivation,"new-payment",40);
        tx.cooldownCommitted(next,newActivation,"new-cooldown");tx.publish(next,newActivation);
        a.ledger.detach(a.lease);
        assertEquals(CycloneBarrierLedger.Phase.ACTIVE,a.ledger.state(next).phase());
        assertEquals(10,a.ledger.debit(next,a.ledger.state(next).generation(),"new-hit",10).absorbed());
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
