package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingBlizzardTest {
    @Test void nativeRotationMapsAuthoredForwardForCardinalsDiagonalsAndVerticalOffsets(){
        var start=new Vec3(13,4,-8);
        for(var delta:List.of(new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(-1,0,0),
                new Vec3(1,0,1),new Vec3(-1,0,1),new Vec3(1,0,-1),new Vec3(-1,0,-1),
                new Vec3(2,7,-3),new Vec3(2,-7,-3),new Vec3(0,2,0),new Vec3(0,-2,0))){
            var end=start.add(delta);var r=NativeBeamTransform.rotation(start,end);
            var rotated=r.transform(new org.joml.Vector3d(0,0,-1));var expected=delta.normalized();
            assertEquals(expected.x(),rotated.x,1e-6);assertEquals(expected.y(),rotated.y,1e-6);assertEquals(expected.z(),rotated.z,1e-6);
            var samples=NativeBeamTransform.samples(start,end);assertFalse(samples.isEmpty());assertTrue(samples.size()<=9);
            for(var point:samples)assertEquals(0,point.subtract(start).length()+end.subtract(point).length()-delta.length(),1e-8);
        }
        assertTrue(NativeBeamTransform.samples(start,start).isEmpty());
    }
    @Test void eachContinuationUsesItsActualSegmentEndpoints(){
        for(String passive:List.of("arc","fork","chain")){
            var segmentShapes=new ArrayList<ConnectionShape>();
            var h=new Stage13SupportTetherTest.Healing(passive){
                @Override public void presentTether(SkillExecutionContext c,List<ConnectionWorldPort.TetherVisualSegment> segments){
                    segments.stream().filter(s->!s.id().startsWith("PRIMARY:")).map(ConnectionWorldPort.TetherVisualSegment::shape).forEach(segmentShapes::add);
                }
            };
            h.targets.add(Stage08ConnectionCohortBTest.enemy(2,3,1.35,5));
            h.targets.add(Stage08ConnectionCohortBTest.enemy(3,-3,2.35,6));
            h.cast();h.stepTo(.5);assertFalse(segmentShapes.isEmpty(),passive);
            for(var shape:segmentShapes){
                var actual=NativeBeamTransform.rotation(shape.start(),shape.end()).transform(new org.joml.Vector3d(0,0,-1));
                var expected=shape.end().subtract(shape.start()).normalized();
                assertEquals(expected.x(),actual.x,1e-6);assertEquals(expected.y(),actual.y,1e-6);assertEquals(expected.z(),actual.z,1e-6);
            }
        }
    }
    @Test void actualHealingAggregationIncludesPartialOverhealAndZeroWithoutChangingPulses(){
        var a=new HealingTextAccumulator();var key=new HealingTextAccumulator.Key(UUID.randomUUID(),UUID.randomUUID(),"root","ally");
        assertTrue(a.add(key,0,80,89).isEmpty());assertTrue(a.add(key,.25,96,100).isEmpty());
        assertTrue(a.flush(key.owner(),null,.49,false).isEmpty());
        var first=a.flush(key.owner(),null,.5,false).getFirst();assertEquals(13,first.actualHealing());assertEquals(2,first.pulses());
        a.add(key,.6,100,100);assertEquals("0",HealingTextAccumulator.text(a.flush(key.owner(),"root",0,true).getFirst().actualHealing()));
        a.add(key,.7,70,73);a.add(key,.9,73,77);
        assertEquals("+7",HealingTextAccumulator.text(a.flush(key.owner(),"root",0,true).getFirst().actualHealing()));assertEquals(0,a.size());
    }
    @Test void visualWindowsIsolateRootsOwnersAndWorldsAndCanFlushOnTeardown(){
        var a=new HealingTextAccumulator();var owner=UUID.randomUUID();var world=UUID.randomUUID();
        var one=new HealingTextAccumulator.Key(owner,world,"one","ally");var two=new HealingTextAccumulator.Key(owner,UUID.randomUUID(),"two","ally");
        a.add(one,0,1,2);a.add(two,0,1,4);
        assertEquals(1,a.flush(owner,"one",0,true).getFirst().actualHealing());assertEquals(1,a.size());
        assertEquals(3,a.flush(owner,null,0,true).getFirst().actualHealing());assertEquals(0,a.size());
    }
    static class Falling implements AreaWorldPort {
        final AreaRuntime runtime=new AreaRuntime();final SkillExecutionContext c=Stage06AreaRuntimeTest.context("blizzard");
        final Map<Integer,Vec3> initial=new LinkedHashMap<>();final Set<Integer> terminated=new HashSet<>();
        final List<Double> impacts=new ArrayList<>(),hitTimes=new ArrayList<>();final List<Vec3> contacts=new ArrayList<>();
        double now,surface=0;boolean ground=true,hugeTarget;int ends;
        void start(){runtime.start(c,Vec3.ZERO,Vec3.FORWARD,0,1,this);}
        void step(double t){now=t;runtime.tick(c.request().actorId(),t,this);}
        public Optional<Vec3> sweepShard(Vec3 from,Vec3 to,double r){
            return ground&&from.y()>=surface&&to.y()<=surface+r?Optional.of(new Vec3(to.x(),surface,to.z())):Optional.empty();
        }
        public Query query(AreaGeometry g,int b){return new Query(hugeTarget?List.of(new Target("giant",new AreaGeometry.Bounds(new Vec3(-20,-1,-20),new Vec3(20,3,20)),false)):List.of(),false);}
        public boolean lineOfSight(Vec3 p,Target t){return true;}
        public boolean apply(SkillExecutionContext c,Target t,Payload p){assertEquals(.38,p.coefficient());assertEquals(1,p.chillStacks());hitTimes.add(now);return true;}
        public void present(SkillExecutionContext c,AreaGeometry g,String phase,double seconds){if(phase.equals("IMPACT")){impacts.add(now);contacts.add(g.origin());}}
        public void shardVisual(SkillExecutionContext c,int index,Vec3 p,boolean terminal){if(terminal)assertTrue(terminated.add(index));else initial.putIfAbsent(index,p);}
        public void endVisuals(SkillExecutionContext c){ends++;}
        public void trace(SkillExecutionContext c,String e,Map<String,?> d){}
    }
    @Test void elevenFallingCarriersImpactWithinThreeSecondsAndKeepRootHitGate(){
        var h=new Falling();h.hugeTarget=true;h.start();for(int i=1;i<=60;i++)h.step(i*.05);
        assertEquals(11,h.initial.size());assertEquals(11,h.impacts.size());assertEquals(.45,h.impacts.getFirst(),1e-8);assertEquals(2.95,h.impacts.getLast(),1e-8);
        assertTrue(h.initial.values().stream().allMatch(p->p.y()==7.5&&p.horizontalLength()<=4+1e-9));
        for(int i=1;i<h.hitTimes.size();i++)assertTrue(h.hitTimes.get(i)-h.hitTimes.get(i-1)>=.75-1e-9);
        assertEquals(1,h.ends);assertEquals(0,h.runtime.size());assertEquals(0,h.runtime.retainedRootCount());
    }
    @Test void roofFirstContactDeterminesImpactAndCannotReachFloor(){
        var h=new Falling();h.surface=4;h.start();for(int i=1;i<=60;i++)h.step(i*.05);
        assertEquals(11,h.contacts.size());assertTrue(h.contacts.stream().allMatch(p->p.y()==4));assertTrue(h.impacts.getFirst()<.45);
    }
    @Test void missingSurfaceAndExpiryNeverManufactureImpact(){
        var h=new Falling();h.ground=false;h.start();for(int i=1;i<=60;i++)h.step(i*.05);
        assertTrue(h.impacts.isEmpty());assertEquals(0,h.runtime.size());
        var late=new Falling();late.start();late.step(2.99);late.step(3.01);assertTrue(late.impacts.isEmpty());assertEquals(0,late.runtime.size());
    }
    @Test void cancellationPreventsAllFutureImpacts(){var h=new Falling();h.start();h.step(.1);assertEquals(1,h.runtime.cancel(h.c.request().actorId()).size());for(int i=2;i<=70;i++)h.step(i*.05);assertTrue(h.impacts.isEmpty());assertEquals(0,h.runtime.retainedRootCount());}
}
