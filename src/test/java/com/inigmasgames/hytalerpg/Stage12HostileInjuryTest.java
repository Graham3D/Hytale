package com.inigmasgames.hytalerpg;
import com.inigmasgames.hytalerpg.progress.HostileInjuryLedger;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Stage12HostileInjuryTest {
    final UUID world=UUID.randomUUID(),player=UUID.randomUUID(),enemy=UUID.randomUUID();
    final HostileInjuryLedger ledger=new HostileInjuryLedger();
    void hit(){ledger.damage(world,player,enemy,100,80,0);}
    @Test void realHostileHealthRecoveredOnlyOnce(){hit();assertEquals(Map.of(enemy,10d),ledger.healed(world,player,80,90,1));assertEquals(Map.of(enemy,10d),ledger.healed(world,player,90,100,2));assertTrue(ledger.healed(world,player,80,100,3).isEmpty());}
    @Test void noOverhealOrUnknownEnemyCredit(){assertTrue(ledger.healed(world,player,50,100,0).isEmpty());ledger.damage(world,player,null,100,80,0);assertTrue(ledger.healed(world,player,80,100,1).isEmpty());}
    @Test void resourceHealthSpendInvalidatesOldInjuries(){hit();ledger.invalidate(world,player);assertTrue(ledger.healed(world,player,70,100,1).isEmpty());}
    @Test void nativeRegenerationConsumesInjuryBeforeLaterHealthCost(){hit();ledger.observedHealth(world,player,100,1);ledger.invalidate(world,player);assertTrue(ledger.healed(world,player,80,100,2).isEmpty());}
    @Test void nativeRegenerationIsNotCountedAgainAsHealing(){hit();ledger.observedHealth(world,player,90,1);assertEquals(Map.of(enemy,10d),ledger.healed(world,player,90,100,2));}
    @Test void unobservedDownwardChangeClearsUnknownProvenance(){hit();assertTrue(ledger.healed(world,player,70,100,1).isEmpty());}
    @Test void friendlySparringDoesNotAddEligibleHealth(){hit();ledger.damage(world,player,null,80,70,1);assertTrue(ledger.healed(world,player,70,80,2).isEmpty());assertEquals(Map.of(enemy,20d),ledger.healed(world,player,80,100,3));}
    @Test void exactEnemyIntervalsAreKeptSeparate(){hit();var second=UUID.randomUUID();ledger.damage(world,player,second,80,60,1);assertEquals(Map.of(second,20d,enemy,5d),ledger.healed(world,player,60,85,2));}
    @Test void expiryReversedTimeAndWrongWorldCannotCredit(){hit();assertTrue(ledger.healed(world,player,80,100,20001).isEmpty());hit();assertTrue(ledger.healed(world,player,80,100,-1).isEmpty());hit();assertTrue(ledger.healed(UUID.randomUUID(),player,80,100,1).isEmpty());}
    @Test void logoutAndRestartDiscardUnprovenOutstandingInjury(){hit();ledger.forget(player);assertEquals(0,ledger.size());assertTrue(ledger.healed(world,player,80,100,1).isEmpty());assertTrue(new HostileInjuryLedger().healed(world,player,80,100,1).isEmpty());}
    @Test void invalidAndCancelledShapedHealthCannotCreateInjury(){for(double after:new double[]{100,101,Double.NaN,Double.NEGATIVE_INFINITY})ledger.damage(world,player,enemy,100,after,0);assertEquals(0,ledger.size());}
    @Test void recipientMemoryHasAHardBound(){for(int i=0;i<HostileInjuryLedger.MAX_RECIPIENTS+1;i++)ledger.damage(world,UUID.randomUUID(),enemy,100,90,0);assertEquals(HostileInjuryLedger.MAX_RECIPIENTS,ledger.size());}
}
