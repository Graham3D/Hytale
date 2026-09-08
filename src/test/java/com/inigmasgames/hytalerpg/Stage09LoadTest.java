package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.execution.support.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Measured backend work only: excludes native ECS/stat/AI/damage, network, file persistence and rendering. */
class Stage09LoadTest {
    @Test void paidThornsCanRunFiveMinutesWithoutLifetimeCapOrAccumulatedSecondaryBudget(){
        var h=new Stage09AuraRuntimeTest.Harness("thorns_aura");h.cast();long start=System.nanoTime();int grants=0;
        for(int i=1;i<=3000;i++){
            h.mana=100;h.tick(i*.1); // External fixture supply; runtime still pays every native-port-shaped upkeep slice.
            if(i%10==0){var e=h.runtime.thorns(h.world,h.actor,h.now).orElseThrow();for(int j=0;j<100;j++)if(h.runtime.claimSupportSecondary(e,h.now)==1)grants++;}
        }
        assertEquals(2400,grants);assertEquals(1,h.runtime.auraCount());assertEquals(1,h.budget.size());
        double paid=h.slices.stream().mapToDouble(Double::doubleValue).sum();assertEquals(300.25,paid,1e-7);
        h.runtime.detach(h.actor,"LOAD_END",h);assertEquals(0,h.runtime.auraCount());assertEquals(0,h.budget.size());
        System.out.printf(Locale.ROOT,"STAGE09_LOAD thornsSeconds=300 ownerTicks=3000 attemptedSecondary=30000 grantedSecondary=%d paidSeconds=%.2f elapsedMs=%.4f finalAuras=0 finalFields=0 nativeExecution=false%n",grants,paid,(System.nanoTime()-start)/1e6);
    }
    @Test void maximumFiniteEffectLoadIsBoundedAndExpiryReleasesEverything(){
        var registry=new FiniteSupportEffects();var contexts=new ArrayList<com.inigmasgames.hytalerpg.execution.SkillExecutionContext>();
        for(int owner=0;owner<16;owner++){var h=new Stage09SupportRuntimeTest.Harness("minor_heal");h.link("overflow",PassiveSlot.PASSIVE01);h.cast();contexts.add(h.context);}
        long start=System.nanoTime();
        for(var c:contexts)for(int target=0;target<256;target++)registry.healingResolved(c,UUID.randomUUID(),30,100,100,100,0);
        assertEquals(4096,registry.size());int applications=0;
        for(var c:contexts){ // Existing recipient views are bounded; repeated refresh must never add a pool.
            var victim=UUID.randomUUID();assertThrows(IllegalStateException.class,()->registry.healingResolved(c,victim,20,100,100,100,0));applications++;
        }
        registry.expire(6);assertEquals(0,registry.size());assertEquals(0,registry.secondaryRootCount());
        System.out.printf(Locale.ROOT,"STAGE09_LOAD overflowEffects=4096 owners=16 rejectedOverflow=%d elapsedMs=%.4f finalEffects=0 finalSecondaryRoots=0 nativeExecution=false%n",applications,(System.nanoTime()-start)/1e6);
    }
    @Test void auraPulseAndUpkeepDoNotDependOnOwnerTickCadence(){
        for(double step:new double[]{.025,.05,.1,.125,.2}){
            var h=new Stage09AuraRuntimeTest.Harness("reaping_storm");h.cast();for(double now=step;now<8;now+=step)h.tick(now);h.tick(8);
            assertEquals(8,h.damagePulses.size());assertEquals(8,h.slices.stream().mapToDouble(Double::doubleValue).sum(),1e-7);assertEquals(28,h.mana,1e-7);
        }
    }
}
