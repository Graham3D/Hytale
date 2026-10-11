package com.inigmasgames.hytalerpg.execution.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CycloneDamageStageTest {
    @TempDir Path root;
    private record Active(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease,UUID generation){}
    private Active active(CycloneBarrierLedger ledger){
        var player=UUID.randomUUID();var lease=ledger.attach(player);
        var generation=UUID.randomUUID();var activation=UUID.randomUUID();
        var tx=new CycloneActivationTransaction(ledger);
        tx.begin(lease,activation,generation,500,()->true,()->true,()->true);
        tx.manaCommitted(lease,activation,"mana",100);
        tx.cooldownCommitted(lease,activation,"cooldown");
        tx.publish(lease,activation);
        return new Active(ledger,lease,generation);
    }
    @Test void nativeBoundaryUsesOnlyConfirmedAbsorptionAndPreservesOverflow(){
        var a=active(new CycloneBarrierLedger(root));
        var full=CycloneDamageStage.absorb(a.ledger,a.lease,a.generation,"full",40f);
        assertEquals(40,full.debit().absorbed());assertEquals(0,full.nativeRemainder());
        var partial=CycloneDamageStage.absorb(a.ledger,a.lease,a.generation,"partial",470f);
        assertEquals(460,partial.debit().absorbed());assertEquals(10,partial.nativeRemainder());
        var duplicate=CycloneDamageStage.absorb(a.ledger,a.lease,a.generation,"partial",470f);
        assertEquals(0,duplicate.debit().absorbed());assertEquals(470,duplicate.nativeRemainder());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,a.ledger.state(a.lease).phase());
    }
    @Test void exactProductionDebitBridgeLatencyAndContention() throws Exception {
        profile("sequential-one-player",1,240,false);
        profile("concurrent-one-player",8,30,false);
        profile("concurrent-eight-players",8,30,true);
    }
    private void profile(String label,int threads,int hitsPerThread,boolean separatePlayers) throws Exception {
        var ledger=new CycloneBarrierLedger(root.resolve(label));
        var players=new ArrayList<Active>();
        for(int i=0;i<(separatePlayers?threads:1);i++)players.add(active(ledger));
        var pool=Executors.newFixedThreadPool(threads);
        var start=new CountDownLatch(1);var ready=new CountDownLatch(threads);
        try{
            var jobs=new ArrayList<java.util.concurrent.Future<long[]>>();
            for(int t=0;t<threads;t++){
                final int task=t;var target=players.get(separatePlayers?t:0);
                jobs.add(pool.submit(()->{
                    var samples=new long[hitsPerThread];ready.countDown();start.await();
                    for(int i=0;i<hitsPerThread;i++){
                        long before=System.nanoTime();
                        var result=CycloneDamageStage.absorb(ledger,target.lease,target.generation,
                                label+"-"+task+"-"+i,1f);
                        samples[i]=System.nanoTime()-before;
                        if(result.debit().absorbed()!=1||result.nativeRemainder()!=0)
                            throw new AssertionError("UNCONFIRMED_BRIDGE_ABSORPTION");
                    }
                    return samples;
                }));
            }
            assertTrue(ready.await(10,TimeUnit.SECONDS));long wallStart=System.nanoTime();start.countDown();
            var samples=new long[threads*hitsPerThread];int offset=0;
            for(var job:jobs){var each=job.get(45,TimeUnit.SECONDS);
                System.arraycopy(each,0,samples,offset,each.length);offset+=each.length;}
            Arrays.sort(samples);
            assertEquals(0,ledger.metrics().failedWrites());
            for(var target:players)assertEquals(500-(separatePlayers?hitsPerThread:samples.length),
                    ledger.state(target.lease).remaining());
            System.out.printf("CYCLONE_DAMAGE_BRIDGE scenario=%s hits=%d threads=%d p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f wallMs=%.3f forcedWriteMaxMs=%.3f%n",
                    label,samples.length,threads,percentile(samples,50),percentile(samples,95),
                    percentile(samples,99),samples[samples.length-1]/1e6,
                    (System.nanoTime()-wallStart)/1e6,ledger.metrics().maximumNanos()/1e6);
        }finally{start.countDown();pool.shutdownNow();}
    }
    private static double percentile(long[] sorted,int percent){
        return sorted[(int)Math.ceil(sorted.length*percent/100.0)-1]/1e6;
    }
}
