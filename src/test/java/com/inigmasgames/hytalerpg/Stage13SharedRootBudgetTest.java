package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13SharedRootBudgetTest {
    final Stage13AuthoredProjectileTest fixture=new Stage13AuthoredProjectileTest();
    @Test void mixedNativeCarriersAndOtherEffectsHaveOneFortyEightLimit(){
        var h=fixture.cast("blunderbuss_shot","GUN");var c=h.last();var registry=new ProjectileLifecycleRegistry();
        registry.registerAll(fixture.plans(h,0).stream().map(ProjectileInstance::new).toList(),c.effects().projectileLifetime());
        assertEquals(8,c.effects().spawned());
        for(int i=0;i<40;i++)assertEquals("PASS",c.effects().claim("other"+i,1,false));
        assertEquals(48,c.effects().spawned());assertEquals("ROOT_SPAWN_EFFECT_BUDGET",c.effects().claim("overflow",1,false));
    }
    @Test void preexistingOtherEffectsCanAtomicallyRejectANativeBatch(){
        var h=fixture.cast("blunderbuss_shot","GUN");var c=h.last();var registry=new ProjectileLifecycleRegistry();
        for(int i=0;i<41;i++)assertEquals("PASS",c.effects().claim("other"+i,1,false));
        var thrown=assertThrows(IllegalStateException.class,()->registry.registerAll(fixture.plans(h,0).stream().map(ProjectileInstance::new).toList(),c.effects().projectileLifetime()));
        assertEquals("ROOT_SPAWN_EFFECT_BUDGET",thrown.getMessage());assertEquals(0,registry.size());assertEquals(0,registry.rootCount());assertEquals(42,c.effects().spawned());
    }
    @Test void nativeShrapnelAndHitProcsShareSixteenTriggeredSlots(){
        var h=fixture.cast("fireball","STAFF");var c=h.last();var instance=new ProjectileInstance(fixture.plans(h,0).getFirst());
        var registry=new ProjectileLifecycleRegistry();registry.registerAll(List.of(instance),c.effects().projectileLifetime());
        for(int i=0;i<9;i++)assertEquals("PASS",c.effects().claim("proc"+i,2,true));
        for(int i=0;i<7;i++)assertEquals("PASS",registry.reserveSecondary(instance,"native"+i));
        assertEquals(16,c.effects().triggered());assertEquals("ROOT_TRIGGERED_SECONDARY_BUDGET",registry.reserveSecondary(instance,"overflow"));
        assertEquals("ROOT_TRIGGERED_SECONDARY_BUDGET",c.effects().claim("other-overflow",2,true));
    }
    @Test void repeatedAuthoredComponentContactsAreNotAdditionalSpawnedControllers(){
        var budget=new RootEffectBudget(UUID.randomUUID(),"root");
        for(int i=0;i<512;i++)assertEquals("PASS",budget.authoredComponent("orbit-explosion"));
        assertEquals(2,budget.spawned());assertEquals(0,budget.triggered());
        assertEquals("DUPLICATE_EFFECT",budget.claim("orbit-explosion",1,false));
    }
    @Test void nativeCarrierRemovalDoesNotRefundTheLifetimeEffectCount(){
        var h=fixture.cast("blunderbuss_shot","GUN");var c=h.last();var registry=new ProjectileLifecycleRegistry();
        var batch=fixture.plans(h,0).stream().map(ProjectileInstance::new).toList();registry.registerAll(batch,c.effects().projectileLifetime());
        batch.forEach(registry::remove);assertEquals(0,registry.size());assertEquals(8,c.effects().spawned());
        assertThrows(IllegalStateException.class,()->registry.registerAll(batch,c.effects().projectileLifetime()));
    }
    @Test void cosmeticPelletGroupingDoesNotChangeDamageLedgers(){
        var group=new com.inigmasgames.hytalerpg.vfx.ProjectileReadability.Group();assertTrue(group.cast("release"));assertFalse(group.cast("release"));
        assertTrue(group.impact("victim",0));for(int i=0;i<7;i++)assertFalse(group.impact("victim",0));assertTrue(group.impact("victim",50_000_000));
        var h=fixture.cast("blunderbuss_shot","GUN");assertEquals(8,fixture.plans(h,0).stream().map(ProjectileInstance::new).filter(p->p.acceptTarget("victim")).count());
    }
    @Test void explosionOnlyOrbitPreservesItsPayloadWithoutInventingDirectDamage(){
        var f=new Stage11FoundationTest();
        for(String skill:List.of("bomb_toss","explosive_flask")){
            var p=f.effective(skill,"orbit");assertEquals(Stage04SkillProfile.Family.ORBIT,p.family());assertEquals(0,p.projectile().coefficient());
            assertEquals(p.projectile().details().explosion().coefficient(),p.connection().coefficient());assertEquals(4,p.connection().lifetimeSeconds());
        }
        for(String skill:List.of("arcane_missiles","blunderbuss_shot"))assertEquals(3,f.effective(skill,"orbit").connection().details().bladeCount());
    }
}
