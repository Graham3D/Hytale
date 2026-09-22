package com.inigmasgames.hytalerpg.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.ConcurrentInstancePolicy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class R078LifecycleSeparationTest {
    @Test void projectileLifetimeDoesNotBecomeACastLockAndCleanupIsExact(){
        var lifecycle=new SkillInstanceLifecycle();var owner=UUID.randomUUID();
        assertTrue(lifecycle.begin(owner,"a",SkillInstanceLifecycle.Phase.COMMITTED));
        assertTrue(lifecycle.transition(owner,"a",SkillInstanceLifecycle.Phase.COMMITTED,SkillInstanceLifecycle.Phase.PROJECTILE));
        assertTrue(lifecycle.begin(owner,"b",SkillInstanceLifecycle.Phase.COMMITTED));
        assertTrue(lifecycle.owns(owner,"a"));assertTrue(lifecycle.owns(owner,"b"));
        assertTrue(lifecycle.terminate(owner,"a"));assertTrue(lifecycle.owns(owner,"b"));
    }

    @Test void explicitConcurrencyContractsAreVisibleAndBounded(){
        assertEquals(ConcurrentInstancePolicy.Mode.UNRESTRICTED,ConcurrentInstancePolicy.forSkill("lightning_coil").mode());
        assertEquals(ConcurrentInstancePolicy.Mode.COMMAND_EXISTING,ConcurrentInstancePolicy.forSkill("bomb_toss").mode());
        assertThrows(IllegalArgumentException.class,()->ConcurrentInstancePolicy.max(0));
        assertEquals(2,ConcurrentInstancePolicy.max(2).maximum());assertEquals(ConcurrentInstancePolicy.Mode.SINGLETON,ConcurrentInstancePolicy.singleton().mode());
        var owner=UUID.randomUUID();var registry=new ConcurrentInstanceRegistry();
        registry.activate(owner,"single","a",ConcurrentInstancePolicy.singleton());
        assertEquals("INSTANCE_LIMIT_REACHED",registry.admission(owner,"single",ConcurrentInstancePolicy.singleton()));
        assertTrue(registry.terminate(owner,"single","a"));assertEquals("PASS",registry.admission(owner,"single",ConcurrentInstancePolicy.singleton()));
        var max=ConcurrentInstancePolicy.max(2);registry.activate(owner,"max","a",max);registry.activate(owner,"max","b",max);
        assertEquals("INSTANCE_LIMIT_REACHED",registry.admission(owner,"max",max));
        assertTrue(registry.terminate(owner,"max","a"));assertEquals(1,registry.count(owner,"max"));
    }

    @Test void recoveryRateChangesPreserveCompletedWorkAndUseDivisionSemantics(){
        var clock=new AtomicLong();var service=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),clock::get);var owner=UUID.randomUUID();
        service.startCooldown(owner,"skill",10,1,0,CompiledSkillPlan.KernelModifiers.NONE);
        clock.set(2_000_000_000L);assertEquals(8,service.remaining(owner,"skill"),1e-9);
        service.setOwnerRecovery(owner,.30);clock.set(4_000_000_000L);
        assertEquals(5.4/1.3,service.remaining(owner,"skill"),1e-9);
        service.setOwnerRecovery(owner,0);clock.set(5_000_000_000L);assertEquals(4.4,service.remaining(owner,"skill"),1e-9);
        assertEquals(10/1.3,service.calculate(10,1,.30,CompiledSkillPlan.KernelModifiers.NONE).finalSeconds(),1e-9);
        assertEquals(10/1.75,service.calculate(10,1,3,CompiledSkillPlan.KernelModifiers.NONE).finalSeconds(),1e-9);
    }

    @Test void castRateOnlyDividesPositiveWindup(){
        var rate=new CastRateModifiers(.25,.15,.10);
        assertEquals(2,rate.windup(3),1e-9);assertEquals(0,rate.windup(0),1e-9);
    }
}
