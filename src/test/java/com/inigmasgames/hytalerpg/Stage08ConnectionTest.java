package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.*;
import com.inigmasgames.hytalerpg.combat.balance.*;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.power.*;
import com.inigmasgames.hytalerpg.combat.resource.*;
import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.connection.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Engine-neutral execution proof, explicitly not client input, native damage or rendering evidence. */
class Stage08ConnectionTest {
    @Test void pilotProfilesRetainCatalogAndExactAuthoredGeometry() {
        var catalog=Stage01BTestSupport.bundle().catalog();var p=Stage04SkillProfiles.loadCanonical(catalog);
        assertEquals(87,catalog.skills().size());assertEquals(66,catalog.passives().size());
        assertEquals(Stage04SkillProfiles.EXPECTED_STAGE08_PROFILES,p.all().values().stream().filter(x->x.connection()!=null).count());
        var wave=p.require("wind_cutter");assertEquals(Stage04SkillProfile.Family.LINE,wave.family());
        assertEquals(16,wave.connection().range());assertEquals(1.2,wave.connection().width());assertEquals(2.5,wave.connection().height());
        assertEquals(.3,wave.connection().depth());assertEquals(20,wave.connection().speed());assertEquals(.8,wave.connection().lifetimeSeconds());assertEquals(.95,wave.damageCoefficient());
        var beam=p.require("void_beam");assertEquals("NONE",beam.resourceType());assertEquals(0,beam.resourceCost());assertEquals(2,beam.cooldownSeconds());
        assertEquals(.25,beam.connection().intervalSeconds());assertEquals(.45,beam.damageCoefficient());assertEquals(4,beam.connection().upkeepPerSecond());assertEquals(22,beam.connection().range());
        var orb=p.require("ball_lightning");assertEquals(18,orb.resourceCost());assertEquals(10,orb.cooldownSeconds());assertEquals(18,orb.connection().range());
        assertEquals(5,orb.connection().speed());assertEquals(5,orb.connection().lifetimeSeconds());assertEquals(.75,orb.connection().intervalSeconds());assertEquals(1.8,orb.connection().radius());
    }
    @Test void lineUsesFullWidthAndActualBoundsInsteadOfPointCenters() {
        var shape=ConnectionShape.line(new Vec3(0,1.25,0),new Vec3(0,1.25,16),1.2,2.5);
        assertTrue(shape.intersects(bounds(.69,1,8,.1)));assertFalse(shape.intersects(bounds(.71,1,8,.1)));
        assertTrue(shape.intersects(bounds(0,2.59,8,.1)));assertFalse(shape.intersects(bounds(0,2.61,8,.1)));
        assertFalse(shape.intersects(bounds(0,1,16.11,.1)));assertFalse(shape.intersects(bounds(0,1,-.11,.1)));
    }
    @Test void diagonalAndVerticalPrismsDoNotBecomeAxisAlignedBoxes() {
        var shape=ConnectionShape.line(Vec3.ZERO,new Vec3(10,0,10),1,1);
        assertTrue(shape.intersects(bounds(5,0,5,.1)));assertFalse(shape.intersects(bounds(0,0,10,.1)));
        var vertical=ConnectionShape.line(Vec3.ZERO,new Vec3(0,10,0),.8,.8);
        assertTrue(vertical.intersects(bounds(0,5,0,.1)));assertFalse(vertical.intersects(bounds(.6,5,0,.1)));
        assertTrue(ConnectionShape.line(Vec3.ZERO,Vec3.ZERO,1,1).intersects(bounds(0,0,0,.1)));
    }
    @Test void cylinderHeightAndRadialCornerSemanticsAreIndependent() {
        var shape=ConnectionShape.cylinder(new Vec3(0,1.5,0),1.8,3);
        assertTrue(shape.intersects(bounds(1.85,1.5,0,.1)));assertFalse(shape.intersects(bounds(1.9,1.5,1.9,.1)));
        assertFalse(shape.intersects(bounds(0,3.11,0,.1)));
    }
    @Test void windFrontTravelsAtTwentyAndHitsEachVictimOnce() {
        var h=new Harness("wind_cutter");h.targets.add(target("victim",0,1.25,8));assertTrue(h.cast().committed());
        h.advance(.3);assertEquals(0,h.hits.size());h.advance(.4);assertEquals(1,h.hits.size());
        h.advance(.41);h.advance(.8);assertEquals(1,h.hits.size());assertEquals(.95,h.hits.getFirst().coefficient());
        assertFalse(h.hits.getFirst().periodic());assertEquals(0,h.runtime.size());assertEquals(192,h.mana);assertEquals(1,h.resourceWrites);
    }
    @Test void waveStopsAtWallAndRejectsTargetsBehindIt() {
        var h=new Harness("wind_cutter");h.wallZ=6;h.targets.add(target("before",0,1.25,4));h.targets.add(target("behind",0,1.25,8));
        h.cast();h.advance(.5);assertEquals(List.of("before"),h.hits.stream().map(Hit::id).toList());assertEquals(List.of("WAVE_BLOCKED"),h.ends);
    }
    @Test void beamPaysEveryQuarterSecondBeforeDamageAndEndsAtFiveSeconds() {
        var h=beam();assertTrue(h.cast().committed());assertEquals(0,h.hits.size());assertEquals(200,h.mana);assertEquals(0,h.resourceWrites);
        assertTrue(h.kernel.cooldowns().canActivate(h.owner,"void_beam"));
        assertEquals(SkillInstanceLifecycle.Phase.CHANNEL,h.lifecycle.active(h.owner).orElseThrow().phase());
        for(int n=1;n<=100;n++)h.advance(n*.05);
        assertEquals(20,h.hits.size());assertEquals(2.25,h.hits.stream().mapToDouble(Hit::coefficient).sum(),1e-9);
        assertTrue(h.hits.stream().allMatch(Hit::periodic));assertEquals(180,h.mana);assertEquals(20,h.resourceWrites);
        assertEquals(0,h.runtime.size());assertTrue(h.lifecycle.active(h.owner).isEmpty());assertFalse(h.kernel.cooldowns().canActivate(h.owner,"void_beam"));
        assertEquals(1,h.cooldownEndTraces());assertTrue(h.order.stream().filter(s->s.startsWith("hit")).allMatch(s->h.order.indexOf("pay"+s.substring(3))<h.order.indexOf(s)));
    }
    @Test void beamIntegrationDoesNotDependOnWorldTickJitter() {
        var h=beam();h.cast();for(double t:new double[]{.17,.53,.72,1.3,2.1,2.7,3.6,4.2,4.99,5})h.advance(t);
        assertEquals(20,h.hits.size());assertEquals(2.25,h.hits.stream().mapToDouble(Hit::coefficient).sum(),1e-9);assertEquals(180,h.mana);
    }
    @Test void insufficientUpkeepStopsBeforeTheUnpaidHitAndStartsOneCooldown() {
        var h=beam();h.mana=1.5;h.cast();h.advance(.25);h.advance(.5);h.advance(.75);
        assertEquals(1,h.hits.size());assertEquals(.5,h.mana);assertEquals(List.of("INSUFFICIENT_UPKEEP"),h.ends);assertEquals(1,h.cooldownEndTraces());
        h.service.terminate(h.contexts.getFirst(),"duplicate");assertEquals(1,h.cooldownEndTraces());
    }
    @Test void beamSamplesCurrentAimClipsAtSolidAndCannotDamageThroughWalls() {
        var h=beam();h.targets.add(target("side",5,1.35,0));h.wallZ=3;h.cast();h.advance(.25);assertEquals(0,h.hits.size());
        h.aim=new Vec3(1,0,0);h.advance(.5);assertEquals(List.of("side"),h.hits.stream().map(Hit::id).toList());assertEquals(198,h.mana);
    }
    @Test void invalidWorldEquipmentDeathAndControlEndBeforeFurtherUpkeep() {
        for(String reason:List.of("WORLD_CHANGED","COMMITTED_EQUIPMENT_CHANGED","ACTOR_NOT_USABLE","CHANNEL_CONTROL_INTERRUPT","CHANNEL_NATIVE_STUN_INTERRUPT")) {
            var h=beam();h.cast();h.advance(.25);h.validation=reason;h.advance(.5);
            assertEquals(1,h.hits.size(),reason);assertEquals(199,h.mana,reason);assertEquals(List.of(reason),h.ends);assertEquals(1,h.cooldownEndTraces());
        }
    }
    @Test void disconnectCancellationReleasesChannelAndIsIdempotent() {
        var h=beam();h.cast();h.advance(.25);
        for(var c:h.runtime.cancel(h.owner,false))h.service.terminate(c,"disconnect");
        assertTrue(h.runtime.cancel(h.owner,false).isEmpty());h.advance(.5);assertEquals(199,h.mana);assertEquals(1,h.hits.size());
        assertEquals(0,h.capacity.size());assertEquals(1,h.cooldownEndTraces());
    }
    @Test void largeSimulationGapDoesNotRetroactivelyDamageCurrentTargets() {
        var h=beam();h.cast();h.advance(1.01);assertEquals(0,h.hits.size());assertEquals(200,h.mana);assertEquals(List.of("SIMULATION_GAP_EXCEEDS_ONE_SECOND"),h.ends);
    }
    @Test void orbHasSixAuthoredPulsesNoSpawnHitAndStopsAtRange() {
        var h=new Harness("ball_lightning");h.targets.add(new ConnectionWorldPort.Target("large",new AreaGeometry.Bounds(new Vec3(-.1,1,0),new Vec3(.1,1.7,20))));
        h.cast();assertEquals(0,h.hits.size());for(int n=1;n<=100;n++)h.advance(n*.05);
        assertEquals(6,h.hits.size());assertEquals(List.of(1,2,3,4,5,6),h.hits.stream().map(Hit::tick).toList());
        assertEquals(2.1,h.hits.stream().mapToDouble(Hit::coefficient).sum(),1e-9);assertEquals(182,h.mana);assertEquals(1,h.resourceWrites);
        assertEquals(18,h.shapes.stream().mapToDouble(s->s.start().z()).max().orElseThrow(),1e-9);assertEquals(0,h.runtime.size());
    }
    @Test void orbWallStopKeepsPulsingUntilTotalFiveSeconds() {
        var h=new Harness("ball_lightning");h.wallZ=3.5;h.targets.add(target("before",0,1.35,3));h.targets.add(target("behind",0,1.35,4));
        h.cast();for(int n=1;n<=20;n++)h.advance(n*.25);
        assertEquals(6,h.hits.size());assertTrue(h.hits.stream().allMatch(hit->hit.id().equals("before")));
        assertEquals(3.5,h.shapes.stream().mapToDouble(s->s.start().z()).max().orElseThrow(),1e-9);assertEquals(List.of("CONNECTION_EXPIRED"),h.ends);
    }
    @Test void nativeWriteFailureCannotProduceFreeChannelDamage() {
        var h=beam();h.rejectWrites=true;h.cast();h.advance(.25);assertEquals(0,h.hits.size());assertEquals(200,h.mana);assertEquals(List.of("INSUFFICIENT_UPKEEP"),h.ends);
    }
    @Test void duplicateTargetsAndRepeatedSameTimeCannotDoubleDamage() {
        var h=beam();h.targets.add(h.targets.getFirst());h.cast();h.advance(.25);h.advance(.25);h.advance(.1);h.advance(.25);
        assertEquals(1,h.hits.size());assertEquals(199,h.mana);h.advance(.5);assertEquals(2,h.hits.size());
    }
    @Test void candidateOverflowRejectsWholeSliceBeforePayment() {
        var h=beam();h.cast();h.overflow=true;h.advance(.25);assertEquals(0,h.hits.size());assertEquals(200,h.mana);assertEquals(List.of("CANDIDATE_BUDGET_REJECTED"),h.ends);
    }
    @Test void boundedVictimLedgerDoesNotEvictAndPermitRepeatedHits() {
        var h=beam();h.targets.clear();h.cast();
        for(int slice=1;slice<=5;slice++) {h.targets.clear();for(int n=0;n<64;n++)h.targets.add(target("t"+(slice*64+n),0,1.35,5));h.advance(slice*.25);}
        assertEquals(256,h.hits.size());assertEquals(196,h.mana);assertEquals(List.of("TARGET_LEDGER_BUDGET"),h.ends);
    }
    @Test void fractionalUpkeepIsIndependentOfIntegerUpfrontCost() {
        var h=beam();var cost=new ResourceCost(ResourceType.MANA,1.25);var modifiers=new CompiledSkillPlan.KernelModifiers(0,.85,0);
        assertEquals(1.0625,h.kernel.resources().evaluateUpkeep(cost,modifiers).amount());assertEquals(2,h.kernel.resources().evaluate(cost,modifiers).amount());
        assertEquals(0,h.kernel.resources().evaluateUpkeep(cost,new CompiledSkillPlan.KernelModifiers(0,0,0)).amount());
        for(int n=0;n<20;n++) {var token=h.kernel.resources().reserveCost(h.owner,h.kernel.resources().evaluateUpkeep(cost,null),h);h.kernel.resources().commitCost(token,h);h.kernel.resources().finish(token);}
        assertEquals(175,h.mana);assertThrows(IllegalArgumentException.class,()->new ResourceCost(ResourceType.MANA,Double.NaN));
    }
    @Test void sharedFieldCapacityIsNotDuplicatedAcrossAreasAndConnections() {
        var capacity=new OwnedFieldBudget();var areas=new AreaRuntime(capacity);var runtime=new ConnectionRuntime(capacity);var owner=UUID.randomUUID();
        for(int n=0;n<8;n++)capacity.reserve(owner,"field"+n);
        assertEquals("OWNER_FIELD_BUDGET",areas.admission(owner,"blizzard",false));assertEquals("OWNER_FIELD_BUDGET",runtime.admission(owner,false));
        assertTrue(capacity.release(owner,"field0"));assertFalse(capacity.release(owner,"field0"));assertEquals("PASS",runtime.admission(owner,false));
        for(int n=0;n<121;n++)capacity.reserve(new UUID(1,n),"field");assertEquals(128,capacity.size());
        assertEquals("GLOBAL_FIELD_BUDGET",areas.admission(UUID.randomUUID(),"blizzard",false));assertEquals("GLOBAL_FIELD_BUDGET",runtime.admission(UUID.randomUUID(),false));
    }
    @Test void beamRejectsRepeatControllersAndLineRejectsProjectileContinuations() {
        for(String skill:List.of("wind_cutter","void_beam","ball_lightning"))for(String passive:List.of("fork","chain","piercing","return")) {
            var h=new Harness(skill);assertFalse(h.link(passive),skill+"/"+passive);
        }
        for(String passive:List.of("echo","skill_delay")){var h=beam();assertFalse(h.link(passive),passive);}
        assertFalse(beam().bundle.catalog().skill(new SkillId("void_beam")).orElseThrow().canCrit());
    }
    @Test void finiteOrbInheritsExpandedRadiusPotencyAndEchoWithoutExtraCost() {
        var h=new Harness("ball_lightning","expanded_radius","potency","echo");assertTrue(h.cast().committed());h.advance(.45);
        assertEquals(2,h.contexts.size());assertEquals(h.contexts.get(0).rootCastId(),h.contexts.get(1).rootCastId());assertNotEquals(h.contexts.get(0).skillInstanceId(),h.contexts.get(1).skillInstanceId());
        assertTrue(h.shapes.stream().allMatch(s->Math.abs(s.radius()-2.25)<1e-9));assertEquals(.15,h.contexts.getFirst().compiledPlan().kernelModifiers().scalablePayloadIncreased());
        assertEquals(182,h.mana);assertEquals(1,h.resourceWrites);assertTrue(h.contexts.getLast().echo());
    }
    @Test void delayedWaveUsesCommittedAimAndPaysOnlyOnce() {
        var h=new Harness("wind_cutter","skill_delay");assertTrue(h.cast().committed());h.aim=new Vec3(1,0,0);h.advance(1);h.advance(2);
        assertEquals(1,h.contexts.size());assertEquals(Vec3.FORWARD,h.shapes.getFirst().direction());assertEquals(192,h.mana);assertEquals(1,h.resourceWrites);
    }
    static AreaGeometry.Bounds bounds(double x,double y,double z,double half){return new AreaGeometry.Bounds(new Vec3(x-half,y-half,z-half),new Vec3(x+half,y+half,z+half));}
    static ConnectionWorldPort.Target target(String id,double x,double y,double z){return new ConnectionWorldPort.Target(id,bounds(x,y,z,.1));}
    static Harness beam(){var h=new Harness("void_beam");h.targets.add(target("victim",0,1.35,5));return h;}
    record Hit(String id,int tick,double coefficient,boolean periodic){ }
    static final class Harness implements SkillExecutionPort,NativeResourcePort,ConnectionWorldPort {
        final UUID owner=UUID.randomUUID(),world=new UUID(0,1);final AtomicLong clock=new AtomicLong();
        final Stage01BTestSupport.Bundle bundle=Stage01BTestSupport.bundle();
        final Stage01BTestSupport.RecordingTracer tracer=(Stage01BTestSupport.RecordingTracer)bundle.tracer();
        final RpgCombatKernel kernel=new RpgCombatKernel(CombatBalanceProfile.loadCanonical(),new CriticalRoller(()->1));
        final OwnedFieldBudget capacity=new OwnedFieldBudget();final ConnectionRuntime runtime=new ConnectionRuntime(capacity);
        final SkillInstanceLifecycle lifecycle=new SkillInstanceLifecycle();final SkillExecutionService service;final Stage04SkillProfile profile;
        final List<Target> targets=new ArrayList<>();final List<Hit> hits=new ArrayList<>();final List<String> order=new ArrayList<>(),ends=new ArrayList<>();
        final List<ConnectionShape> shapes=new ArrayList<>();final List<SkillExecutionContext> contexts=new ArrayList<>();
        final List<SkillExecutionContext> hitContexts=new ArrayList<>();
        Vec3 aim=Vec3.FORWARD,feet=Vec3.ZERO;double mana=200,wallZ=Double.POSITIVE_INFINITY,health=50,healTotal;int resourceWrites;boolean overflow,rejectWrites;String validation="PASS";
        final Map<String,Double> victimHealth=new HashMap<>();final List<Double> healRequests=new ArrayList<>();
        Harness(String skill,String...passives) {
            assertTrue(bundle.service().equipSkill(owner,SkillSlot.SKILL01,new SkillId(skill)).success());for(String passive:passives)assertTrue(link(passive),passive);
            var profiles=Stage04SkillProfiles.loadCanonical(bundle.catalog());profile=profiles.require(skill);
            service=new SkillExecutionService(bundle.service(),profiles,kernel,SkillExecutorRegistry.stage04(),lifecycle,tracer,clock::get);
        }
        int passiveIndex;
        boolean link(String passive){var slot=PassiveSlot.values()[passiveIndex++];assertTrue(bundle.service().equipPassive(owner,slot,new PassiveId(passive)).success(),passive);return bundle.service().link(owner,LinkNodeId.valueOf(slot.name()),LinkNodeId.SKILL01).success();}
        SkillExecutionResult cast(){return service.request(new SkillExecutionRequest(owner,SkillSlot.SKILL01,"fixture",1,"stage08-fixture",aim),this);}
        void advance(double seconds){clock.set(Math.round(seconds*1e9));runtime.tick(owner,seconds,this);service.tickScheduled(owner,this);}
        long cooldownEndTraces(){return tracer.records.stream().filter(r->r.eventType().name().equals("COOLDOWN_STARTED")).count();}
        public boolean actorAliveAndUsable(){return true;}
        public Equipment equipment(){String kind=profile.allowedMainHandKinds().stream().sorted().findFirst().orElseThrow();return new Equipment(new Item("fixture",kind,new ItemPowerDescriptor("fixture",Set.of(kind),20d,20d)),null);}
        public NativeResourcePort resources(){return this;}
        public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){String verdict=runtime.admission(owner,p.connection().channel());
            if(verdict.equals("PASS")&&p.connection().requiresTarget())verdict=ConnectionTargeting.select(p.connection(),this).verdict();
            return verdict.equals("PASS")?Validation.pass():Validation.reject(verdict);}
        public CommittedTarget captureTarget(Stage04SkillProfile p,CompiledSkillPlan plan,SkillExecutionRequest r){var origin=feet.add(new Vec3(0,p.connection().originHeight(),0));
            var selected=p.connection().requiresTarget()?ConnectionTargeting.select(p.connection(),this).target():null;
            return new CommittedTarget(world,origin,selected==null?origin.add(aim.multiply(p.connection().range())):selected.bounds().centre(),aim,selected==null?null:UUID.fromString(selected.id()));}
        public Validation validateRelease(SkillExecutionContext c){return Validation.pass();}
        public SkillExecutionResult executeConnection(SkillExecutionContext c){contexts.add(c);runtime.start(c,clock.get()/1e9,this);return SkillExecutionResult.committed("CONNECTION_STARTED",0,0);}
        public SkillExecutionResult executeProjectile(SkillExecutionContext c){throw new AssertionError();}
        public SkillExecutionResult executeStrike(SkillExecutionContext c){throw new AssertionError();}public SkillExecutionResult executeMovement(SkillExecutionContext c){throw new AssertionError();}public SkillExecutionResult executeReaction(SkillExecutionContext c){throw new AssertionError();}
        public double current(ResourceType type){return mana;}public double maximum(ResourceType type){return 200;}
        public void setCurrent(ResourceType type,double value){if(!rejectWrites)mana=value;resourceWrites++;}
        public Frame frame(){return new Frame(world,feet,aim);}
        public String validate(SkillExecutionContext c,UUID world){return validation;}
        public Vec3 unobstructedEndpoint(Vec3 from,Vec3 to){if(to.z()>wallZ&&to.z()>from.z())return from.add(to.subtract(from).multiply(Math.max(0,(wallZ-from.z())/(to.z()-from.z()))));return to;}
        public Query query(ConnectionShape shape,int cap){return new Query(targets.stream().filter(t->shape.intersects(t.bounds())).toList(),overflow);}
        public Optional<Target> resolveTarget(String id){return targets.stream().filter(t->t.id().equals(id)).findFirst();}
        public boolean lineOfSight(Vec3 from,Target t){return t.bounds().centre().z()<=wallZ;}
        public boolean payUpkeep(SkillExecutionContext c,int tick,double seconds){
            var cost=kernel.resources().evaluateUpkeep(new ResourceCost(ResourceType.MANA,c.profile().connection().upkeepPerSecond()*seconds),c.compiledPlan().kernelModifiers());
            if(!kernel.resources().canAfford(owner,cost,this))return false;double before=mana;var token=kernel.resources().reserveCost(owner,cost,this);
            try{kernel.resources().commitCost(token,this);order.add("pay"+tick);return Math.abs(before-mana-cost.amount())<1e-9;}finally{kernel.resources().finish(token);}
        }
        public double damage(SkillExecutionContext c,Target target,int tick,double coefficient,boolean periodic){hitContexts.add(c);hits.add(new Hit(target.id(),tick,coefficient,periodic));order.add("hit"+tick);
            double before=victimHealth.getOrDefault(target.id(),10000d),lost=Math.min(before,coefficient*20);victimHealth.put(target.id(),before-lost);return lost;}
        public void healFromDamage(SkillExecutionContext c,int tick,double lost){
            var healing=new com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService().fromActualDamage(lost,c.profile().connection().details().healFraction(),c.snapshot().derivedStats().healingMultiplier(),c.compiledPlan().kernelModifiers().scalablePayloadIncreased());
            healRequests.add(healing.requestedHealing());double before=health;health=Math.min(100,health+healing.requestedHealing());healTotal+=health-before;
        }
        public void present(SkillExecutionContext c,ConnectionShape shape,String phase,double seconds){assertTrue(seconds>0&&seconds<=.25);shapes.add(shape);}
        public void ended(SkillExecutionContext c,String reason){ends.add(reason);service.terminate(c,reason);}
        public void trace(SkillExecutionContext c,String event,Map<String,?> details){ }
    }
}
