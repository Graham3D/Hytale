package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real validate -> commit path; native basic attacks do not use SkillExecutionService. */
class StatusSilenceExecutionTest {
    @Test void silenceRejectsManualBeforePaymentAndLeavesTriggeredOriginAvailable(){
        var negative=new Harness(true);
        var rejected=negative.cast(SkillExecutionRequest.Origin.MANUAL);
        assertEquals(SkillExecutionResult.Status.REJECTED,rejected.status());
        assertEquals("SILENCED",rejected.code());
        assertEquals(1000,negative.mana);
        assertNull(negative.dispatched);
        var control=new Harness(false);
        assertNotEquals("SILENCED",control.cast(SkillExecutionRequest.Origin.MANUAL).code());
        var triggered=new Harness(true);
        assertNotEquals("SILENCED",triggered.cast(SkillExecutionRequest.Origin.TRIGGERED).code());
    }
    private static final class Harness implements SkillExecutionPort,NativeResourcePort {
        final UUID actor=UUID.randomUUID(),world=UUID.randomUUID();
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final Stage04SkillProfiles profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());
        final SkillExecutionService service;
        final boolean silenced;
        SkillExecutionContext dispatched;
        double mana=1000,stamina=1000;
        long now;
        Harness(boolean silenced){
            this.silenced=silenced;
            assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId("wind_cutter")).success());
            service=new SkillExecutionService(bundle.service(),profiles,RpgCombatKernel.createProduction(),
                    SkillExecutorRegistry.runtime(),new SkillInstanceLifecycle(),bundle.tracer(),()->now);
        }
        SkillExecutionResult cast(SkillExecutionRequest.Origin origin){
            var result=service.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"status-test",1,
                    "silence-"+origin,Vec3.FORWARD,origin),this);
            if(result.status()==SkillExecutionResult.Status.PENDING){now=20_000_000_000L;service.tickScheduled(actor,this);}
            return result;
        }
        @Override public boolean manualSkillsSilenced(){return silenced;}
        @Override public boolean actorAliveAndUsable(){return true;}
        @Override public Equipment equipment(){
            return new Equipment(new Item("fixture","STAFF",new ItemPowerDescriptor("fixture",Set.of("STAFF"),20d,20d)),null);
        }
        @Override public NativeResourcePort resources(){return this;}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan c){return Validation.pass();}
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan c,SkillExecutionRequest r){
            return new CommittedTarget(world,Vec3.ZERO,Vec3.FORWARD,Vec3.FORWARD,actor);
        }
        @Override public Validation validateRelease(SkillExecutionContext c){return Validation.pass();}
        private SkillExecutionResult done(SkillExecutionContext c){dispatched=c;return SkillExecutionResult.committed("EXECUTED",1,0);}
        @Override public SkillExecutionResult executeStrike(SkillExecutionContext c){return done(c);}
        @Override public SkillExecutionResult executeMovement(SkillExecutionContext c){return done(c);}
        @Override public SkillExecutionResult executeReaction(SkillExecutionContext c){return done(c);}
        @Override public SkillExecutionResult executeProjectile(SkillExecutionContext c){return done(c);}
        @Override public double current(ResourceType type){return type==ResourceType.STAMINA?stamina:mana;}
        @Override public double maximum(ResourceType type){return 1000;}
        @Override public void setCurrent(ResourceType type,double value){if(type==ResourceType.STAMINA)stamina=value;else mana=value;}
    }
}
