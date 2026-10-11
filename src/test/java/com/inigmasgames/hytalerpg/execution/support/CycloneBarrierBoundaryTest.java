package com.inigmasgames.hytalerpg.execution.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CycloneBarrierBoundaryTest {
    @TempDir Path root;

    private record Active(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease,
                          UUID player,UUID generation,UUID activation) {}

    private Active active(Path directory,UUID player,int maxRecords) {
        var ledger=new CycloneBarrierLedger(directory,4096,maxRecords);
        var lease=ledger.attach(player);var generation=UUID.randomUUID();var activation=UUID.randomUUID();
        var tx=new CycloneActivationTransaction(ledger);
        tx.begin(lease,activation,generation,20,()->true,()->true,()->true);
        tx.manaCommitted(lease,activation,"mana",4);
        tx.cooldownCommitted(lease,activation,"cooldown");tx.publish(lease,activation);
        return new Active(ledger,lease,player,generation,activation);
    }

    @Test void receiptCeilingForfeitsPoolWithoutUnreceiptedAbsorptionAndActivationCeilingRejects() {
        var a=active(root,UUID.randomUUID(),2);
        assertEquals(1,a.ledger.debit(a.lease,a.generation,"hit-1",1).absorbed());
        assertEquals(1,a.ledger.debit(a.lease,a.generation,"hit-2",1).absorbed());
        var limit=a.ledger.debit(a.lease,a.generation,"hit-3",1);
        assertEquals(0,limit.absorbed());assertEquals(1,limit.damageRemaining());
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,a.ledger.state(a.lease).phase());
        var replay=new CycloneBarrierLedger(root,4096,2);var lease=replay.attach(a.player);
        assertEquals(CycloneBarrierLedger.Phase.DEPLETED,replay.state(lease).phase());
        assertEquals(CycloneActivationTransaction.Result.DUPLICATE_INACTIVE,
                new CycloneActivationTransaction(replay).begin(lease,a.activation,UUID.randomUUID(),20,
                        ()->true,()->true,()->true));
        var second=UUID.randomUUID();var tx=new CycloneActivationTransaction(replay);
        tx.begin(lease,second,UUID.randomUUID(),20,()->true,()->true,()->true);
        replay.abort(lease,second,false);
        long revision=replay.state(lease).revision();
        var rejection=assertThrows(IllegalStateException.class,()->
                tx.begin(lease,UUID.randomUUID(),UUID.randomUUID(),20,()->true,()->true,()->true));
        assertEquals("CYCLONE_ACTIVATION_BUDGET",rejection.getMessage());
        assertEquals(revision,replay.state(lease).revision());
    }

    @Test void conflictingEqualRevisionSegmentsFailClosed() throws Exception {
        var player=UUID.randomUUID();var first=active(root.resolve("first"),player,10);
        var second=active(root.resolve("second"),player,10);
        assertEquals(first.ledger.state(first.lease).revision(),second.ledger.state(second.lease).revision());
        Files.copy(root.resolve("second").resolve(player+".journal"),
                root.resolve("first").resolve(player+".journal.alt"));
        assertThrows(IllegalStateException.class,()->
                new CycloneBarrierLedger(root.resolve("first"),4096,10).attach(player));
    }

    @Test void sessionAndPreloadBudgetsRejectExcessWithoutEviction() {
        var ledger=new CycloneBarrierLedger(root);
        var first=UUID.randomUUID();var lease=ledger.attach(first);
        for(int i=1;i<CycloneBarrierLedger.MAX_SESSIONS;i++)ledger.attach(UUID.randomUUID());
        assertEquals("CYCLONE_SESSION_BUDGET",assertThrows(IllegalStateException.class,
                ()->ledger.attach(UUID.randomUUID())).getMessage());
        assertEquals(CycloneBarrierLedger.Phase.NONE,ledger.state(lease).phase());
        var preload=new CycloneBarrierLedger(root.resolve("preloads"));
        for(int i=0;i<256;i++)preload.preload(UUID.randomUUID());
        assertEquals("CYCLONE_PRELOAD_BUDGET",assertThrows(IllegalStateException.class,
                ()->preload.preload(UUID.randomUUID())).getMessage());
    }
}
