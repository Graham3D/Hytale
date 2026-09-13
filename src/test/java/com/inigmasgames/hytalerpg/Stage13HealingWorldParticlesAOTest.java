package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingWorldParticlesAOTest {
    @Test void packetBurstsKeepExactApprovedAppearanceAndExpireWithinPointTwoSeconds()throws Exception{
        for(String child:List.of("Sparks","Glow","Plus")){
            var am=json("HealingPath/RPG_Heal_Path_"+child+".particlespawner");
            var ao=json("HealingWorld/RPG_Heal_World_"+child+".particlespawner");
            assertEquals(am.get("Particle"),ao.get("Particle"));
            var allowed=Set.of("MaxConcurrentParticles","TotalParticles","LifeSpan","ParticleLifeSpan","SpawnBurst","SpawnRate","WaveDelay");
            for(var e:am.entrySet())if(!allowed.contains(e.getKey()))assertEquals(e.getValue(),ao.get(e.getKey()),child+e.getKey());
            assertTrue(ao.get("SpawnBurst").getAsBoolean());assertEquals(1,ao.get("MaxConcurrentParticles").getAsInt());
            assertEquals(.18,ao.get("LifeSpan").getAsDouble());
            for(String bound:List.of("Min","Max")){
                assertEquals(1,ao.getAsJsonObject("TotalParticles").get(bound).getAsInt());
                assertEquals(.18,ao.getAsJsonObject("ParticleLifeSpan").get(bound).getAsDouble());
                assertEquals(0,ao.getAsJsonObject("WaveDelay").get(bound).getAsInt());
                assertEquals(0,ao.getAsJsonObject("InitialVelocity").getAsJsonObject("Speed").get(bound).getAsInt());
            }
        }
    }
    @Test void systemsUseOneSparkAndPairedGlowPlusFiniteAtNativeCullDistance()throws Exception{
        for(String kind:List.of("Blips","Pulse")){
            var system=json("HealingWorld/RPG_Heal_World_"+kind+".particlesystem");
            assertEquals(.18,system.get("LifeSpan").getAsDouble());assertEquals(30,system.get("CullDistance").getAsDouble());
            var expected=kind.equals("Blips")?List.of("Sparks"):List.of("Glow","Plus");
            var groups=system.getAsJsonArray("Spawners");assertEquals(expected.size(),groups.size());
            for(int i=0;i<groups.size();i++){
                var group=groups.get(i).getAsJsonObject();assertEquals("RPG_Heal_World_"+expected.get(i),group.get("SpawnerId").getAsString());
                assertEquals(1,group.get("TotalSpawners").getAsInt());assertEquals(1,group.get("MaxConcurrent").getAsInt());assertFalse(group.has("InitialVelocity"));
            }
        }
    }
    @Test void cappedSamplingStillPinsBothEndpointsAcrossAllExtents(){
        for(double length:new double[]{0,2,6,18,25.2,1000}){
            var frame=new HealingWorldParticleFrame();var start=new Vec3(3,4,5);var end=start.add(new Vec3(length,0,0));
            assertTrue(frame.update(start,end,0));assertTrue(frame.bodyCount()<=64);assertTrue(frame.count()<=67);
            if(length==0){assertEquals(0,frame.count());continue;}
            assertEquals(start,frame.sample(0));assertEquals(end,frame.sample(frame.bodyCount()-1));
            assertEquals(frame.bodyCount()+3,frame.count());
            for(int i=0;i<frame.bodyCount();i++)assertEquals(3+length*i/(frame.bodyCount()-1),frame.sample(i).x(),1e-8);
            if(length<=18)assertTrue(length/(frame.bodyCount()-1)<=.325);
            else assertEquals(64,frame.bodyCount());
        }
    }
    @Test void cadenceNeverCatchesUpOrEmitsMoreThanTenPassesPerSecond(){
        var frame=new HealingWorldParticleFrame();int passes=0;double last=-1;
        for(int i=0;i<1000;i++){
            double now=i/1000d;
            if(frame.update(Vec3.ZERO,new Vec3(18,0,0),now)){passes++;if(last>=0)assertTrue(now-last>=.1-1e-12);last=now;}
        }
        assertEquals(10,passes);assertTrue(frame.update(Vec3.ZERO,new Vec3(18,0,0),20));
        assertFalse(frame.update(Vec3.ZERO,new Vec3(18,0,0),20));assertFalse(frame.update(Vec3.ZERO,new Vec3(18,0,0),19));
        assertThrows(IllegalArgumentException.class,()->frame.update(Vec3.ZERO,Vec3.ZERO,Double.NaN));
    }
    @Test void threePulsesAdvanceAtSixMetresPerSecondAndWrapWithoutReverseTravel(){
        var frame=new HealingWorldParticleFrame();frame.update(Vec3.ZERO,new Vec3(2,0,0),0);
        for(int i=1;i<=100;i++){
            double time=i*.101;assertTrue(frame.update(Vec3.ZERO,new Vec3(2,0,0),time));
            for(int j=0;j<3;j++)assertEquals((2d*j/3+6*time)%2,frame.pulseArc(j),1e-8);
        }
    }
    @Test void branchesHaveIndependentElasticStateAndResetByNewObject(){
        var a=new HealingWorldParticleFrame();var b=new HealingWorldParticleFrame();
        a.update(Vec3.ZERO,new Vec3(18,0,0),0);b.update(Vec3.ZERO,new Vec3(18,0,0),0);
        a.update(new Vec3(0,1,0),new Vec3(18,1,0),.101);b.update(Vec3.ZERO,new Vec3(18,0,0),.101);
        assertEquals(1,a.sample(0).y());assertEquals(1,a.sample(a.bodyCount()-1).y());
        assertTrue(a.sample(a.bodyCount()+1).y()<1);assertEquals(0,b.sample(b.bodyCount()+1).y());
        for(int i=2;i<80;i++)a.update(new Vec3(0,1,0),new Vec3(18,1,0),i*.101);
        assertEquals(1,a.sample(a.bodyCount()+1).y(),1e-6);
    }
    @Test void viewFilteringUsesThreeDimensionalParticleAndNativeViewDistance(){
        assertTrue(SplineHealingParticleVisuals.withinView(Vec3.ZERO,new Vec3(30,0,0),64));
        assertFalse(SplineHealingParticleVisuals.withinView(Vec3.ZERO,new Vec3(30.01,0,0),64));
        assertFalse(SplineHealingParticleVisuals.withinView(Vec3.ZERO,new Vec3(0,31,0),64));
        assertFalse(SplineHealingParticleVisuals.withinView(Vec3.ZERO,new Vec3(12,0,0),10));
        assertFalse(SplineHealingParticleVisuals.withinView(Vec3.ZERO,Vec3.ZERO,0));
    }
    @Test void productionHasNoCarrierConstructionOrGlobalBroadcast()throws Exception{
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/SplineHealingParticleVisuals.java"));
        for(String forbidden:List.of("spawnCarrier(","ModelComponent","BeamComponent","addEntity(","Visible_AN","SpawnParticleEffect","Universe.get()"))assertFalse(source.contains(forbidden),forbidden);
        assertTrue(source.contains("ref.getStore()!=store"));assertTrue(source.contains("getPlayerRefs()"));assertTrue(source.contains("withinView(position"));
        assertEquals(2048,SplineHealingParticleVisuals.MAX_ANCHORS);assertEquals(512,SplineHealingParticleVisuals.MAX_ROOTS);assertEquals(6,SplineHealingParticleVisuals.MAX_SEGMENTS);
        assertEquals(.18f,HealingWorldParticleFrame.LIFETIME);
    }
    private static JsonObject json(String name)throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Particles/RPG",name))).getAsJsonObject();}
}
