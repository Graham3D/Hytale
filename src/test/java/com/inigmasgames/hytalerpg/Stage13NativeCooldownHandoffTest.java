package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.cooldown.*;
import com.inigmasgames.hytalerpg.execution.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13NativeCooldownHandoffTest {
    static final class Disk implements RpgCooldownService.AsyncPersistence {
        Map<String,SavedCooldown> saved=Map.of();final ArrayDeque<Map<String,SavedCooldown>> values=new ArrayDeque<>();
        final ArrayDeque<CompletableFuture<Void>> receipts=new ArrayDeque<>();boolean reject;int writes;
        public Map<String,SavedCooldown> load(UUID actor){return saved;}
        public void save(UUID actor,Map<String,SavedCooldown> value){throw new AssertionError("SYNCHRONOUS_NATIVE_COOLDOWN_SAVE");}
        public CompletionStage<Void> submit(UUID actor,Map<String,SavedCooldown> value){
            if(reject)throw new IllegalStateException("test admission full");writes++;values.add(Map.copyOf(value));var receipt=new CompletableFuture<Void>();receipts.add(receipt);return receipt;
        }
        void durable(){saved=values.remove();receipts.remove().complete(null);}
    }
    private Stage09SupportRuntimeTest.Harness healer(Disk disk){var h=new Stage09SupportRuntimeTest.Harness("minor_heal");h.kernel.cooldowns().bindPersistence(disk);return h;}
    @Test void productionCastWaitsForDurableDebtWithoutWaitingOnOwner(){
        var disk=new Disk();var h=healer(disk);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()->{
            assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertEquals(100,h.mana);assertEquals(40,h.health);
            assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertEquals(1,disk.writes);
            assertEquals(SkillExecutionResult.Status.PENDING,h.execution.completePersistence(h.actor,h).status());
        });
        disk.durable();assertTrue(h.execution.completePersistence(h.actor,h).committed());
        assertTrue(h.mana<100);assertTrue(h.health>40);assertEquals(1,disk.writes);assertNull(h.execution.completePersistence(h.actor,h));
    }
    @Test void cancellationAndDisconnectRetainDebtUntilRefundAndEvictionAreDurable(){
        var disk=new Disk();var h=healer(disk);h.cast();h.execution.cancel(h.actor,"disconnect");h.kernel.cooldowns().detach(h.actor);
        assertFalse(h.kernel.cooldowns().persistenceReady(h.actor));assertEquals(100,h.mana);assertEquals(40,h.health);
        disk.durable();disk.reject=true;h.execution.pollCancelledPersistence();assertTrue(h.execution.pendingCast(h.actor));
        disk.reject=false;h.execution.pollCancelledPersistence();assertFalse(h.execution.pendingCast(h.actor));
        assertFalse(h.kernel.cooldowns().persistenceReady(h.actor));assertEquals(1,disk.receipts.size());
        disk.durable();h.kernel.cooldowns().pollMaintenance();assertFalse(h.kernel.cooldowns().persistenceReady(h.actor));
        disk.durable();h.kernel.cooldowns().pollMaintenance();assertTrue(h.kernel.cooldowns().persistenceReady(h.actor));assertTrue(disk.saved.isEmpty());
        assertEquals(100,h.mana);assertEquals(40,h.health);
    }
    @Test void rejectedSecondSubmissionRetainsFirstAuraAuthorityAndNeverDispatches(){
        var support=new Stage13NativeSupportHandoffTest.Store();var h=new Stage13NativeSupportHandoffTest.Harness(support);
        var cooldown=new Disk();cooldown.reject=true;h.kernel.cooldowns().bindPersistence(cooldown);
        assertEquals(SkillExecutionResult.Status.PENDING,h.cast().status());assertFalse(support.receipt.isDone());
        support.durable();assertFalse(h.execution.completePersistence(h.actor,h).committed());
        assertEquals(0,h.runtime.auraCount());assertEquals(100,h.mana);assertEquals(20,h.hit(20));assertFalse(h.execution.pendingCast(h.actor));
    }
    @Test void staleOwnerValidationCannotExecuteAfterHeldDebtCompletes(){
        var disk=new Disk();var h=new Stage09SupportRuntimeTest.Harness("minor_heal"){
            @Override public SkillExecutionPort.Validation validateDurableCompletion(SkillExecutionContext context){return SkillExecutionPort.Validation.reject("NEW_WORLD_GENERATION");}
        };
        h.kernel.cooldowns().bindPersistence(disk);h.cast();disk.durable();disk.reject=true;
        assertFalse(h.execution.completePersistence(h.actor,h).committed());assertTrue(h.execution.pendingCast(h.actor));
        assertEquals(100,h.mana);assertEquals(40,h.health);
        disk.reject=false;h.execution.pollCancelledPersistence();disk.durable();h.kernel.cooldowns().pollMaintenance();
        assertTrue(disk.saved.isEmpty());assertFalse(h.execution.pendingCast(h.actor));
    }
}
