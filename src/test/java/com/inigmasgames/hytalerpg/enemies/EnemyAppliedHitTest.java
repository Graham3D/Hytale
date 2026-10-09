package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyAppliedHitTest {
    static EnemyOffenseSnapshot offense(){
        return new EnemyOffenseSnapshot(new WeaponDamageExecution.Identity(UUID.randomUUID(),UUID.randomUUID(),"root","action","strike"),
                1,"binding","channels","balance",1,Map.of("Physical",100d),100,Map.of("Physical",100d,"Fire",25d),null,0);
    }
    @Test void aggregateCountsActualLossAfterAbsorbAndNeverOverkill(){
        var result=EnemyAppliedHit.completed(offense(),UUID.randomUUID(),Map.of(
                "Physical",new NativeResult(false,100,70,10,100),"Fire",new NativeResult(false,25,10,-15,25)));
        assertEquals(70,result.actualHealthLoss());
        assertEquals(0,EnemyAppliedHit.healthLoss(new NativeResult(true,100,70,70)));
        assertEquals(0,EnemyAppliedHit.healthLoss(new NativeResult(false,0,70,70)));
        assertEquals(0,EnemyAppliedHit.healthLoss(new NativeResult(false,10,-1,-11)));
    }
    @Test void unavailableOrIncompleteReceiptsCannotCreateReactions(){
        assertThrows(IllegalArgumentException.class,()->EnemyAppliedHit.completed(offense(),UUID.randomUUID(),
                Map.of("Physical",new NativeResult(false,100,70,0))));
        assertThrows(IllegalArgumentException.class,()->EnemyAppliedHit.healthLoss(new NativeResult(false,10,Double.NaN,0)));
        assertThrows(IllegalArgumentException.class,()->EnemyAppliedHit.healthLoss(new NativeResult(false,Double.NaN,10,0)));
    }
}
