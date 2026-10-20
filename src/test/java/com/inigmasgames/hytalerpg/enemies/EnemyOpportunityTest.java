package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.execution.TimedOpportunityLedger;
import com.inigmasgames.hytalerpg.execution.area.RootDisplacementLedger;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyOpportunityTest {
    @Test void failedChanceAndFortuneAvoidanceConsumeTheAttemptButPreflightRejectionsDoNot(){
        var source=UUID.randomUUID();var target=UUID.randomUUID();
        var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->0);
        var claims=new AtomicInteger();var rolls=new AtomicInteger();
        var rollValue=new AtomicReference<Double>(.9);
        var random=new CriticalRoller(()->{rolls.incrementAndGet();return rollValue.get();});
        var app=new StatusApplication(source,target,"WEAKENED",.35,0,true,false,false,false);
        java.util.function.BooleanSupplier claim=()->{claims.incrementAndGet();return true;};
        assertEquals(StatusService.Admission.PROTECTED,status.admit(app,new ControlProfile(true,false,false),true,random,claim));
        status.configureAdmission(id->.5,(id,type)->true,(a,b)->true);
        assertEquals(StatusService.Admission.IMMUNE,status.admit(app,ControlProfile.NORMAL,true,random,claim));
        status.configureAdmission(id->.5,(id,type)->false,(a,b)->true);
        assertEquals(StatusService.Admission.NO_STATUS_MUTATION,status.admit(app,ControlProfile.NORMAL,false,random,claim));
        assertEquals(0,claims.get());assertEquals(0,rolls.get());
        assertEquals(StatusService.Admission.STATUS_CHANCE_FAILED,status.admit(app,ControlProfile.NORMAL,true,random,claim));
        assertEquals(1,claims.get());assertEquals(1,rolls.get());
        status.configureFortuneEscape(id->.2);rollValue.set(.1);
        assertEquals(StatusService.Admission.FORTUNE_STATUS_AVOIDED,status.admit(app,ControlProfile.NORMAL,true,random,claim));
        assertEquals(2,claims.get());assertEquals(3,rolls.get());assertTrue(status.inspect(target).active().isEmpty());
        assertEquals(StatusService.Admission.PROC_LOCK,status.admit(app,ControlProfile.NORMAL,true,random,()->false));
        assertEquals(3,rolls.get());
    }
    @Test void sourceAffixPairLocksSurviveRootChangesAndRestoreRemainingLifetime(){
        record Key(UUID actor,long generation,String affix,UUID target){}
        var key=new Key(UUID.randomUUID(),3,"ME-014",UUID.randomUUID());
        var locks=new TimedOpportunityLedger<Key>(2);
        assertEquals(TimedOpportunityLedger.Result.CLAIMED,locks.claim(key,10,2));
        assertEquals(TimedOpportunityLedger.Result.LOCKED,locks.claim(key,11.5,2));
        var restored=new TimedOpportunityLedger<Key>(2);restored.restore(locks.snapshot(11.5),500);
        assertEquals(TimedOpportunityLedger.Result.LOCKED,restored.claim(key,500.49,2));
        assertEquals(TimedOpportunityLedger.Result.CLAIMED,restored.claim(key,500.5,2));
        assertThrows(IllegalStateException.class,()->restored.restore(List.of(),500.5));
        assertEquals(TimedOpportunityLedger.Result.CLOCK_REVERSED,restored.claim(key,499,2));
        var another=new Key(key.actor(),key.generation(),"ME-025",key.target());
        assertEquals(TimedOpportunityLedger.Result.CLAIMED,restored.claim(another,500.5,2));
        assertEquals(TimedOpportunityLedger.Result.CAPACITY,restored.claim(new Key(UUID.randomUUID(),3,"ME-014",key.target()),500.5,2));
        assertEquals(TimedOpportunityLedger.Result.LOCKED,restored.claim(key,500.5,2));
    }
    @Test void extractionPreservesExistingDisplacementStringsBoundaryAndNoLiveEviction(){
        var ledger=new RootDisplacementLedger();
        assertEquals("PASS",ledger.claim("target",0));
        assertEquals("ROOT_TARGET_DISPLACEMENT_ICD",ledger.claim("target",.999999));
        assertEquals("DISPLACEMENT_CLOCK_REVERSED",ledger.claim("target",0));
        assertEquals("PASS",ledger.claim("target",1));
        for(int i=0;i<255;i++)assertEquals("PASS",ledger.claim("victim-"+i,1));
        assertEquals("ROOT_DISPLACEMENT_TARGET_BUDGET",ledger.claim("over",1));
        assertEquals(256,ledger.size());assertEquals("PASS",ledger.claim("over",2));assertEquals(1,ledger.size());
    }
}
