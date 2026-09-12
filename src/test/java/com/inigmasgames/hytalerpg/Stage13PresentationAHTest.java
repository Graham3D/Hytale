package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.hytale.HealingParticleVisuals;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13PresentationAHTest {
    static JsonObject json(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    @Test void carrierHasExplicitExistingNativeTextureNotTheFailedInferredEmptyPng()throws Exception{
        var model=json(Path.of("src/main/resources/Server/Models/RPG/RPG_Healing_Stream.json"));
        try(var zip=new ZipFile(Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip").toFile())){
            assertNull(zip.getEntry("Common/NPC/MISC/Empty.png"),"AG connected missing-texture control");
            for(String key:List.of("Model","Texture")){
                var entry=zip.getEntry("Common/"+model.get(key).getAsString());
                assertNotNull(entry,key+" must resolve to real shipped bytes");assertTrue(entry.getSize()>0);
            }
            var nativeModel=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("Server/Models/Projectiles/Abilities/Ground_Slam.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(nativeModel.get("Model"),model.get("Model"));
            assertEquals(nativeModel.get("Texture"),model.get("Texture"));
        }
    }
    @Test void allStaffAttachmentsAreFiniteCosmeticChannelEffectsAndNoPermanentBronzeParticles()throws Exception{
        var mapping=json(Path.of("src/main/resources/rpg/presentation/staff-heads-ag.json"));
        assertEquals(26,mapping.size());var effectIds=new HashSet<String>();
        for(var entry:mapping.entrySet()){
            var id=HealingParticleVisuals.staffEffect(entry.getKey());assertNotNull(id);effectIds.add(id);
            var effect=json(Path.of("src/main/resources/Server/Entity/Effects/RPG/"+id+".json"));
            assertEquals(Set.of("Duration","OverlapBehavior","Debuff","ApplicationEffects"),effect.keySet());
            assertEquals(.3,effect.get("Duration").getAsDouble());assertFalse(effect.get("Debuff").getAsBoolean());
            var application=effect.getAsJsonObject("ApplicationEffects");
            assertEquals(Set.of("Particles"),application.keySet());
            var particles=application.getAsJsonArray("Particles");assertEquals(1,particles.size());
            var p=particles.get(0).getAsJsonObject();
            assertEquals("Staff_Bronze",p.get("SystemId").getAsString());
            assertEquals("PrimaryItem",p.get("TargetEntityPart").getAsString());
            assertEquals(entry.getValue().getAsJsonObject().get("node"),p.get("TargetNodeName"));
            assertTrue(p.get("ClearParticlesOnRemove").getAsBoolean());
            var item=json(Path.of("src/main/resources/Server/Item/Items/Weapon/Staff/"+entry.getKey()+".json"));
            for(var particle:item.getAsJsonArray("Particles"))assertNotEquals("Staff_Bronze",particle.getAsJsonObject().get("SystemId").getAsString());
        }
        assertEquals(4,effectIds.size());assertNull(HealingParticleVisuals.staffEffect("third_party_unknown_staff"));
    }
    @Test void executionParticipatesInNativeStatModificationOrdering(){
        assertTrue(EntityStatsSystems.StatModifyingSystem.class.isAssignableFrom(HytaleSkillExecutionSystem.class));
    }
}
