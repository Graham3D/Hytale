package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.input.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.*;
import com.hypixel.hytale.protocol.InteractionType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13SnipeReleaseTest {
    static class EmptySpace extends Stage11ResourcePassivesTest.H {
        EmptySpace(){super("snipe");weapon="BOW";}
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return null;}
    }
    SkillExecutionRequest request(EmptySpace h){return new SkillExecutionRequest(h.actor,SkillSlot.SKILL01,"Ability2",1,"snipe-release-test",Vec3.FORWARD);}
    @Test void releasedRootPaysOnceAndUsesNativeChargedPhysicsIntoEmptySpace(){
        var h=new EmptySpace();assertTrue(h.service.requestNative(request(h),h,"snipe").committed());
        assertNull(h.last().target());assertEquals(88,h.current(ResourceType.STAMINA));assertEquals(1,h.cooldownSaves);
        var p=h.last().profile().projectile();assertEquals(85,p.speed());assertEquals(25,p.gravity());assertEquals(.075,p.radius());
        assertEquals(48,p.maxDistance());assertEquals(2,p.coefficient());assertTrue(p.fullyCharged());assertEquals(1,p.ammoQuantity());
        h.service.terminate(h.last(),"EMPTY_SPACE_EXPIRY");assertEquals(88,h.current(ResourceType.STAMINA));
        assertEquals("COOLDOWN_ACTIVE",h.service.requestNative(request(h),h,"snipe").code());assertEquals(1,h.contexts.size());
    }
    @Test void releaseRevalidatesEquipmentResourcesAndDeathBeforePayment(){
        var wrong=new EmptySpace();wrong.weapon="SWORD";assertEquals("INVALID_MAIN_HAND",wrong.cast().code());
        var poor=new EmptySpace();poor.current.put(ResourceType.STAMINA,0d);assertFalse(poor.cast().committed());
        var dead=new EmptySpace();dead.alive=false;assertFalse(dead.cast().committed());
        for(var h:List.of(wrong,poor,dead)){assertTrue(h.contexts.isEmpty());assertEquals(0,h.cooldownSaves);}
    }
    @Test void changedSkillCannotBeCastByAnOldHeldSnipeChain(){
        var h=new EmptySpace();assertTrue(h.b.service().equipSkill(h.actor,SkillSlot.SKILL01,new SkillId("quick_shot")).success());
        assertEquals("HELD_SKILL_CHANGED",h.service.requestNative(request(h),h,"snipe").code());
        assertTrue(h.contexts.isEmpty());assertEquals(100,h.current(ResourceType.STAMINA));assertEquals(0,h.cooldownSaves);
    }
    @Test void releaseCallbackUsesExistingOncePerChainDeliveryAndBindsSnipe(){
        var h=new EmptySpace();var inputs=new HytaleAbilitySkillInputAdapter();inputs.useNativeExecution();Object chain=new Object();
        assertEquals(0,inputs.drainFor(h.actor,r->fail(),8)); // No server release callback means no paid request.
        for(int i=0;i<2;i++)inputs.acceptNativeExecution(h.actor,InteractionType.Ability2,1,chain,"RPG_Ability_Snipe",0);
        assertEquals(1,inputs.drainFor(h.actor,r->{assertEquals("snipe",r.expectedSkill());
            assertTrue(h.service.requestNative(new SkillExecutionRequest(r.player(),r.slot(),r.action(),r.chainId(),r.correlationId(),r.desiredMovement()),h,r.expectedSkill()).committed());},8));
        assertEquals(88,h.current(ResourceType.STAMINA));assertEquals(1,h.contexts.size());assertEquals(1,h.cooldownSaves);
    }
    @Test void forkHomingAndOrbitDoNotRestoreTheObsoleteNativeMaximumGate(){
        for(String passive:List.of("fork","homing","orbit")){
            var h=new EmptySpace();h.link(passive,PassiveSlot.PASSIVE01);
            assertTrue(h.cast().committed(),passive);
            assertEquals("",Stage04SkillProfiles.loadCanonical(h.b.catalog()).require("snipe").activationGate());
        }
    }
    @Test void onlySnipeUsesHoldReleaseRoot(){
        assertEquals("Root_RPG_Snipe_Release",NativeAbilityBridgeAudit.rootForItem("RPG_Ability_Snipe"));
        for(String id:List.of("Quick_Shot","Crossbow_Bolt","Fire_Bolt","Quick_Slash"))
            assertEquals(NativeAbilityBridgeAudit.ROOT_ID,NativeAbilityBridgeAudit.rootForItem("RPG_Ability_"+id));
    }
}
