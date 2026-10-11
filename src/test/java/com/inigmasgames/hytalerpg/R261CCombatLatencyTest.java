package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline native-float bridge timing; excludes ECS scheduling and connected rendering. */
class R261CCombatLatencyTest {
    @TempDir Path root;
    private final RpgResourceService resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());
    private static final class Port implements NativeResourcePort {
        double mana;
        Port(double mana){this.mana=mana;}
        public double current(ResourceType type){return mana;}
        public double maximum(ResourceType type){return 1000;}
        public void setCurrent(ResourceType type,double value){mana=value;}
    }
    private record Actor(UUID id,CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease,UUID generation,Port port) {}
    private Actor actor(String label,int index,double capacity,double mana){
        UUID id=UUID.randomUUID(),generation=UUID.randomUUID();
        if(capacity<=0)return new Actor(id,null,null,null,new Port(mana));
        var ledger=new CycloneBarrierLedger(root.resolve(label+"-"+index));
        var lease=ledger.attach(id);var activation=UUID.randomUUID();var tx=new CycloneActivationTransaction(ledger);
        tx.begin(lease,activation,generation,capacity,()->true,()->true,()->true);
        tx.manaCommitted(lease,activation,"mana-"+index,100);
        tx.cooldownCommitted(lease,activation,"cooldown-"+index);
        tx.publish(lease,activation);
        return new Actor(id,ledger,lease,generation,new Port(mana));
    }
    private void hit(Actor actor,String receipt,float incoming,double fraction){
        float remaining=incoming;
        if(actor.ledger()!=null&&actor.ledger().state(actor.lease()).phase()==CycloneBarrierLedger.Phase.ACTIVE)
            remaining=CycloneDamageStage.absorb(actor.ledger(),actor.lease(),actor.generation(),receipt,incoming).nativeRemainder();
        var result=ManaBeforeLifeStage.apply(remaining,fraction,actor.id(),resources,actor.port());
        assertEquals(incoming,(incoming-remaining)+result.manaSpent()+result.healthDamage(),1e-3);
    }
    private static double percentile(long[] sorted,int percent){return sorted[(int)Math.ceil(sorted.length*percent/100.0)-1]/1e6;}
    private static void report(String scenario,long[] samples){
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT,"R261C_BRIDGE_PROFILE scenario=%s hits=%d p50Ms=%.3f p95Ms=%.3f p99Ms=%.3f worstMs=%.3f%n",
                scenario,samples.length,percentile(samples,50),percentile(samples,95),percentile(samples,99),samples[samples.length-1]/1e6);
    }
    @Test void sequentialDamagePathsRecordTailLatency(){
        for(String scenario:List.of("barrier","mana","combined","overflow")){
            var samples=new long[80];Actor shared=null;
            if(!scenario.equals("combined"))shared=actor(scenario,0,scenario.equals("barrier")?1000:0,
                    scenario.equals("overflow")?0:1000);
            for(int i=0;i<samples.length;i++){
                var player=scenario.equals("combined")?actor(scenario,i,1,1000):shared;
                float incoming=scenario.equals("combined")?2:1;
                long start=System.nanoTime();hit(player,scenario+"-"+i,incoming,.5);
                samples[i]=System.nanoTime()-start;
            }
            report(scenario,samples);
        }
    }
    @Test void concurrentSameAndIndependentPlayersRecordContention() throws Exception {
        for(boolean separate:List.of(false,true)){
            String scenario=separate?"eight-players":"one-player";
            var actors=new ArrayList<Actor>();
            // Keep a substantial barrier phase, then force each actor through
            // Mana Before Life. Both stages must appear in the contention sample.
            for(int i=0;i<(separate?8:1);i++)actors.add(actor(scenario,i,separate?20:160,1000));
            var pool=Executors.newFixedThreadPool(8);
            var ready=new CountDownLatch(8);
            var start=new CountDownLatch(1);
            try{
                var jobs=new ArrayList<Future<long[]>>();
                for(int t=0;t<8;t++){
                    int thread=t;var player=actors.get(separate?t:0);
                    jobs.add(pool.submit(()->{var sample=new long[30];ready.countDown();start.await();
                        for(int i=0;i<sample.length;i++){
                            long before=System.nanoTime();hit(player,scenario+"-"+thread+"-"+i,2,.5);
                            sample[i]=System.nanoTime()-before;
                        }return sample;}));
                }
                assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
                var all=new long[240];int offset=0;
                for(var job:jobs){var sample=job.get(45,TimeUnit.SECONDS);System.arraycopy(sample,0,all,offset,sample.length);offset+=sample.length;}
                report(scenario,all);
            }finally{start.countDown();pool.shutdownNow();}
        }
    }
}
