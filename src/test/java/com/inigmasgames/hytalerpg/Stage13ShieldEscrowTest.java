package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.execution.support.ShieldEscrow;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13ShieldEscrowTest {
    private SupportProgress full(){return new SupportProgress(0,1,new ManaguardLedger(0,100,50,0),Map.of());}
    private void grant(ShieldEscrow escrow,SupportProgress actual){
        var debit=escrow.prepare(actual);escrow.submitted(debit,CompletableFuture.completedStage(debit.persisted().nextRevision()));assertNotNull(escrow.poll());
    }
    @Test void unfinishedSaveGrantsNoAbsorptionAndHitDoesNotWait(){
        var escrow=new ShieldEscrow();var state=full();var debit=escrow.prepare(state);var held=new CompletableFuture<SupportProgress>();escrow.submitted(debit,held);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{
            assertNull(escrow.poll());assertEquals(15,escrow.absorb(state.managuard(),15,100,false).damageRemaining());
        });
        assertFalse(held.isDone());held.complete(debit.persisted().nextRevision());assertNotNull(escrow.poll());
        assertEquals(0,escrow.absorb(state.managuard(),15,100,false).damageRemaining());
    }
    @Test void restartCannotRecoverUnusedOwnerOrSharedAuthorization(){
        var escrow=new ShieldEscrow();var debit=escrow.prepare(full());escrow.submitted(debit,CompletableFuture.completedStage(debit.persisted()));escrow.poll();
        var recovered=debit.persisted().managuard();assertEquals(0,recovered.current(100));assertEquals(0,recovered.sharedCurrent(100));
        var restart=new ShieldEscrow();grant(restart,debit.persisted());
        assertEquals(25,restart.absorb(recovered,25,100,false).damageRemaining());
        assertEquals(25,restart.absorb(recovered,25,100,true).damageRemaining());
    }
    @Test void lateReceiptCannotReissueCapacitySpentWhilePending(){
        var escrow=new ShieldEscrow();var state=full();grant(escrow,state);
        var debit=escrow.prepare(state);var held=new CompletableFuture<SupportProgress>();escrow.submitted(debit,held);
        var own=escrow.absorb(state.managuard(),30,100,false);var shared=escrow.absorb(own.ledger(),20,100,true);
        held.complete(debit.persisted().nextRevision());escrow.poll();
        assertEquals(70,escrow.available(shared.ledger(),100,false));assertEquals(15,escrow.available(shared.ledger(),100,true));
        assertNull(escrow.poll());assertEquals(70,escrow.available(shared.ledger(),100,false));
    }
    @Test void disconnectedGenerationCannotPublishLateGrant(){
        var escrow=new ShieldEscrow();var debit=escrow.prepare(full());var held=new CompletableFuture<SupportProgress>();escrow.submitted(debit,held);
        escrow.revoke();held.complete(debit.persisted());assertNotNull(escrow.poll());assertEquals(0,escrow.available(full().managuard(),100,false));
    }
    @Test void failedReceiptRevokesAuthorityAndCannotBeRetried(){
        var escrow=new ShieldEscrow();grant(escrow,full());var debit=escrow.prepare(full());escrow.submitted(debit,CompletableFuture.failedStage(new IllegalStateException("held force failed")));
        assertThrows(RuntimeException.class,escrow::poll);assertTrue(escrow.uncertain());assertThrows(IllegalStateException.class,()->escrow.prepare(full()));
    }
    @Test void rechargeMustBeAuthorizedBeforeUseAndCannotIncreaseCapacityOnItsOwn(){
        var escrow=new ShieldEscrow();var state=full();grant(escrow,state);
        var spent=escrow.absorb(state.managuard(),100,100,false).ledger();var recharged=spent.recharge(1);
        assertEquals(0,escrow.available(recharged,100,false));grant(escrow,state.guard(recharged));
        assertEquals(10,escrow.available(recharged,100,false));assertEquals(10,escrow.available(recharged,200,false));
    }
}
