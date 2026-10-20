package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic backend fixtures. They do not establish native connected movement or rendering. */
class Stage11MobileDomainTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    @Test void acceptsOnlyTheThreeMovableGroundZoneProfiles(){
        for(String id:List.of("poison_cloud","vortex","earthquake"))assertTrue(f.accepts(id,"mobile_domain"),id);
    }
    @Test void rejectsAuraTrapAndPlacementOwnedBlizzard(){
        for(String id:List.of("thorns_aura","root_snare","blizzard","wall_of_fire","bone_cage","corpse_burst","fire_bolt"))assertFalse(f.accepts(id,"mobile_domain"),id);
    }
    @Test void conversionAddsMobileTagsButNotAuraOrReservation(){
        var plan=f.plan("poison_cloud","mobile_domain");assertEquals("MOBILE_ZONE",plan.finalFamily());
        assertTrue(plan.finalTags().containsAll(Set.of("MOBILE_ZONE","CASTER_ATTACHED_ZONE")));assertTrue(plan.zones().mobileDomain());
        assertTrue(Collections.disjoint(plan.finalTags(),Set.of("AURA","MANA_RESERVATION","HAS_UPKEEP")));
        assertEquals(1,plan.kernelModifiers().resourceCostMultiplier());assertNotEquals(plan.planHash(),f.plan("poison_cloud").planHash());
    }
    @Test void mobileDoesNotRewriteAuthoredPulseLifetimeRadiusOrCost(){
        var a=f.profiles.require("poison_cloud");assertSame(a,f.effective("poison_cloud","mobile_domain"));
        assertEquals(8,a.area().lifetimeSeconds());assertEquals(.25,a.area().intervalSeconds());assertEquals(24,a.resourceCost());assertEquals(16,a.cooldownSeconds());
    }
    @Test void onePaymentOneCooldownAndSharedSnapshotAtEightyPercent(){
        var h=mobile();h.targets=List.of(target("a",0,0));assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());
        h.step(.25);assertEquals(76,h.mana);assertEquals(1,h.resourceWrites);assertEquals(0,h.reserved);assertEquals(.8,h.context.snapshot().modifiers().factor());
        assertEquals(.3*.25,h.payloads.getFirst().coefficient());assertSame(h.context,h.hitContexts.getFirst());
        assertFalse(h.cast().committed());assertEquals(1,h.resourceWrites);assertEquals(0,h.runtime.auraCount());
    }
    @Test void potencyAndRadiusMultiplyOnceWithoutChangingFiniteResourceModel(){
        var h=mobile();h.link("potency",PassiveSlot.PASSIVE02);h.link("expanded_radius",PassiveSlot.PASSIVE03);h.cast();
        assertEquals(1.15*.9*.8,h.context.snapshot().modifiers().factor(),1e-9);assertEquals(6.25,h.presented.getFirst().radius());assertEquals(76,h.mana);
    }
    @Test void attachmentUsesReleasePositionNotOldCommittedPoint(){
        var h=mobile();h.anchor=new Vec3(20,0,0);h.cast();assertEquals(Vec3.ZERO,h.context.target().point());assertEquals(h.anchor,h.presented.getFirst().origin());
    }
    @Test void followsOwnerWithoutLeavingDamageTrailOrRetainingOldOrigin(){
        var h=mobile();h.targets=List.of(target("old",0,0),target("trail",10,0),target("new",20,0));h.cast();h.step(.25);
        h.anchor=new Vec3(20,0,0);h.step(.5);assertEquals(List.of("old","new"),h.hitIds);assertEquals(h.anchor,h.payloads.getLast().origin());
        assertEquals(h.anchor,h.presented.getLast().origin());
    }
    @Test void attachedVerticalGeometryAlsoFollowsWithoutExpandingHeight(){
        var h=mobile();h.cast();h.anchor=new Vec3(0,5,0);h.targets=List.of(target("floor",0,0),target("high",0,5));h.step(.25);
        assertEquals(List.of("high"),h.hitIds);assertEquals(3,h.presented.getLast().height());
    }
    @Test void staticZoneStaysAtCommittedPositionAndNeverReadsAnchor(){
        var h=new Harness("poison_cloud");h.anchor=new Vec3(20,0,0);h.anchorAvailable=false;h.targets=List.of(target("old",0,0),target("new",20,0));h.cast();h.step(.25);
        assertEquals(List.of("old"),h.hitIds);assertEquals(0,h.anchorReads);assertEquals(Vec3.ZERO,h.presented.getLast().origin());
    }
    @Test void lifetimeAndPerTargetLedgerDoNotRestartWhileMoving(){
        var h=mobile();h.cast();for(int i=1;i<=32;i++){h.anchor=new Vec3(i*.1,0,0);h.targets=List.of(target("same",i*.1,0));h.step(i*.25);}
        assertEquals(32,h.payloads.size());assertEquals(0,h.areaRuntime.size());assertEquals(0,h.areaRuntime.retainedRootCount());assertEquals(0,h.budget.size());
        h.step(9);assertEquals(32,h.payloads.size());assertEquals(1,h.resourceWrites);
    }
    @Test void missingAnchorStopsAndReleasesCapacityWithoutStaleDamage(){
        var h=mobile();h.cast();h.targets=List.of(target("old",0,0));h.anchorAvailable=false;h.step(.25);
        assertTrue(h.payloads.isEmpty());assertEquals(0,h.budget.size());assertTrue(h.reasons.contains("MOBILE_OWNER_ANCHOR_UNAVAILABLE"));
    }
    @Test void wrongWorldAnchorStopsTheOldField(){
        var h=mobile();h.cast();h.anchorWorld=UUID.randomUUID();h.step(.25);assertEquals(0,h.areaRuntime.size());assertEquals(0,h.areaRuntime.retainedRootCount());
    }
    @Test void foreignActorCannotMoveOrRetainTheField(){
        var h=mobile();h.cast();h.anchorActor=UUID.randomUUID();h.step(.25);assertEquals(0,h.areaRuntime.size());assertEquals(0,h.budget.size());
    }
    @Test void invalidStartDoesNotReserveAFieldOrRootBudget(){
        var h=mobile();h.cast();var c=h.context;h.areaRuntime.cancel(h.actor);h.anchorAvailable=false;
        assertThrows(IllegalStateException.class,()->h.areaRuntime.start(c,Vec3.ZERO,Vec3.FORWARD,1,1,h));assertEquals(0,h.budget.size());assertEquals(0,h.areaRuntime.retainedRootCount());
    }
    @Test void nativePortFailureAlsoCleansTheField(){
        var h=mobile();h.cast();h.throwAnchor=true;h.step(.25);assertEquals(0,h.budget.size());assertEquals(0,h.areaRuntime.retainedRootCount());
        assertTrue(h.reasons.contains("NATIVE_ADAPTER_FAILURE_IllegalStateException"));
    }
    @Test void teardownIsIdempotentAndRetainsNoAnchorReference(){
        var h=mobile();h.cast();assertEquals(List.of(h.context),h.areaRuntime.cancel(h.actor));assertTrue(h.areaRuntime.cancel(h.actor).isEmpty());
        h.step(.25);assertEquals(0,h.budget.size());assertTrue(h.payloads.isEmpty());
    }
    @Test void longSimulationGapCannotDamageAnUnobservedPath(){
        var h=mobile();h.cast();h.targets=List.of(target("a",0,0));h.step(2);assertTrue(h.payloads.isEmpty());assertEquals(0,h.areaRuntime.size());assertTrue(h.reasons.contains("SIMULATION_GAP_EXCEEDS_ONE_SECOND"));
    }
    @Test void movingGeometryStillUsesLosAndWholeQueryOverflowGate(){
        var h=mobile();h.cast();h.targets=List.of(target("a",0,0));h.los=false;h.step(.25);assertTrue(h.payloads.isEmpty());
        h.los=true;h.overflow=true;h.step(.5);assertTrue(h.payloads.isEmpty());assertEquals(0,h.areaRuntime.size());
    }
    @Test void vortexRetainsPullRulesWithCurrentMobileOrigin(){
        var h=new Harness("vortex");h.link("mobile_domain",PassiveSlot.PASSIVE01);h.cast();h.anchor=new Vec3(10,0,0);h.targets=List.of(target("a",12,0));h.step(.25);
        var payload=h.payloads.getFirst();assertEquals(h.anchor,payload.origin());assertEquals(1.5*.25,payload.pull());assertEquals(1.5,payload.pullCoreRadius());assertEquals(.8,h.context.snapshot().modifiers().factor());
    }
    @Test void earthquakeRetainsFourDiscreteImpactsAndStaggerRules(){
        var h=new Harness("earthquake");h.link("mobile_domain",PassiveSlot.PASSIVE01);h.targets=List.of(target("a",0,0));h.cast();
        for(int i=1;i<=18;i++)h.step(i*.25);assertEquals(4,h.payloads.size());assertEquals(.4,h.payloads.getFirst().statusSeconds());assertEquals(0,h.areaRuntime.size());
    }
    @Test void lingeringComposesWithMobileWithoutIntroducingDrain(){
        var h=mobile();h.link("lingering",PassiveSlot.PASSIVE02);h.cast();assertEquals(11.2,h.context.profile().area().lifetimeSeconds(),1e-9);
        assertEquals(72,h.mana);assertEquals(0,h.reserved);assertEquals(1,h.resourceWrites);assertEquals(.8,h.context.snapshot().modifiers().factor());
    }
    @Test void skillDelayAttachesOnlyOnReleaseAndPreservesIdentifiers(){
        var h=mobile();h.link("skill_delay",PassiveSlot.PASSIVE02);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());assertEquals(0,h.areaRuntime.size());
        h.anchor=new Vec3(25,0,0);h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(h.anchor,h.presented.getFirst().origin());
        assertEquals(1.35*.8,h.context.snapshot().modifiers().factor(),1e-9);assertEquals("stage09",h.context.request().correlationId());assertEquals(1,h.resourceWrites);
        h.targets=List.of(target("a",25,0));h.step(2.25);assertSame(h.context,h.hitContexts.getFirst());
    }
    @Test void echoKeepsOwnFiniteInstanceAndInheritedMobileMagnitudeWithoutRepayment(){
        var h=mobile();h.link("echo",PassiveSlot.PASSIVE02);assertEquals(SkillExecutionResult.Status.COMMITTED,h.cast().status());var primary=h.context;
        h.anchor=new Vec3(10,0,0);h.now=.45;h.execution.tickScheduled(h.actor,h);var echo=h.context;
        assertTrue(echo.echo());assertEquals(2,h.areaRuntime.size());assertEquals(primary.rootCastId(),echo.rootCastId());assertNotEquals(primary.skillInstanceId(),echo.skillInstanceId());
        assertEquals(.8*.7,echo.snapshot().modifiers().factor(),1e-9);assertEquals(.8,primary.snapshot().modifiers().factor());assertEquals(1,h.resourceWrites);
        h.areaRuntime.cancel(h.actor);assertEquals(0,h.budget.size());assertEquals(0,h.areaRuntime.retainedRootCount());
    }
    @Test void immediateMobileRequiresWorldCaptureBeforePayment(){
        var h=mobile();h.captureAvailable=false;var result=h.cast();assertFalse(result.committed());assertTrue(result.code().startsWith("TARGET_CAPTURE_FAILED_"));
        assertEquals(100,h.mana);assertEquals(0,h.resourceWrites);assertEquals(0,h.areaRuntime.size());
    }
    @Test void runtimeRejectsUnsafeProfileEvenWithForgedMobileContext(){
        var h=mobile();h.cast();var c=h.context;h.areaRuntime.cancel(h.actor);
        var unsafe=new SkillExecutionContext(c.request(),c.rootCastId(),c.skillInstanceId(),f.profiles.require("blizzard"),c.compiledPlan(),c.snapshot(),c.equipment(),c.target(),false);
        assertEquals("MOBILE_FINITE_ZONE_COMPONENT_REQUIRED",assertThrows(IllegalStateException.class,()->h.areaRuntime.start(unsafe,Vec3.ZERO,Vec3.FORWARD,1,1,h)).getMessage());assertEquals(0,h.budget.size());
    }

    private static Harness mobile(){var h=new Harness("poison_cloud");h.link("mobile_domain",PassiveSlot.PASSIVE01);return h;}
    private static AreaWorldPort.Target target(String id,double x,double y){return Stage06AreaRuntimeTest.target(id,x,y);}
    static final class Harness extends Stage09SupportRuntimeTest.Harness implements AreaWorldPort {
        final AreaRuntime areaRuntime=new AreaRuntime(budget);
        Vec3 anchor=Vec3.ZERO;UUID anchorActor=actor,anchorWorld=world;
        boolean anchorAvailable=true,captureAvailable=true,throwAnchor,overflow,los=true;int anchorReads,resourceWrites;
        List<Target> targets=List.of();final List<String> hitIds=new ArrayList<>(),reasons=new ArrayList<>();
        final List<Payload> payloads=new ArrayList<>();final List<SkillExecutionContext> hitContexts=new ArrayList<>();final List<AreaGeometry> presented=new ArrayList<>();
        Harness(String id){super(id);}
        @Override public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest request){return captureAvailable?super.captureTarget(p,plan,request):null;}
        @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
        @Override public SkillExecutionResult executeArea(SkillExecutionContext c){context=c;areaRuntime.start(c,c.target()==null?Vec3.ZERO:c.target().point(),c.target()==null?Vec3.FORWARD:c.target().direction(),now,c.compiledPlan().executionModifiers().radiusFactor(),this);return SkillExecutionResult.committed("FIXTURE_AREA",0,0);}
        @Override public void setCurrent(com.inigmasgames.hytalerpg.combat.resource.ResourceType type,double value){super.setCurrent(type,value);resourceWrites++;}
        void step(double to){now=to;areaRuntime.tick(actor,to,this);}
        public Optional<OwnerAnchor> ownerAnchor(SkillExecutionContext c){anchorReads++;if(throwAnchor)throw new IllegalStateException("fixture");return anchorAvailable?Optional.of(new OwnerAnchor(anchorActor,anchorWorld,anchor)):Optional.empty();}
        public Query query(AreaGeometry shape,int budget){return new Query(targets,overflow);}
        public boolean lineOfSight(Vec3 origin,Target target){return los;}
        public boolean apply(SkillExecutionContext c,Target t,Payload p){hitContexts.add(c);hitIds.add(t.id());payloads.add(p);return true;}
        public void present(SkillExecutionContext c,AreaGeometry shape,String phase,double seconds){presented.add(shape);}
        @Override public void trace(SkillExecutionContext c,String event,Map<String,?> details){super.trace(c,event,details);if(details.containsKey("reason"))reasons.add(details.get("reason").toString());}
    }
}
