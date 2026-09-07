package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage08LoadTest {
    @Test void maximumGlobalBeamLoadDrainsSlicesAndReleasesEveryHandle(){
        var source=Stage08ConnectionTest.beam();source.cast();var template=source.contexts.getFirst();source.runtime.cancel(source.owner,false);source.service.terminate(template,"FIXTURE_CAPTURE");
        var capacity=new OwnedFieldBudget();var runtime=new ConnectionRuntime(capacity);var port=new Port();var owners=new ArrayList<UUID>();
        for(int n=0;n<128;n++)owners.add(new UUID(1,n));
        long began=System.nanoTime();for(int n=0;n<128;n++)runtime.start(copy(template,owners.get(n),"beam-"+n),0,port);
        assertEquals("GLOBAL_FIELD_BUDGET",runtime.admission(UUID.randomUUID(),true));
        for(int tick=1;tick<=20;tick++)for(var owner:owners)runtime.tick(owner,tick*.25,port);
        double ms=(System.nanoTime()-began)/1e6;
        assertEquals(128*20*64,port.hits);assertEquals(128*20,port.upkeep);assertEquals(128,port.ends);assertEquals(0,runtime.size());assertEquals(0,capacity.size());
        System.out.println("STAGE08_BEAM_LOAD {\"fields\":128,\"owners\":128,\"victimsPerSlice\":64,\"damageCalls\":"+port.hits+",\"upkeepCalls\":"+port.upkeep+",\"remainingFields\":0,\"remainingCapacity\":0,\"milliseconds\":"+ms+",\"nativeOrConnectedProof\":false}");
    }
    @Test void maximumOwnerAndGlobalOrbitLoadUsesSharedIcdAndReleasesAll(){
        var source=Stage08ConnectionCohortBTest.orbit();source.cast();var template=source.contexts.getFirst();source.runtime.cancel(source.owner,false);
        var capacity=new OwnedFieldBudget();var runtime=new ConnectionRuntime(capacity);var port=new Port();var owners=new ArrayList<UUID>();
        for(int n=0;n<16;n++)owners.add(new UUID(2,n));
        long began=System.nanoTime();for(int n=0;n<128;n++)runtime.start(copy(template,owners.get(n/8),"orbit-"+n),0,port);
        for(int tick=1;tick<=200;tick++)for(var owner:owners)runtime.tick(owner,tick*.05,port);
        double ms=(System.nanoTime()-began)/1e6;
        assertEquals(128*14*64,port.hits);assertEquals(0,port.upkeep);assertEquals(128,port.ends);assertEquals(0,runtime.size());assertEquals(0,capacity.size());
        System.out.println("STAGE08_ORBIT_LOAD {\"fields\":128,\"owners\":16,\"blades\":512,\"victimsPerSlice\":64,\"damageCalls\":"+port.hits+",\"remainingFields\":0,\"remainingCapacity\":0,\"milliseconds\":"+ms+",\"nativeOrConnectedProof\":false}");
    }
    private static SkillExecutionContext copy(SkillExecutionContext source,UUID owner,String id){
        var old=source.snapshot();var request=new SkillExecutionRequest(owner,source.request().slot(),"stage08-load",1,id,Vec3.FORWARD);
        var snapshot=new CombatSnapshot(id,id,owner,old.rawAttributes(),old.effectiveAttributes(),old.derivedStats(),old.itemId(),old.weaponClass(),old.basePowerSource(),old.basePower(),old.compiledPlanHash(),old.skillCoefficient(),old.criticalChance(),old.criticalMultiplier(),old.modifiers(),old.resourceCost(),old.cooldownSeconds(),old.statusModifiers());
        return new SkillExecutionContext(request,id,id,source.profile(),source.compiledPlan(),snapshot,source.equipment());
    }
    /** Bounded in-memory world port; excludes native ECS, physics, trace I/O, network and rendering costs. */
    private static final class Port implements ConnectionWorldPort {
        final List<Target> targets;long hits,upkeep,ends;
        Port(){var list=new ArrayList<Target>();for(int n=0;n<64;n++)list.add(new Target(new UUID(0,n).toString(),new AreaGeometry.Bounds(new Vec3(-4,.8,-4),new Vec3(4,1.5,6))));targets=List.copyOf(list);}
        public Frame frame(){return new Frame(new UUID(0,1),Vec3.ZERO,Vec3.FORWARD);}
        public String validate(SkillExecutionContext context,UUID world){return "PASS";}
        public Vec3 unobstructedEndpoint(Vec3 from,Vec3 to){return to;}
        public Query query(ConnectionShape shape,int cap){return new Query(targets,false);}
        public Query query(List<ConnectionShape> shapes,int cap){return new Query(targets,false);}
        public boolean lineOfSight(Vec3 origin,Target target){return true;}
        public boolean payUpkeep(SkillExecutionContext context,int tick,double seconds){upkeep++;return true;}
        public double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic){hits++;return coefficient*20;}
        public void present(SkillExecutionContext context,ConnectionShape shape,String phase,double seconds){ }
        public void ended(SkillExecutionContext context,String reason){ends++;}
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){ }
    }
}
