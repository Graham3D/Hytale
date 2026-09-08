package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage09GuardLedgerTest {
    @TempDir Path temp;
    @Test void baseShieldEqualsActualReservation(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(50,1);
        assertEquals(50,ledger.current(ledger.lastValidatedCapacity()));
    }
    @Test void absorptionConsumesDeficitNotManaAndCapsDamage(){
        var hit=ManaguardLedger.INITIAL.validateCapacity(50,1).absorb(80,50);
        assertEquals(50,hit.absorbed());assertEquals(30,hit.damageRemaining());assertEquals(50,hit.ledger().deficit());
    }
    @Test void lowerAndReRaiseCapacityRetainsAllDeficit(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(50,1).absorb(40,50).ledger();
        ledger=ledger.validateCapacity(10,1);assertEquals(0,ledger.current(10));assertEquals(40,ledger.deficit());
        ledger=ledger.validateCapacity(50,1);assertEquals(10,ledger.current(50));
    }
    @Test void barrierModifiersUseActualReservedCapacity(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(40,.9*1.15);
        assertEquals(41.4,ledger.lastValidatedCapacity(),1e-9);
    }
    @Test void rechargeUsesTenPercentOfLastValidatedCapacityEvenUnequipped(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(50,1).absorb(40,50).ledger().recharge(2);
        assertEquals(30,ledger.deficit());assertEquals(20,ledger.current(50));
    }
    @Test void noEligibleTimeMeansNoRecharge(){
        var ledger=ManaguardLedger.INITIAL.validateCapacity(50,1).absorb(40,50).ledger();
        var state=SupportProgress.INITIAL.guard(ledger).observedTime(6,0);
        assertEquals(40,state.managuard().deficit());
    }
    @Test void invalidEvidenceCannotCreditRecharge(){
        assertThrows(IllegalArgumentException.class,()->SupportProgress.INITIAL.observedTime(1,2));
        assertThrows(IllegalArgumentException.class,()->ManaguardLedger.INITIAL.recharge(Double.NaN));
    }
    @Test void allocationHasExactlyOneToFiftyIntegerPercent(){
        assertEquals(1,ManaguardLedger.INITIAL.allocate(1).allocationPercent());
        assertThrows(IllegalArgumentException.class,()->ManaguardLedger.INITIAL.allocate(0));
        assertThrows(IllegalArgumentException.class,()->ManaguardLedger.INITIAL.allocate(51));
    }
    @Test void savedLedgerDoesNotResumeAuraOrCreditOfflineTime(){
        var player=RpgPlayerState.create(UUID.randomUUID());
        player.support=player.support.guard(ManaguardLedger.INITIAL.validateCapacity(50,1).absorb(30,50).ledger())
                .toggle("managuard",3,true);
        var repo=new FileRpgPlayerStateRepository(temp);repo.save(player);
        var read=repo.load(player.playerUuid()).state();
        assertEquals(player.support,read.support);assertEquals(30,read.support.managuard().deficit());
        assertEquals(3,read.support.toggleLocks().get("managuard"));assertEquals(1,read.support.lastAuraEpoch());
    }
    @Test void toggleLocksNeedObservedTimeAndActivationEpochsIncrease(){
        var state=SupportProgress.INITIAL.toggle("managuard",3,true);
        assertThrows(IllegalStateException.class,()->state.toggle("managuard",3,false));
        var off=state.observedTime(3,0).toggle("managuard",3,false);
        assertEquals(1,off.lastAuraEpoch());
        assertEquals(2,off.observedTime(3,0).toggle("managuard",3,true).lastAuraEpoch());
    }
    @Test void minorHealUsesTwentyWisMasteryAndHealingBucket(){
        assertEquals(20*1.2*1.1*1.15,new HealingCalculationService().direct(20,1,1.2,1.1,.15),1e-9);
    }
}
