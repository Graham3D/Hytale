package com.inigmasgames.hytalerpg;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage11CascadeTest {
    final Stage11FoundationTest f=new Stage11FoundationTest();
    static class H extends Stage09SupportRuntimeTest.Harness implements AreaWorldPort {
        final AreaRuntime areas=new AreaRuntime(budget);Vec3 anchor=Vec3.ZERO;boolean ground=true,throwGround;int writes,preSpentSecondaries;
        final List<AreaGeometry> shapes=new ArrayList<>();final List<SkillExecutionContext> shown=new ArrayList<>(),hits=new ArrayList<>();
        final List<Payload> payloads=new ArrayList<>();final List<String> reasons=new ArrayList<>();List<Target> targets=List.of();
        H(String skill,String...passives){super(skill);for(int i=0;i<passives.length;i++)link(passives[i],PassiveSlot.values()[i]);}
        void start(){var r=cast();if(r.status()==SkillExecutionResult.Status.PENDING)r=execution.completeWindup(actor,this);assertEquals(SkillExecutionResult.Status.COMMITTED,r.status());}
        void step(double t){now=t;areas.tick(actor,t,this);}
        public Optional<OwnerAnchor> ownerAnchor(SkillExecutionContext c){return Optional.of(new OwnerAnchor(actor,world,anchor));}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){return Validation.pass();}
        public SkillExecutionResult executeArea(SkillExecutionContext c){context=c;for(int i=0;i<preSpentSecondaries;i++)assertEquals("PASS",c.effects().claim("prior"+i,1,true));preSpentSecondaries=0;areas.start(c,Vec3.ZERO,Vec3.FORWARD,now,c.compiledPlan().executionModifiers().radiusFactor(),this);return SkillExecutionResult.committed("AREA_FIXTURE",0,0);}
        public void setCurrent(com.inigmasgames.hytalerpg.combat.resource.ResourceType type,double value){super.setCurrent(type,value);writes++;}
        public Query query(AreaGeometry s,int cap){return new Query(targets,false);}
        public boolean lineOfSight(Vec3 point,Target t){return true;}
        public Optional<AreaGeometry> prepareImpact(Vec3 parent,AreaGeometry shape){if(throwGround)throw new IllegalStateException("fixture terrain");return ground?Optional.of(shape):Optional.empty();}
        public boolean apply(SkillExecutionContext c,Target t,Payload p){hits.add(c);payloads.add(p);return true;}
        public void present(SkillExecutionContext c,AreaGeometry s,String phase,double seconds){shown.add(c);shapes.add(s);}
        public void trace(SkillExecutionContext c,String event,Map<String,?> data){super.trace(c,event,data);if(data.containsKey("reason"))reasons.add(data.get("reason").toString());}
    }
    H h(String...mods){return new H("poison_cloud",mods);}
    @Test void positiveGroundRadialAndExplicitNegativeGates(){for(String id:List.of("blizzard","poison_cloud","vortex","earthquake","meteor","comet","avalanche","void_cataclysm"))assertTrue(f.accepts(id,"cascade"),id);for(String id:List.of("corpse_burst","bone_cage","frost_nova","root_snare","wall_of_fire","managuard","fire_bolt"))assertFalse(f.accepts(id,"cascade"),id);}
    @Test void primaryAndTwoChildrenShareOneResourcePayment(){var h=h("cascade");h.start();assertEquals(3,h.areas.size());assertEquals(76,h.mana);assertEquals(1,h.writes);assertEquals(3,h.context.effects().spawned());assertEquals(2,h.context.effects().triggered());}
    @Test void childrenUseBaseRadiusAimRightOffsetsAndSixtyPercentRadius(){var h=h("cascade");h.start();assertEquals(List.of(Vec3.ZERO,new Vec3(-6,0,0),new Vec3(6,0,0)),h.shapes.stream().map(AreaGeometry::origin).toList());assertEquals(List.of(5d,3d,3d),h.shapes.stream().map(AreaGeometry::radius).toList());}
    @Test void inheritedChildMagnitudeFortyFivePercentAndSameLifetime(){var h=h("cascade","potency");h.start();for(var c:h.shown.stream().filter(c->c.derivedRelease()).toList()){assertEquals(1.15*.45,c.snapshot().modifiers().factor(),1e-9);assertEquals(8,c.profile().area().lifetimeSeconds());assertEquals(h.context.rootCastId(),c.rootCastId());assertEquals(h.context.request().correlationId(),c.request().correlationId());assertSame(h.context.effects(),c.effects());assertSame(h.context.leechBudget(),c.leechBudget());}}
    @Test void scaledRadiusDoesNotAlsoScaleAuthoredOffset(){var h=h("cascade","expanded_radius","concentration");h.start();assertEquals(5*1.25*.7,h.shapes.getFirst().radius(),1e-9);assertEquals(5*1.25*.7*.6,h.shapes.get(1).radius(),1e-9);assertEquals(-6,h.shapes.get(1).origin().x());assertEquals(3,h.shapes.get(1).height());}
    @Test void primaryProfileRemainsImmutable(){var h=h("cascade");var original=h.profiles.require("poison_cloud");h.start();assertSame(original,h.context.profile());assertEquals(5,original.area().radius());}
    @Test void childFootprintCanHitItsOwnTargetsWithoutHittingOutside(){var h=h("cascade");h.targets=List.of(Stage06AreaRuntimeTest.target("left",-6,0),Stage06AreaRuntimeTest.target("right",6,0),Stage06AreaRuntimeTest.target("outside",10,0));h.start();h.step(.25);assertEquals(2,h.payloads.size());assertEquals(Set.of("cascade"),h.hits.stream().map(SkillExecutionContext::secondaryKind).collect(java.util.stream.Collectors.toSet()));}
    @Test void noLegalTerrainKeepsPrimaryAndDoesNotCreateChildren(){var h=h("cascade");h.ground=false;h.start();assertEquals(1,h.areas.size());assertEquals(2,h.reasons.stream().filter("CASCADE_NO_LEGAL_TERRAIN"::equals).count());assertEquals(1,h.writes);}
    @Test void terrainAdapterFailureDoesNotRefundOrCancelPrimary(){var h=h("cascade");h.throwGround=true;h.start();assertEquals(1,h.areas.size());assertEquals(76,h.mana);assertEquals(2,h.reasons.stream().filter("CASCADE_CHILD_REJECTED"::equals).count());}
    @Test void availableFieldCapacityLimitsChildrenWithoutExceedingEight(){var h=h("cascade");for(int i=0;i<7;i++)h.budget.reserve(h.actor,"occupied"+i);h.start();assertEquals(8,h.budget.size());assertEquals(1,h.areas.size());assertEquals(2,h.reasons.stream().filter("CASCADE_CHILD_REJECTED"::equals).count());}
    @Test void sharedSecondaryBudgetCanRejectChildrenWithoutFreePrimary(){var h=h("cascade");h.preSpentSecondaries=16;h.start();assertEquals(1,h.areas.size());assertEquals(16,h.context.effects().triggered());assertEquals(2,h.reasons.stream().filter("CASCADE_CHILD_REJECTED"::equals).count());assertEquals(76,h.mana);}
    @Test void areaChildrenCannotRepeatControllers(){var h=h("cascade","echo");h.start();var root=h.context;assertThrows(IllegalStateException.class,()->root.cascadeCopy(1).cascadeCopy(1));assertThrows(IllegalStateException.class,()->root.cascadeCopy(1).echoCopy());assertThrows(IllegalStateException.class,()->root.echoCopy().cascadeCopy(1));}
    @Test void cascadeChildrenDoNotRecursivelyCascade(){var h=h("cascade");h.start();for(int i=1;i<=32;i++)h.step(i*.25);assertEquals(0,h.areas.size());assertEquals(0,h.budget.size());assertEquals(0,h.areas.retainedRootCount());assertEquals(3,h.context.effects().spawned());}
    @Test void echoAddsOneFieldNotTwoMoreCascades(){var h=h("cascade","echo");h.start();var root=h.context;h.now=.45;h.execution.tickScheduled(h.actor,h);assertEquals(4,h.areas.size());assertTrue(h.context.echo());assertSame(root.effects(),h.context.effects());assertEquals(4,root.effects().spawned());assertEquals(2,root.effects().triggered());assertEquals(1,h.writes);}
    @Test void mobileChildrenRetainOffsetsWhenOwnerMoves(){var h=h("cascade","mobile_domain");h.anchor=new Vec3(20,0,0);h.start();assertEquals(List.of(20d,14d,26d),h.shapes.stream().map(s->s.origin().x()).toList());h.shapes.clear();h.anchor=new Vec3(30,0,0);h.step(.25);assertEquals(List.of(30d,24d,36d),h.shapes.stream().map(s->s.origin().x()).toList());}
    @Test void forcedOwnerCancelReleasesAllChildrenAndLedgers(){var h=h("cascade");h.start();assertEquals(3,h.areas.cancel(h.actor).size());assertEquals(0,h.budget.size());assertEquals(0,h.areas.retainedRootCount());h.step(1);assertTrue(h.payloads.isEmpty());}
    @Test void allChildrenRetainDurationAndPulseCadenceUnderLingering(){var h=h("cascade","lingering");h.start();assertEquals(11.2,h.context.profile().area().lifetimeSeconds(),1e-9);for(int i=1;i<=44;i++)h.step(i*.25);assertEquals(3,h.areas.size());h.step(11.2);assertEquals(0,h.areas.size());assertEquals(1,h.writes);}
    @Test void sharedStatusIcdPreventsOverlapMultiplyingPoisonApplications(){var h=h("cascade");h.targets=List.of(Stage06AreaRuntimeTest.target("overlap",3,0));h.start();h.step(.25);assertEquals(2,h.payloads.size());assertEquals(1,h.payloads.stream().filter(p->p.status().equals("POISON")).count());h.step(.5);assertEquals(1,h.payloads.stream().filter(p->p.status().equals("POISON")).count());}
    @Test void finiteBudgetCapsBlizzardImpactsWithoutDroppingEitherChildField(){var h=new H("blizzard","cascade");h.start();assertEquals(3,h.areas.size());for(int i=1;i<=34;i++)h.step(i*.25);assertEquals(48,h.context.effects().spawned());assertEquals(2,h.context.effects().triggered());assertTrue(h.reasons.contains("ROOT_SPAWN_EFFECT_BUDGET"));assertEquals(0,h.areas.size());}
    @Test void derivedControllersDoNotStartAgainWhenOwnerKeepsTicking(){var h=h("cascade");h.start();for(int i=1;i<=100;i++)h.step(i*.25);assertEquals(0,h.budget.size());assertEquals(1,h.writes);assertEquals(3,h.context.effects().spawned());}
    @Test void childSnapshotRetainsDelayAndResourceAuthority(){var h=h("cascade","skill_delay");h.start();assertEquals(0,h.areas.size());assertEquals(76,h.mana);h.now=2;h.execution.tickScheduled(h.actor,h);assertEquals(3,h.areas.size());assertEquals(1.35*.45,h.shown.get(1).snapshot().modifiers().factor(),1e-9);assertEquals(1,h.writes);}
}
