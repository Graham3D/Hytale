package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13NativeSupportHandoffTest {
    static final class Store implements SupportProgressStore.Async {
        SupportProgress saved=SupportProgress.INITIAL,submitted;
        CompletableFuture<SupportProgress> receipt;
        public SupportProgress read(UUID actor){return saved;}
        public SupportProgress save(UUID actor,SupportProgress value){throw new AssertionError("Native caller attempted synchronous player save");}
        public CompletionStage<SupportProgress> submit(UUID actor,SupportProgress value){
            if(receipt!=null&&!receipt.isDone())throw new AssertionError("unbounded support submission");
            assertEquals(saved.revision(),value.revision());submitted=value;receipt=new CompletableFuture<>();return receipt;
        }
        public CompletionStage<SupportProgress> settle(UUID actor,CompletionStage<SupportProgress> prior,SupportProgress actual){
            return prior.thenCompose(saved->submit(actor,new SupportProgress(saved.revision(),actual.lastAuraEpoch(),actual.managuard(),actual.toggleLocks())));
        }
        void durable(){saved=submitted.nextRevision();receipt.complete(saved);}
    }
    static final class Harness extends Stage09SupportRuntimeTest.Harness {
        Harness(Store store){super("managuard",ignored->store);}
        @Override public CompletionStage<Void> prepareDurable(SkillExecutionContext c){return runtime.prepareDurable(c,this);}
        @Override public void abandonDurable(SkillExecutionContext c){runtime.abandonDurable(c);}
    }
    @Test void productionExecutionStaysPendingUntilShieldDebitIsDurable(){
        var store=new Store();var h=new Harness(store);
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertEquals(100,h.mana);assertEquals(0,h.runtime.auraCount());
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{
            assertEquals(20,h.hit(20));assertEquals(SkillExecutionResult.Status.PENDING,h.execution.completePersistence(h.actor,h).status());
        });
        assertFalse(store.receipt.isDone());assertEquals(50,store.submitted.managuard().deficit());assertEquals(25,store.submitted.managuard().sharedDeficit());
        store.durable();assertTrue(h.execution.completePersistence(h.actor,h).committed());assertEquals(1,h.runtime.auraCount());assertEquals(50,h.mana);
        assertEquals(0,h.hit(20));assertEquals(50,store.saved.managuard().deficit());assertEquals(20,h.runtime.state(h.actor).managuard().deficit());
    }
    @Test void activeNativeHitSpendsPreauthorizedCapacityWhileNextSaveIsHeld(){
        var store=new Store();var h=new Harness(store);h.cast();store.durable();h.execution.completePersistence(h.actor,h);
        h.tick(1);assertFalse(store.receipt.isDone());
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{assertEquals(0,h.hit(20));assertEquals(0,h.hit(20));assertEquals(10,h.hit(20));});
        assertFalse(store.receipt.isDone());store.durable();h.tick(1.1);assertEquals(20,h.hit(20));
        assertEquals(50,h.runtime.state(h.actor).managuard().deficit());assertEquals(50,store.saved.managuard().deficit());
    }
    @Test void failedInitialAuthorizationCannotApplyNativeAuraOrAbsorb(){
        var store=new Store();var h=new Harness(store);h.cast();store.receipt.completeExceptionally(new IllegalStateException("disk force failed"));
        assertFalse(h.execution.completePersistence(h.actor,h).committed());assertEquals(100,h.mana);assertEquals(0,h.runtime.auraCount());assertEquals(20,h.hit(20));
    }
    @Test void sharedAegisSpendsOnlyPreviouslyDurableSeparateEscrowWhileSaveIsHeld(){
        var store=new Store();var h=new Harness(store);var ally=UUID.randomUUID();h.members.add(ally);
        h.link("shared_aegis",com.inigmasgames.hytalerpg.domain.PassiveSlot.PASSIVE01);
        h.cast();assertTrue(h.runtime.sharedGuards(h.world,ally,0).isEmpty());store.durable();h.execution.completePersistence(h.actor,h);
        h.tick(1);var offered=h.runtime.sharedGuards(h.world,ally,1).getFirst();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{
            assertEquals(0,h.runtime.absorbShared(offered,10,1,h).remainder());
            assertEquals(10,h.runtime.absorbShared(offered,25,1,h).remainder());
            assertEquals(0,h.hit(40));
        });
        assertFalse(store.receipt.isDone());assertEquals(25,store.saved.managuard().sharedDeficit());
        store.durable();h.tick(1.1);assertEquals(10,h.runtime.absorbShared(offered,10,1.1,h).remainder());
    }
}
