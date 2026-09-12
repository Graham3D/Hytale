package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionWorldPort;
import com.inigmasgames.hytalerpg.execution.hytale.HealingParticleVisuals;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PresentationAGTest {
    private static JsonObject json(String path)throws Exception{return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
    @Test void legacyReferenceRetainedButProductionUsesEndpointControlledTether()throws Exception{
        var model=json("src/main/resources/Server/Models/RPG/RPG_Healing_Stream.json");
        assertEquals("NPC/MISC/Empty.blockymodel",model.get("Model").getAsString());
        var particles=model.getAsJsonArray("Particles");assertEquals(1,particles.size());
        assertEquals("Beam_Heal_Green2",particles.get(0).getAsJsonObject().get("SystemId").getAsString());
        assertTrue(particles.get(0).getAsJsonObject().get("ClearParticlesOnRemove").getAsBoolean());
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingParticleVisuals.java"));
        assertFalse(source.contains("BeamComponent"));assertFalse(source.contains("SpawnParticleSystem"));
        assertTrue(source.contains("getNonSerializedComponentType"));
        var wiring=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        assertTrue(wiring.contains("new HealingTetherPresentation()"));assertFalse(wiring.contains("new HealingParticleVisuals()"));
        var owner=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingTetherPresentation.java"));
        assertTrue(owner.contains("new NativeHealingBeamVisuals()"));assertTrue(owner.contains("new HealingParticleVisuals(true)"));
    }
    @Test void recipientEffectIsCosmeticFiniteAttachedAndHasNoHealthCondition()throws Exception{
        var effect=json("src/main/resources/Server/Entity/Effects/RPG/RPG_Healing_Recipient.json");
        assertEquals(Set.of("Duration","OverlapBehavior","Debuff","ApplicationEffects"),effect.keySet());
        assertEquals(.3,effect.get("Duration").getAsDouble());assertFalse(effect.get("Debuff").getAsBoolean());
        var effects=effect.getAsJsonObject("ApplicationEffects");assertEquals(Set.of("Particles"),effects.keySet());
        var p=effects.getAsJsonArray("Particles").get(0).getAsJsonObject();
        assertEquals("Effect_Health_Pack",p.get("SystemId").getAsString());assertEquals("Entity",p.get("TargetEntityPart").getAsString());
        assertTrue(p.get("ClearParticlesOnRemove").getAsBoolean());
    }
    @Test void healthyTargetAndEveryContinuationCarryExplicitRecipientIdentity(){
        for(String passive:List.of("","arc","fork","chain")){
            var seen=new ArrayList<ConnectionWorldPort.TetherVisualSegment>();
            var h=new Stage13SupportTetherTest.Healing(passive.isEmpty()?new String[0]:new String[]{passive}){
                @Override public double heal(SkillExecutionContext c,ConnectionWorldPort.Target t,int tick,double coefficient){return 0;}
                @Override public void presentTether(SkillExecutionContext c,List<ConnectionWorldPort.TetherVisualSegment> frame){seen.addAll(frame);}
            };
            h.hp.put(Stage08ConnectionCohortBTest.id(1),100d);
            h.targets.add(Stage08ConnectionCohortBTest.enemy(2,3,1.35,5));h.cast();h.stepTo(.5);
            assertFalse(seen.isEmpty(),passive);
            for(var segment:seen){assertNotNull(segment.recipient());assertFalse(segment.recipient().isBlank());assertTrue(segment.id().endsWith(segment.recipient()));}
        }
    }
    @Test void streamRotationUsesNativeForwardFromCasterTowardRecipient(){
        for(var delta:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,2,-3),new Vec3(0,2,0))){
            var r=HealingParticleVisuals.rotation(Vec3.ZERO,delta).transform(new org.joml.Vector3d(0,0,-1));var d=delta.normalized();
            assertEquals(d.x(),r.x,1e-6);assertEquals(d.y(),r.y,1e-6);assertEquals(d.z(),r.z,1e-6);
        }
    }
    @Test void everyStaffOverridePreservesAllNativeGameplayFieldsAndOtherParticles()throws Exception{
        var zipPath=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new ZipFile(zipPath.toFile());var files=Files.list(Path.of("src/main/resources/Server/Item/Items/Weapon/Staff"))){
            var list=files.filter(p->p.toString().endsWith(".json")).toList();assertEquals(26,list.size());
            for(var path:list){
                var entry="Server/Item/Items/Weapon/Staff/"+path.getFileName();var original=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(entry)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var patched=json(path.toString());var before=original.remove("Particles");var after=patched.remove("Particles").getAsJsonArray();
                assertEquals(original,patched,entry+" gameplay changed");
                var nativeOther=new JsonArray();if(before!=null&&!before.isJsonNull())for(var p:before.getAsJsonArray())if(!"Staff_Bronze".equals(p.getAsJsonObject().get("SystemId").getAsString()))nativeOther.add(p);
                // AH moves the same exact node attachment from permanent item particles to an active-channel effect.
                assertEquals(nativeOther,after,entry);
                var effectId=HealingParticleVisuals.staffEffect(path.getFileName().toString().replace(".json",""));
                var effect=json("src/main/resources/Server/Entity/Effects/RPG/"+effectId+".json");
                var added=effect.getAsJsonObject("ApplicationEffects").getAsJsonArray("Particles").get(0).getAsJsonObject();
                assertEquals("Staff_Bronze",added.get("SystemId").getAsString());assertTrue(added.get("ClearParticlesOnRemove").getAsBoolean());
                assertEquals("PrimaryItem",added.get("TargetEntityPart").getAsString());
                assertEquals(.3,effect.get("Duration").getAsDouble());
                var modelEntry=zip.getEntry("Common/"+original.get("Model").getAsString());
                var model=JsonParser.parseString(new String(zip.getInputStream(modelEntry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var names=new ArrayList<String>();nodes(model.getAsJsonArray("nodes"),names);
                assertEquals(1,Collections.frequency(names,added.get("TargetNodeName").getAsString()),entry+" ambiguous/missing node");
            }
        }
    }
    private static void nodes(JsonArray nodes,List<String> names){if(nodes==null)return;for(var e:nodes){var n=e.getAsJsonObject();names.add(n.get("name").getAsString());nodes(n.getAsJsonArray("children"),names);}}
}
