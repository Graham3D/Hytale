package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyBulwarkTest {
    private final UUID world=UUID.randomUUID(),actor=UUID.randomUUID();
    private final FiniteSupportEffects.IntrinsicShieldKey key=new FiniteSupportEffects.IntrinsicShieldKey(world,actor,3,"ME-027");
    private FiniteSupportEffects.ShieldHit hit(FiniteSupportEffects owner,double amount,double now){
        return owner.shieldHit(world,actor,amount,true,now,(effect,transfer)->{throw new AssertionError("Bulwark cannot redirect");});
    }
    @Test void consumesOnceWithoutChangingHealthCapacityOrRegenerating(){
        var owner=new FiniteSupportEffects();var initial=new FiniteSupportEffects.IntrinsicShield(key,250,0);
        owner.restoreIntrinsicShield(initial);
        var first=hit(owner,100,0);assertEquals(100,first.absorbed());assertEquals(0,first.remainder());
        assertTrue(first.allocations().isEmpty());assertEquals(1,first.intrinsicAllocations().size());
        owner.restoreIntrinsicShield(initial); // duplicate registration cannot recharge
        assertEquals(150,owner.intrinsicShield(key).orElseThrow().remaining());
        var last=hit(owner,200,10000);assertEquals(150,last.absorbed());assertEquals(50,last.remainder());
        assertEquals(100,hit(owner,100,20000).remainder());assertEquals(1,owner.size());
        assertEquals(250,owner.intrinsicShield(key).orElseThrow().capacity());
    }
    @Test void reloadKeepsConsumedAmountAndOldGenerationCannotReplaceLease(){
        var owner=new FiniteSupportEffects();owner.restoreIntrinsicShield(new FiniteSupportEffects.IntrinsicShield(key,100,0));
        hit(owner,30,0);var json=new com.google.gson.Gson().toJson(owner.intrinsicShield(key).orElseThrow());
        var restored=new com.google.gson.Gson().fromJson(json,FiniteSupportEffects.IntrinsicShield.class);
        var restarted=new FiniteSupportEffects();restarted.restoreIntrinsicShield(restored);
        assertEquals(30,hit(restarted,100,0).remainder());
        assertThrows(IllegalStateException.class,()->restarted.restoreIntrinsicShield(new FiniteSupportEffects.IntrinsicShield(
                new FiniteSupportEffects.IntrinsicShieldKey(world,actor,4,"ME-027"),100,0)));
        assertThrows(IllegalStateException.class,()->restarted.restoreIntrinsicShield(new FiniteSupportEffects.IntrinsicShield(key,200,0)));
        restarted.unbindIntrinsicShield(key);assertEquals(0,restarted.size());
    }
    @Test void worldsAndMutationGatesRemainIsolated(){
        var owner=new FiniteSupportEffects();owner.restoreIntrinsicShield(new FiniteSupportEffects.IntrinsicShield(key,100,0));
        assertEquals(20,owner.shieldHit(UUID.randomUUID(),actor,20,true,0,(a,b)->false).remainder());
        owner.cleanseStats(world,actor,3,()->true);assertEquals(100,owner.intrinsicShield(key).orElseThrow().remaining());
        owner.clearWorld(world);assertEquals(20,hit(owner,20,1).remainder());
    }
}
