package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.power.ItemPowerDescriptor;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06LinksTest {
    @Test void plainStrikePotencyIsFifteenPercentNotDuplicatedByExecutionAndSnapshotFactory() {
        var h=new Harness("quick_slash","potency");assertTrue(h.cast().committed());
        assertEquals(List.of(.15),h.port.released.getFirst().snapshot().modifiers().increased());
        assertEquals(1.15,h.port.released.getFirst().snapshot().modifiers().factor(),1e-12);
    }
    @Test void delayEchoAndPotencyPayOnceAndKeepCommittedSnapshotTargetAndIdentities() {
        var h=new Harness("frost_bolt","skill_delay","echo","potency");
        assertEquals("RELEASE_PENDING",h.cast().code());double paid=h.port.current(ResourceType.MANA);
        assertTrue(h.port.released.isEmpty());assertEquals(1,h.service.pendingReleaseCount());
        h.advance(1.999);assertTrue(h.port.released.isEmpty());
        h.port.capturePoint=new Vec3(99,0,0); // Later aim cannot replace the committed point.
        h.advance(2);assertEquals(1,h.port.released.size());
        h.advance(2.449);assertEquals(1,h.port.released.size());
        h.advance(2.45);assertEquals(2,h.port.released.size());
        var primary=h.port.released.getFirst();var echo=h.port.released.getLast();
        assertEquals(1.35*1.15,primary.snapshot().modifiers().factor(),1e-12);
        assertEquals(.945*1.15,echo.snapshot().modifiers().factor(),1e-12);
        assertEquals(primary.target(),echo.target());assertEquals(Vec3.ZERO,echo.target().point());
        assertEquals(primary.rootCastId(),echo.rootCastId());assertEquals(primary.request().correlationId(),echo.request().correlationId());
        assertNotEquals(primary.skillInstanceId(),echo.skillInstanceId());assertEquals(echo.skillInstanceId(),echo.snapshot().skillInstanceId());
        assertEquals(primary.snapshot().basePower(),echo.snapshot().basePower());
        assertEquals(2,h.port.projectiles.registry().size());assertEquals(paid,h.port.current(ResourceType.MANA));
        assertEquals(1,h.trace.records.stream().filter(r->r.eventType()==RpgTraceEventType.SKILL_COMMITTED).count());
        assertThrows(IllegalStateException.class,echo::echoCopy);
        h.advance(3);assertEquals(2,h.port.released.size());assertEquals(0,h.service.pendingReleaseCount());
    }
    @Test void expandedRadiusChangesAuthoredGeometryAndMagnitudeExactlyOnce() {
        var h=new Harness("frost_nova","expanded_radius","potency");assertTrue(h.cast().committed());
        var context=h.port.released.getFirst();
        assertEquals(1.15*.9,context.snapshot().modifiers().factor(),1e-12);
        assertEquals(6.875,h.port.area.presented.getFirst().radius());assertEquals(3,h.port.area.presented.getFirst().height());
        assertEquals(18,200-h.port.current(ResourceType.MANA));
        var trap=new Harness("powder_mine","expanded_radius");assertTrue(trap.cast().committed());
        trap.port.area.targets=List.of(Stage06AreaRuntimeTest.target("trigger",3,0),Stage06AreaRuntimeTest.target("blast",4.8,0));
        trap.advance(.5);assertEquals(2,trap.port.area.payloads.size());assertEquals(5,trap.port.area.presented.getLast().radius());
    }
    @Test void ownerCancellationNeverRefundsPaidDelayOrLeavesFutureRelease() {
        for(String reason:List.of("DEATH","LOGOUT","WORLD_UNLOAD")) {
            var h=new Harness("fire_bolt","skill_delay","echo");h.cast();double paid=h.port.current(ResourceType.MANA);
            assertTrue(h.service.cancel(h.actor,reason));assertFalse(h.service.cancel(h.actor,reason));h.advance(3);
            assertEquals(paid,h.port.current(ResourceType.MANA));assertTrue(h.port.released.isEmpty());
            assertEquals(0,h.service.pendingReleaseCount());
        }
    }
    @Test void invalidCommittedTargetOrNativeFailureCannotReplayOrRetargetPaidCast() {
        for(boolean failure:new boolean[]{false,true}) {
            var h=new Harness("frost_nova","skill_delay","echo");h.cast();double paid=h.port.current(ResourceType.MANA);
            h.port.releaseValid=failure;h.port.failDispatch=failure;h.advance(2);h.port.failDispatch=false;h.port.releaseValid=true;h.advance(4);
            assertEquals(failure?1:0,h.port.released.size());assertEquals(paid,h.port.current(ResourceType.MANA));
            assertEquals(0,h.service.pendingReleaseCount());assertTrue(h.kernel.cooldowns().remaining(h.actor,"frost_nova")>0);
        }
    }
    @Test void onePendingPrimaryPerSlotSurvivesLoadoutChangeWithoutChargingAnotherSkill() {
        var h=new Harness("fire_bolt","skill_delay");h.cast();double paid=h.port.current(ResourceType.MANA);
        assertTrue(h.bundle.service().equipSkill(h.actor,SkillSlot.SKILL01,new SkillId("frost_bolt")).success());
        assertEquals("PENDING_PRIMARY_FOR_SLOT",h.cast().code());assertEquals(paid,h.port.current(ResourceType.MANA));
        h.advance(2);assertEquals("fire_bolt",h.port.released.getFirst().profile().skillId());
    }
    @Test void unsupportedTargetCaptureFailsBeforePaymentAndReleasesReservation() {
        var h=new Harness("fire_bolt","skill_delay");h.port.captureAvailable=false;
        assertTrue(h.cast().code().startsWith("TARGET_CAPTURE_FAILED"));assertEquals(200,h.port.current(ResourceType.MANA));
        assertEquals(0,h.service.pendingReleaseCount());assertEquals(0,h.kernel.cooldowns().remaining(h.actor,"fire_bolt"));
    }
    @Test void nativeProjectilePlanDoesNotScaleCollisionRadiusForEchoOrDelay() {
        var h=new Harness("frost_bolt","skill_delay","echo");h.cast();h.advance(2);h.advance(2.45);
        assertEquals(2,h.port.plans.size());
        assertEquals(h.port.plans.getFirst().radius(),h.port.plans.getLast().radius());
        assertNotEquals(h.port.plans.getFirst().projectileInstanceId(),h.port.plans.getLast().projectileInstanceId());
    }
    @Test void compatibilityAndExclusiveRepeatControllerRemainExplicit() {
        var b=Stage01BTestSupport.bundle();var service=new com.inigmasgames.hytalerpg.links.CompatibilityService();
        for(var pair:List.of(new String[]{"frost_bolt","echo","true"},new String[]{"quick_slash","echo","false"},
                new String[]{"managuard","skill_delay","false"},new String[]{"meteor","skill_delay","true"},
                new String[]{"frost_bolt","expanded_radius","false"},new String[]{"root_snare","expanded_radius","true"}))
            assertEquals(Boolean.parseBoolean(pair[2]),service.assess(b.catalog().skill(new SkillId(pair[0])).orElseThrow(),
                    b.catalog().passive(new PassiveId(pair[1])).orElseThrow()).accepted(),Arrays.toString(pair));
        var h=new Harness("frost_bolt","echo");
        assertTrue(h.bundle.service().equipPassive(h.actor,PassiveSlot.PASSIVE02,new PassiveId("barrage")).success());
        assertFalse(h.bundle.service().link(h.actor,LinkNodeId.PASSIVE02,LinkNodeId.SKILL01).success());
        for(String skill:List.of("corpse_burst","bone_cage","root_snare")) {
            var result=service.assess(b.catalog().skill(new SkillId(skill)).orElseThrow(),b.catalog().passive(new PassiveId("echo")).orElseThrow());
            assertFalse(result.accepted(),skill);
        }
        assertTrue(service.assess(b.catalog().skill(new SkillId("wall_of_fire")).orElseThrow(),
                b.catalog().passive(new PassiveId("echo")).orElseThrow()).accepted()); // A non-solid wall is not a collision wall.
    }
    @Test void schedulerReservesBoundedCapacityAndRejectsDuplicatePrimarySlots() {
        var scheduler=new SkillReleaseScheduler();var delay=new CompiledSkillPlan.ExecutionModifiers(1,2,.45,.7,false);
        UUID owner=UUID.randomUUID();assertEquals("PASS",scheduler.reserve("a",owner,SkillSlot.SKILL01,delay));
        assertEquals("PENDING_PRIMARY_FOR_SLOT",scheduler.reserve("b",owner,SkillSlot.SKILL01,delay));scheduler.cancel(owner);
        for(int i=0;i<SkillReleaseScheduler.GLOBAL_CAP;i++)assertEquals("PASS",scheduler.reserve("root"+i,new UUID(0,i),SkillSlot.SKILL01,delay));
        assertEquals("GLOBAL_RELEASE_BUDGET",scheduler.reserve("overflow",UUID.randomUUID(),SkillSlot.SKILL01,delay));
        for(int i=0;i<SkillReleaseScheduler.GLOBAL_CAP;i++)scheduler.cancel(new UUID(0,i));assertEquals(0,scheduler.size());
    }
    @Test void echoCannotBypassAvalancheRootHitCap() {
        var h=new Harness("avalanche","echo");h.port.area.targets=List.of(new AreaWorldPort.Target("giant",
                new AreaGeometry.Bounds(new Vec3(-11,0,-11),new Vec3(11,2,11)),false));
        h.cast();for(int i=1;i<=132;i++)h.advance(i*.05);
        assertEquals(2,h.port.released.size());assertEquals(3,h.port.area.payloads.size());assertEquals(0,h.port.areas.retainedRootCount());
    }
    @Test void echoCannotBypassBlizzardPerRootHitInterval() {
        var h=new Harness("blizzard","echo");h.port.area.targets=List.of(new AreaWorldPort.Target("giant",
                new AreaGeometry.Bounds(new Vec3(-8,0,-8),new Vec3(8,2,8)),false));
        h.cast();for(int i=1;i<=180;i++)h.advance(i*.05);
        for(int i=1;i<h.port.hitTimes.size();i++)assertTrue(h.port.hitTimes.get(i)-h.port.hitTimes.get(i-1)>=.75-1e-8);
        assertTrue(h.port.area.payloads.size()<=12);assertEquals(0,h.port.areas.size());assertEquals(0,h.port.areas.retainedRootCount());
    }
    private static class Harness {
        final UUID actor=UUID.randomUUID();final AtomicLong clock=new AtomicLong();
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final Stage01BTestSupport.RecordingTracer trace=(Stage01BTestSupport.RecordingTracer)bundle.tracer();
        final RpgCombatKernel kernel=new RpgCombatKernel(CombatBalanceProfile.loadCanonical(),new CriticalRoller(()->1));
        final SkillExecutionService service;final Port port;final Stage04SkillProfile profile;
        Harness(String skill,String...passives) {
            assertTrue(bundle.service().equipSkill(actor,SkillSlot.SKILL01,new SkillId(skill)).success());
            for(int i=0;i<passives.length;i++) {
                var slot=PassiveSlot.values()[i];assertTrue(bundle.service().equipPassive(actor,slot,new PassiveId(passives[i])).success());
                assertTrue(bundle.service().link(actor,LinkNodeId.valueOf(slot.name()),LinkNodeId.SKILL01).success(),skill+" + "+passives[i]);
            }
            var profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());profile=profiles.require(skill);
            service=new SkillExecutionService(bundle.service(),profiles,kernel,SkillExecutorRegistry.stage04(),new SkillInstanceLifecycle(),trace,clock::get);
            port=new Port(this);
        }
        SkillExecutionResult cast() {return service.request(new SkillExecutionRequest(actor,SkillSlot.SKILL01,"fixture",42,UUID.randomUUID().toString(),Vec3.FORWARD),port);}
        void advance(double seconds) {clock.set(Math.round(seconds*1e9));service.tickScheduled(actor,port);port.areas.tick(actor,seconds,port.world);}
    }
    private static class Port implements SkillExecutionPort,NativeResourcePort {
        final Harness h;final Map<ResourceType,Double> values=new EnumMap<>(ResourceType.class);final Equipment equipment;
        final List<SkillExecutionContext> released=new ArrayList<>();final AreaRuntime areas=new AreaRuntime();
        final Stage06AreaRuntimeTest.FakePort area=new Stage06AreaRuntimeTest.FakePort();final List<Double> hitTimes=new ArrayList<>();
        final List<ProjectileExecutionPlan> plans=new ArrayList<>();final RpgProjectileService projectiles=new RpgProjectileService(new ProjectileLifecycleRegistry());
        final AreaWorldPort world;boolean releaseValid=true,captureAvailable=true,failDispatch;Vec3 capturePoint=Vec3.ZERO;
        Port(Harness h) {
            this.h=h;values.put(ResourceType.MANA,200d);values.put(ResourceType.STAMINA,200d);
            String kind=h.profile.allowedMainHandKinds().stream().sorted().findFirst().orElse("NONE");
            equipment=new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of(kind),20d,20d)),null);
            area.targets=List.of(Stage06AreaRuntimeTest.target("inside",1,0));
            world=new AreaWorldPort() {
                public Query query(AreaGeometry s,int budget){return area.query(s,budget);} public boolean lineOfSight(Vec3 o,Target t){return true;}
                public boolean apply(SkillExecutionContext c,Target t,Payload p){hitTimes.add(h.clock.get()/1e9);return area.apply(c,t,p);}
                public void present(SkillExecutionContext c,AreaGeometry s,String p,double d){area.present(c,s,p,d);}
                public void trace(SkillExecutionContext c,String e,Map<String,?> d){area.trace(c,e,d);}
            };
        }
        public boolean actorAliveAndUsable(){return true;} public Equipment equipment(){return equipment;} public NativeResourcePort resources(){return this;}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest r){return captureAvailable?new CommittedTarget(new UUID(0,1),Vec3.ZERO,capturePoint,Vec3.FORWARD,null):null;}
        public Validation validateRelease(SkillExecutionContext c){return releaseValid?Validation.pass():Validation.reject("COMMITTED_TARGET_INVALID");}
        private void record(SkillExecutionContext c){released.add(c);if(failDispatch)throw new IllegalStateException("fixture native dispatch failure");}
        public SkillExecutionResult executeArea(SkillExecutionContext c){record(c);areas.start(c,c.target()==null?Vec3.ZERO:c.target().point(),Vec3.FORWARD,h.clock.get()/1e9,c.compiledPlan().executionModifiers().radiusFactor(),world);return SkillExecutionResult.committed("AREA",0,0);}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){record(c);var p=c.profile().projectile();var plan=projectiles.buildPlan(c,h.actor,Vec3.ZERO,Vec3.FORWARD,p.configId(),p.speed(),h.clock.get());plans.add(plan);projectiles.onProjectileSpawn(plan);return SkillExecutionResult.committed("PROJECTILE",0,0);}
        public SkillExecutionResult executeStrike(SkillExecutionContext c){record(c);return SkillExecutionResult.committed("STRIKE",0,0);}
        public SkillExecutionResult executeMovement(SkillExecutionContext c){record(c);return SkillExecutionResult.committed("MOVEMENT",0,0);}
        public SkillExecutionResult executeReaction(SkillExecutionContext c){throw new AssertionError("Echo/Delay cannot wrap Reaction");}
        public double current(ResourceType t){return values.getOrDefault(t,0d);} public double maximum(ResourceType t){return 200;}
        public void setCurrent(ResourceType t,double value){values.put(t,value);}
    }
}
