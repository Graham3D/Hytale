package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.REFLECTIVE;
import static org.junit.jupiter.api.Assertions.*;

class EnemyReflectiveEffectsTest {
    final EnemyAffixSnapshotTest fixture=new EnemyAffixSnapshotTest();
    final EnemyDescriptor defender=fixture.actor(false,List.of(fixture.affix(REFLECTIVE)),List.of(),null);
    final UUID attacker=UUID.randomUUID();
    @Test void eachLiveComponentReflectsActualLossWithPerEventRecipientCap(){
        var effects=new EnemyReflectiveEffects();var sent=new ArrayList<Double>();
        for(int component=0;component<3;component++){
            var result=effects.offer(defender,attacker,"live/"+component,70,100,200,0,true,0,()->true,
                    raw->{sent.add(raw);return raw;});
            assertEquals(2,result.rawSent(),1e-10);
        }
        assertEquals(List.of(2d,2d,2d),sent);
        assertTrue(effects.snapshot(0).isEmpty());
    }
    @Test void noLossIndirectOrNoLongerCurrentDoesNotReflect(){
        var effects=new EnemyReflectiveEffects();
        assertEquals(0,effects.offer(defender,attacker,"indirect",10,100,200,0,false,0,()->true,raw->fail()).rawSent());
        assertEquals(0,effects.offer(defender,attacker,"absorbed",0,100,200,0,true,0,()->true,raw->fail()).rawSent());
        assertEquals(0,effects.offer(defender,attacker,"gone",10,100,200,0,true,0,()->false,raw->fail()).rawSent());
    }
}
