package com.inigmasgames.hytalerpg.execution.support;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CycloneReflectionJournalTest {
    @TempDir Path directory;

    private static CycloneBarrierLedger.State state(UUID player,UUID generation,UUID activation,double capacity){
        return new CycloneBarrierLedger.State(CycloneBarrierLedger.SCHEMA,player,"spirit_shield",generation,
                activation,4,CycloneBarrierLedger.Phase.ACTIVE,capacity,capacity-25,"paid","cooldown",100);
    }
    @Test void frozenBehaviorAndClaimBudgetSurviveRestart() {
        UUID player=UUID.randomUUID(),generation=UUID.randomUUID(),activation=UUID.randomUUID();
        var frozen=new CycloneReflectionJournal.Behavior(1,player,generation,activation,"root","instance","correlation",.2,80);
        var journal=new CycloneReflectionJournal(directory);
        journal.install(frozen);
        assertEquals(5,frozen.coefficient()*25,1e-9);
        assertTrue(journal.claim(frozen,"hit-0"));
        assertFalse(journal.claim(frozen,"hit-0"));
        var resumed=new CycloneReflectionJournal(directory);resumed.preload(player);
        assertEquals(frozen,resumed.load(state(player,generation,activation,80)));
        assertNull(resumed.load(state(player,UUID.randomUUID(),activation,80)));
        assertNull(resumed.load(state(player,generation,activation,100)));
        assertFalse(resumed.claim(frozen,"hit-0"));
        for(int n=1;n<16;n++)assertTrue(resumed.claim(frozen,"hit-"+n));
        assertFalse(resumed.claim(frozen,"hit-16"));
        var again=new CycloneReflectionJournal(directory);again.preload(player);
        assertFalse(again.claim(frozen,"hit-16"));
    }
    @Test void missingOrDamagedRecordCannotAuthorizeRecoveryOrAnotherReflection() throws Exception {
        UUID player=UUID.randomUUID(),generation=UUID.randomUUID(),activation=UUID.randomUUID();
        var parent=state(player,generation,activation,80);
        var journal=new CycloneReflectionJournal(directory);journal.preload(player);
        assertNull(journal.load(parent));
        var frozen=new CycloneReflectionJournal.Behavior(1,player,generation,activation,"root","instance","corr",.2,80);
        journal.install(frozen);assertTrue(journal.claim(frozen,"first"));
        Path file=directory.resolve("reflective-ward").resolve(player+".journal");
        Files.writeString(file,"torn",java.nio.file.StandardOpenOption.APPEND);
        var resumed=new CycloneReflectionJournal(directory);resumed.preload(player);
        assertNull(resumed.load(parent));
        assertFalse(resumed.claim(frozen,"first"));
        assertFalse(resumed.claim(frozen,"second"));
    }
    @Test void newGenerationReplacesOldBehaviorWithoutRevivingOldReceipts(){
        UUID player=UUID.randomUUID(),activation=UUID.randomUUID();
        var first=new CycloneReflectionJournal.Behavior(1,player,UUID.randomUUID(),activation,"old","old-instance","old-corr",.2,80);
        var next=new CycloneReflectionJournal.Behavior(1,player,UUID.randomUUID(),UUID.randomUUID(),"new","new-instance","new-corr",.2,100);
        var journal=new CycloneReflectionJournal(directory);
        journal.install(first);assertTrue(journal.claim(first,"hit"));journal.install(next);
        assertFalse(journal.claim(first,"later"));
        var resumed=new CycloneReflectionJournal(directory);resumed.preload(player);
        assertNull(resumed.load(state(player,first.generation(),first.activation(),80)));
        assertEquals(next,resumed.load(state(player,next.generation(),next.activation(),100)));
        assertTrue(resumed.claim(next,"hit"));
    }
}
