package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingPolishAPTest {
    @Test void bodyAndPulsesChangeOnlyColorFromWorkingAo()throws Exception{
        for(String name:List.of("Sparks","Glow","Plus")){
            var before=json("Server/Particles/RPG/HealingWorld/RPG_Heal_World_"+name+".particlespawner");
            var after=json("Server/Particles/RPG/HealingRed/RPG_Heal_Red_"+name+".particlespawner");
            assertNotEquals(before,after);stripColors(before);stripColors(after);assertEquals(before,after);
        }
    }
    @Test void recipientAndStaffRetainExactShippedNonColorBehavior()throws Exception{
        try(var zip=new ZipFile(Path.of(System.getenv("APPDATA"),"Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            for(var pair:List.of(new String[]{"Status_Effect/Heal/Spawners/Heal2","Heal2"},new String[]{"Status_Effect/Heal/Spawners/Heal_Rays","Heal_Rays"},
                    new String[]{"Weapon/Staff/Spawners/Staff_Bronze_Air","Staff_Air"},new String[]{"Weapon/Staff/Spawners/Staff_Bronze_Sparks","Staff_Sparks"})){
                JsonElement before;try(var in=zip.getInputStream(zip.getEntry("Server/Particles/"+pair[0]+".particlespawner"))){before=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
                var after=json("Server/Particles/RPG/HealingRed/RPG_Heal_Red_"+pair[1]+".particlespawner");
                stripColors(before);stripColors(after);assertEquals(before,after);
            }
        }
    }
    @Test void everyProductionParticleUsesRedPaletteWithoutChangingSprites()throws Exception{
        for(String child:List.of("Sparks","Glow","Plus","Heal2","Heal_Rays","Staff_Air","Staff_Sparks")){
            var asset=json("Server/Particles/RPG/HealingRed/RPG_Heal_Red_"+child+".particlespawner");
            var colors=new ArrayList<String>();colors(asset,colors);assertFalse(colors.isEmpty());
            for(String hex:colors){int r=Integer.parseInt(hex.substring(1,3),16),g=Integer.parseInt(hex.substring(3,5),16),b=Integer.parseInt(hex.substring(5,7),16);assertTrue(r>g&&r>b,hex);}
        }
        assertEquals("RPG_Heal_Red_Blips",SplineHealingParticleVisuals.BLIPS);assertEquals("RPG_Heal_Red_Pulse",SplineHealingParticleVisuals.PULSE);
    }
    @Test void helixIsOneBoundedStrandWithPinnedEndsAndCenterlinePulses(){
        var f=new HealingWorldParticleFrame();f.update(Vec3.ZERO,new Vec3(18,0,0),0);
        assertEquals(60,f.count());assertEquals(Vec3.ZERO,f.sample(0));assertEquals(new Vec3(18,0,0),f.sample(f.bodyCount()-1));
        for(int i=1;i<f.bodyCount()-1;i++){
            var p=f.sample(i);double expected=HealingHelix.RADIUS*Math.min(1,Math.min(p.x(),18-p.x())/.5);
            assertEquals(expected,Math.hypot(p.y(),p.z()),1e-9);
            assertEquals(expected*Math.cos(p.x()/HealingHelix.PITCH*2*Math.PI),p.y(),1e-8);
        }
        for(int i=f.bodyCount();i<f.count();i++){assertEquals(0,f.sample(i).y());assertEquals(0,f.sample(i).z());}
    }
    @Test void bodyActuallyFlowsForwardAndRotatesWhilePulsesKeepSixMetresPerSecond(){
        var f=new HealingWorldParticleFrame();f.update(Vec3.ZERO,new Vec3(18,0,0),0);var first=f.sample(5);
        f.update(Vec3.ZERO,new Vec3(18,0,0),.101);var second=f.sample(5);
        assertEquals(.202,second.x()-first.x(),1e-8);assertTrue(Math.hypot(second.y()-first.y(),second.z()-first.z())>.01);
        assertEquals(.606,f.sample(f.bodyCount()).x(),1e-8);assertEquals(0,f.sample(f.bodyCount()).y());
    }
    @Test void parallelTransportDoesNotFlipAtWorldUpOrExactReversal(){
        var tangent=new Vec3(0,0,1);var normal=new Vec3(1,0,0);
        for(int i=1;i<=720;i++){
            double angle=i*Math.PI/360;var next=new Vec3(0,Math.sin(angle),Math.cos(angle));
            var transported=HealingHelix.transport(normal,tangent,next);
            assertEquals(1,transported.length(),1e-9);assertTrue(transported.distanceSquared(normal)<1e-8);
            normal=transported;tangent=next;
        }
        assertEquals(normal,HealingHelix.transport(normal,tangent,tangent.multiply(-1)));
    }
    @Test void audioAssetsAreLoopOnlyCosmeticsAndNeverPersistedByTheOwner()throws Exception{
        for(int i=0;i<8;i++){
            var effect=json("Server/Entity/Effects/RPG/RPG_Healing_Audio_"+i+".json").getAsJsonObject();
            assertEquals(Set.of("Infinite","Debuff","ApplicationEffects"),effect.keySet());assertTrue(effect.get("Infinite").getAsBoolean());
            assertEquals(Set.of("LocalSoundEventId"),effect.getAsJsonObject("ApplicationEffects").keySet());
            assertEquals("SFX_Deployable_Totem_Heal_Effect_Local",effect.getAsJsonObject("ApplicationEffects").get("LocalSoundEventId").getAsString());
        }
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingChannelAudio.java"));
        assertFalse(source.contains("addEffect("));assertFalse(source.contains("addInfiniteEffect("));assertTrue(source.contains("if(sessions.containsKey(root))return"));
        assertTrue(source.contains("EffectOp.Remove:EffectOp.Add"));
    }
    @Test void recipientUsesShippedHealChildrenAndRemainsIndependentOfHealth()throws Exception{
        var system=json("Server/Particles/RPG/HealingRed/RPG_Heal_Red_Recipient.particlesystem").getAsJsonObject();
        var groups=system.getAsJsonArray("Spawners");assertEquals(2,groups.size());
        assertEquals("RPG_Heal_Red_Heal2",groups.get(0).getAsJsonObject().get("SpawnerId").getAsString());
        assertEquals("RPG_Heal_Red_Heal_Rays",groups.get(1).getAsJsonObject().get("SpawnerId").getAsString());
        var effect=json("Server/Entity/Effects/RPG/RPG_Healing_Recipient.json").getAsJsonObject();
        assertEquals(.3,effect.get("Duration").getAsDouble());assertFalse(effect.has("StatModifiers"));
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingParticleVisuals.java"));
        assertFalse(source.contains("actualHealing"));assertTrue(source.contains("releaseRecipient(root.store,id)"));assertTrue(source.contains("audio.stop(key)"));
    }
    private static JsonElement json(String path)throws Exception{return JsonParser.parseString(Files.readString(Path.of("src/main/resources",path)));}
    private static void stripColors(JsonElement e){if(e.isJsonObject()){e.getAsJsonObject().remove("Color");for(var v:e.getAsJsonObject().entrySet())stripColors(v.getValue());}else if(e.isJsonArray())for(var v:e.getAsJsonArray())stripColors(v);}
    private static void colors(JsonElement e,List<String> out){if(e.isJsonObject()){for(var v:e.getAsJsonObject().entrySet())if(v.getKey().equals("Color"))out.add(v.getValue().getAsString());else colors(v.getValue(),out);}else if(e.isJsonArray())for(var v:e.getAsJsonArray())colors(v,out);}
}
