package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.npc.movement.Steering;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.ControlledGearSnapshot;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixStatusProjectionTest {
    @Test void nativeNpcControllerStunStopsMovementAndActionsWhileRootRetainsActions() {
        var body=new Steering().setTranslation(1,0,0);
        var head=new Steering().setYaw(1);
        var interrupts=new AtomicInteger();
        AreaNpcControlSystem.constrain(EnumSet.of(RpgStatusType.STUN),body,head,interrupts::incrementAndGet);
        assertFalse(body.hasTranslation());
        assertFalse(head.hasYaw());
        assertEquals(1,interrupts.get());
        body.setTranslation(1,0,0);
        AreaNpcControlSystem.constrain(EnumSet.of(RpgStatusType.ROOT),body,head,interrupts::incrementAndGet);
        assertFalse(body.hasTranslation());
        assertEquals(1,interrupts.get());
        AreaNpcControlSystem.constrain(EnumSet.of(RpgStatusType.SILENCE),body,head,interrupts::incrementAndGet);
        assertEquals(2,interrupts.get());
    }
    @Test void nativeOutgoingPhysicalFilterUsesOneBlindOrElectrifiedChance() {
        assertTrue(NativeStatusPhysicalMiss.shouldMiss(.5,.499));
        assertFalse(NativeStatusPhysicalMiss.shouldMiss(.5,.5));
        assertTrue(NativeStatusPhysicalMiss.shouldMiss(.25,.249));
        assertFalse(NativeStatusPhysicalMiss.shouldMiss(0,0));
    }
    @Test void hostileDisplacementReducesRequestBeforeSweptCollision() {
        var origin=new Vec3(0,0,0);var center=new Vec3(-1,0,0);
        var reduced=NativeAffixHostileDisplacement.plan(origin,center,false,4,ControlProfile.NORMAL,true,
                ControlledGearSnapshot.with("WA-082"),(point,segment)->1,p->true);
        assertEquals(4*(1-ControlledGearSnapshot.with("WA-082").percent("WA-082")),reduced.distance(),1e-9);
        var wall=NativeAffixHostileDisplacement.plan(origin,center,false,4,ControlProfile.NORMAL,true,
                ControlledGearSnapshot.with("WA-082"),(point,segment)->point.x()>=1.75?0:1,p->true);
        assertEquals(1.75,wall.distance(),1e-9);
        var plain=NativeAffixHostileDisplacement.plan(origin,center,false,4,ControlProfile.NORMAL,true,
                GearEffectSnapshot.EMPTY,(point,segment)->1,p->true);
        assertEquals(4,plain.distance(),1e-9);
    }
}
