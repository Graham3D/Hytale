package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.vfx.AreaPresentationTemplate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06HardeningTest {
    @Test void ninetySixApexRootsAtMaximumOwnerDensityAndQueryBudgetExpireWithoutOrphans() {
        var runtime=new AreaRuntime();var seed=Stage06AreaRuntimeTest.context("void_cataclysm");
        var owners=new ArrayList<UUID>();var contexts=new ArrayList<SkillExecutionContext>();
        var targets=new ArrayList<AreaWorldPort.Target>();
        for(int i=0;i<256;i++) targets.add(new AreaWorldPort.Target("target-"+i,
                new AreaGeometry.Bounds(new Vec3(-15,0,-15),new Vec3(15,3,15)),false));
        var port=new CountingPort(targets);long begin=System.nanoTime();
        for(int i=0;i<12;i++) {
            UUID owner=new UUID(6,i);owners.add(owner);
            for(int j=0;j<8;j++) {
                var context=copy(seed,owner,"load-"+i+"-"+j,"root-"+i+"-"+j);contexts.add(context);
                runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1.25,port);
            }
            assertEquals("OWNER_FIELD_BUDGET",runtime.admission(owner,"void_cataclysm",false));
        }
        assertEquals(96,runtime.size());assertEquals(96,runtime.retainedRootCount());
        for(int step=1;step<=170;step++)for(var owner:owners)runtime.tick(owner,step*.05,port);
        assertEquals(96*256*6,port.hits); // Five root-capped sub-hits plus one independent final hit.
        assertEquals(0,runtime.size());assertEquals(0,runtime.retainedRootCount());
        for(var owner:owners)assertTrue(runtime.cancel(owner).isEmpty());
        double millis=(System.nanoTime()-begin)/1e6;
        System.out.printf(java.util.Locale.ROOT,"STAGE06_LOAD {\"roots\":96,\"owners\":12,\"targetsPerQuery\":256,\"simulationSeconds\":8.5,\"nativeClient\":false,\"hits\":%d,\"queries\":%d,\"presentationCalls\":%d,\"traceCalls\":%d,\"elapsedMillis\":%.3f,\"retainedFields\":0,\"retainedRoots\":0}%n",port.hits,port.queries,port.visuals,port.traces,millis);
        assertTrue(millis<15000,"Generous regression ceiling; not a connected/native frame-time claim: "+millis);
    }
    @Test void finiteFieldBudgetsRejectWithoutEvictionAndMalformedStartCannotLeakRoot() {
        var runtime=new AreaRuntime();var seed=Stage06AreaRuntimeTest.context("root_snare");var port=new CountingPort(List.of());
        assertThrows(RuntimeException.class,()->runtime.start(seed,null,Vec3.FORWARD,0,1,port));
        assertEquals(0,runtime.retainedRootCount());
        for(int i=0;i<AreaRuntime.GLOBAL_CAP;i++)runtime.start(copy(seed,new UUID(2,i),"field-"+i,"root-"+i),Vec3.ZERO,Vec3.FORWARD,0,1,port);
        assertEquals("GLOBAL_FIELD_BUDGET",runtime.admission(UUID.randomUUID(),"root_snare",true));
        assertEquals(128,runtime.size());
        for(int i=0;i<AreaRuntime.GLOBAL_CAP;i++)runtime.cancel(new UUID(2,i));
        assertEquals(0,runtime.size());assertEquals(0,runtime.retainedRootCount());
    }
    @Test void sharedRootSpawnBudgetAndDuplicateInstanceCannotBeBypassed() {
        var runtime=new AreaRuntime();var seed=Stage06AreaRuntimeTest.context("blizzard");var port=new CountingPort(List.of());
        runtime.start(seed,Vec3.ZERO,Vec3.FORWARD,0,1,port); // Field plus 11 child impacts = 12.
        assertThrows(IllegalStateException.class,()->runtime.start(seed,Vec3.ZERO,Vec3.FORWARD,0,1,port));
        runtime.start(copy(seed,seed.request().actorId(),"second",seed.rootCastId()),Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.start(copy(seed,seed.request().actorId(),"third",seed.rootCastId()),Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.start(copy(seed,seed.request().actorId(),"fourth",seed.rootCastId()),Vec3.ZERO,Vec3.FORWARD,0,1,port);
        var error=assertThrows(IllegalStateException.class,()->runtime.start(copy(seed,seed.request().actorId(),"fifth",seed.rootCastId()),Vec3.ZERO,Vec3.FORWARD,0,1,port));
        assertEquals("ROOT_SPAWN_EFFECT_BUDGET",error.getMessage());assertEquals(4,runtime.size());
        runtime.cancel(seed.request().actorId());assertEquals(0,runtime.retainedRootCount());
    }
    @Test void diagnosticSinkFailureCannotLeakFieldsOrPreventOtherOwnersCleanup() {
        var runtime=new AreaRuntime();var seed=Stage06AreaRuntimeTest.context("blizzard");
        var port=new CountingPort(List.of()) { @Override public void trace(SkillExecutionContext c,String e,Map<String,?> d){throw new IllegalStateException("sink failed");} };
        assertThrows(IllegalStateException.class,()->runtime.start(seed,Vec3.ZERO,Vec3.FORWARD,0,1,port));
        assertEquals(0,runtime.size());assertEquals(0,runtime.retainedRootCount());
    }
    @Test void claimedReleaseIsInvalidatedByOwnerTeardownAndRejectsInvalidClock() {
        var scheduler=new SkillReleaseScheduler();var c=Stage06AreaRuntimeTest.context("frost_nova");
        var mods=new CompiledSkillPlan.ExecutionModifiers(1,2,0,0,false);
        assertEquals("PASS",scheduler.reserve(c.skillInstanceId(),c.request().actorId(),SkillSlot.SKILL01,mods));
        assertThrows(IllegalArgumentException.class,()->scheduler.arm(c,Double.NaN));
        scheduler.arm(c,-4);assertTrue(scheduler.due(c.request().actorId(),-2.01).isEmpty());
        var release=scheduler.due(c.request().actorId(),-2).getFirst();assertTrue(scheduler.isCurrent(release));
        assertTrue(scheduler.due(c.request().actorId(),0).isEmpty());scheduler.cancel(c.request().actorId());
        assertFalse(scheduler.isCurrent(release));assertEquals(0,scheduler.size());
    }
    @Test void templateDimensionsAreAuthoritativeAndWarningIsNotOnlyAColorChange() {
        assertTrue(AreaPresentationTemplate.warning("WARNING_CORE"));assertTrue(AreaPresentationTemplate.warning("ARMING"));
        assertFalse(AreaPresentationTemplate.warning("ARMED"));assertFalse(AreaPresentationTemplate.warning("IMPACT_CORE"));
        var footprint=Stage06AreaRuntimeTest.profile("cold_wave").area().footprint(Vec3.ZERO,Vec3.FORWARD,1.25);
        assertEquals(.1,AreaOutline.segments(footprint).getFirst().from().y(),1e-12);
        assertEquals(15,footprint.radius());assertEquals(2.5,footprint.height());
        assertNotEquals(AreaPresentationTemplate.color("FIRE","ACTIVE"),AreaPresentationTemplate.color("COLD","ACTIVE"));
        assertNotEquals(AreaPresentationTemplate.color("FIRE","ACTIVE"),AreaPresentationTemplate.color("FIRE","IMPACT_CORE"));
    }
    private static SkillExecutionContext copy(SkillExecutionContext seed,UUID owner,String instance,String root) {
        var s=seed.snapshot();var snapshot=new CombatSnapshot(root,instance,owner,s.rawAttributes(),s.effectiveAttributes(),s.derivedStats(),
                s.itemId(),s.weaponClass(),s.basePowerSource(),s.basePower(),s.compiledPlanHash(),s.skillCoefficient(),s.criticalChance(),s.criticalMultiplier(),s.modifiers(),s.resourceCost(),s.cooldownSeconds(),s.statusModifiers());
        return new SkillExecutionContext(new SkillExecutionRequest(owner,SkillSlot.SKILL01,"load-fixture",6,"load-correlation",Vec3.FORWARD),
                root,instance,seed.profile(),seed.compiledPlan(),snapshot,null);
    }
    private static class CountingPort implements AreaWorldPort {
        final List<Target> targets;int hits,queries,visuals,traces;
        CountingPort(List<Target> targets){this.targets=List.copyOf(targets);}
        public java.util.Optional<Vec3> sweepShard(Vec3 from,Vec3 to,double radius){return from.y()>0&&to.y()<=radius?java.util.Optional.of(new Vec3(to.x(),0,to.z())):java.util.Optional.empty();}
        public Query query(AreaGeometry shape,int budget){queries++;return new Query(targets,false);}
        public boolean lineOfSight(Vec3 origin,Target target){return true;}
        public boolean apply(SkillExecutionContext context,Target target,Payload payload){hits++;return true;}
        public void present(SkillExecutionContext context,AreaGeometry shape,String phase,double seconds){visuals++;}
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){traces++;}
    }
}
