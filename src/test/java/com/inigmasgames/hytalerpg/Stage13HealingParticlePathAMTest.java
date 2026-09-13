package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingParticlePathAMTest {
    static JsonObject json(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    @Test void derivativesRetainExactInstalledChildAppearanceAndOnlyChangePlacementAndEmissionBudgets()throws Exception{
        try(var zip=new ZipFile(Path.of(System.getenv("APPDATA"),"Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            for(String name:List.of("Sparks","Glow","Plus")){
                var entry=zip.getEntry("Server/Particles/_Test/HealBeams/Spawners/Beam_Heal_Green2_"+name+".particlespawner");
                JsonObject stock;try(var in=zip.getInputStream(entry)){stock=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                var derived=json(Path.of("src/main/resources/Server/Particles/RPG/HealingPath/RPG_Heal_Path_"+name+".particlespawner"));
                assertEquals(stock.get("Particle"),derived.get("Particle"));
                assertEquals(stock.get("ParticleLifeSpan"),derived.get("ParticleLifeSpan"));
                if(!name.equals("Sparks")){assertEquals(stock.get("MaxConcurrentParticles"),derived.get("MaxConcurrentParticles"));assertEquals(stock.get("SpawnRate"),derived.get("SpawnRate"));}
                var permitted=Set.of("InitialVelocity","TrailSpawnerPositionMultiplier","TrailSpawnerRotationMultiplier","EmitOffset","MaxConcurrentParticles","SpawnRate");
                for(var e:stock.entrySet())if(!permitted.contains(e.getKey()))assertEquals(e.getValue(),derived.get(e.getKey()),name+"/"+e.getKey());
                assertTrue(stock.keySet().containsAll(derived.keySet().stream().filter(k->!permitted.contains(k)).toList()));
                assertEquals(0,derived.get("TrailSpawnerPositionMultiplier").getAsInt());assertEquals(0,derived.get("TrailSpawnerRotationMultiplier").getAsInt());
                var speed=derived.getAsJsonObject("InitialVelocity").getAsJsonObject("Speed");assertEquals(0,speed.get("Min").getAsDouble());assertEquals(0,speed.get("Max").getAsDouble());
                for(String axis:List.of("X","Y","Z")){var offset=derived.getAsJsonObject("EmitOffset").getAsJsonObject(axis);assertEquals(0,offset.get("Min").getAsDouble());assertEquals(0,offset.get("Max").getAsDouble());}
            }
        }
    }
    @Test void systemsPairTheExactGlowAndCrossWithNoAutonomousGroupMotion()throws Exception{
        for(String kind:List.of("Blips","Pulse")){
            var system=json(Path.of("src/main/resources/Server/Particles/RPG/HealingPath/RPG_Heal_Path_"+kind+".particlesystem"));
            var groups=system.getAsJsonArray("Spawners");assertEquals(kind.equals("Blips")?1:2,groups.size());
            var expected=kind.equals("Blips")?List.of("Sparks"):List.of("Glow","Plus");
            for(int i=0;i<groups.size();i++){var group=groups.get(i).getAsJsonObject();assertEquals("RPG_Heal_Path_"+expected.get(i),group.get("SpawnerId").getAsString());assertFalse(group.has("InitialVelocity"));assertEquals(1,group.get("TotalSpawners").getAsInt());}
            var model=json(Path.of("src/main/resources/Server/Models/RPG/RPG_Heal_Path_"+kind+".json"));
            var p=model.getAsJsonArray("Particles").get(0).getAsJsonObject();assertEquals(1,p.get("Scale").getAsInt());assertFalse(p.get("DetachedFromModel").getAsBoolean());assertTrue(p.get("ClearParticlesOnRemove").getAsBoolean());
        }
    }
    @Test void straightPathsHaveExactEndpointsBoundedDenseSamplingAndArcLength(){
        for(double length:new double[]{0,2,6,12,18,25.2,1000}){
            var a=new Vec3(4,83,-9);var b=a.add(new Vec3(length,0,0));var path=new HealingParticlePath(new ElasticBeamTether().update(a,b,0));
            assertEquals(length,path.length(),1e-8);assertEquals(a,path.at(-1));assertEquals(b,path.at(length+1));
            assertTrue(path.blips()<=128);if(length>0&&length<=25.2)assertTrue(path.length()/(path.blips()-1)<=.2+1e-8);
            for(int i=0;i<=20;i++)assertEquals(a.x()+length*i/20,path.at(length*i/20).x(),1e-8);
        }
    }
    @Test void smoothElasticPathKeepsSourceAndTargetAndHasIndependentHistory(){
        var motion=new ElasticBeamTether();var independent=new ElasticBeamTether();var a=Vec3.ZERO;var b=new Vec3(18,0,0);motion.update(a,b,0);independent.update(a,b,0);
        var knots=motion.update(new Vec3(0,1,0),new Vec3(18,1,0),.05);var path=new HealingParticlePath(knots);
        assertEquals(knots.getFirst(),path.at(0));assertEquals(knots.getLast(),path.at(path.length()));assertTrue(path.at(path.length()/2).y()<1);
        var other=new HealingParticlePath(independent.update(a,b,.05));assertEquals(0,other.at(9).y());
        for(int i=1;i<100;i++){double arc=path.length()*i/100;assertTrue(path.at(arc).subtract(path.at(arc-path.length()/100)).length()<=path.length()/100+1e-8);}
    }
    @Test void allAppearancePlacementLivesOutsideGameplayAndNoSolidRendererIsSelected()throws Exception{
        var owner=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingTetherPresentation.java"));
        assertTrue(owner.contains("new SplineHealingParticleVisuals()"));assertFalse(owner.contains("new NativeHealingBeamVisuals()"));
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/SplineHealingParticleVisuals.java"));
        assertFalse(source.contains("BeamComponent.spawn"));assertFalse(source.contains("spawnParticleEffect"));
        assertEquals(2048,SplineHealingParticleVisuals.MAX_ANCHORS);assertEquals(6,SplineHealingParticleVisuals.MAX_SEGMENTS);assertEquals(3,HealingParticlePath.PULSES);assertEquals(6,HealingParticlePath.PULSE_SPEED);
    }
}
