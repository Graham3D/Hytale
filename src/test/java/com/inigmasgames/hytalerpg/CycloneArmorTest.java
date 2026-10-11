package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.execution.support.CycloneArmorFormula;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.execution.support.SupportMagnitude;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CycloneArmorTest {
    @Test void exactIntrinsicScalingAndInvalidInputs(){
        assertEquals(150,CycloneArmorFormula.capacity(100,1),1e-12);
        assertEquals(157.5,CycloneArmorFormula.capacity(100,5),1e-12);
        assertEquals(166.875,CycloneArmorFormula.capacity(100,10),1e-12);
        assertEquals(176.25,CycloneArmorFormula.capacity(100,15),1e-12);
        assertEquals(185.625,CycloneArmorFormula.capacity(100,20),1e-12);
        assertEquals(195,CycloneArmorFormula.capacity(100,25),1e-12);
        assertThrows(IllegalArgumentException.class,()->CycloneArmorFormula.capacity(-1,1));
        assertThrows(IllegalArgumentException.class,()->CycloneArmorFormula.capacity(100,0));
    }

    @Test void stableSavedIdentityIsTheOnlyLearnableCycloneEntry(){
        var catalog=Stage01BTestSupport.bundle().catalog();
        var skill=catalog.skill(new SkillId("spirit_shield")).orElseThrow();
        assertEquals("Cyclone Armor",skill.name());
        assertTrue(skill.aliases().contains("cyclone_armor"));
        assertTrue(catalog.skill(new SkillId("cyclone_armor")).isEmpty());
        assertEquals(1,catalog.skills().stream().filter(s->s.name().equals("Cyclone Armor")).count());
        assertTrue(skill.linkCompatibilityTags().contains("ABSORBS_DAMAGE"));
        assertTrue(skill.linkCompatibilityTags().contains("CAN_SHARE"));
        assertFalse(skill.linkCompatibilityTags().contains("DAMAGE_REDIRECT"));
        var profile=com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles.loadCanonical(catalog).require("spirit_shield");
        assertTrue(profile.allowedMainHandKinds().isEmpty());
        assertEquals(0,profile.support().range());
        assertEquals(10,profile.support().durationSeconds());
        assertEquals(18,profile.resourceCost());
        assertEquals(14,profile.cooldownSeconds());
    }

    @Test void paidBarrierAbsorbsOnceAndSpillsWithoutAllyTransfer(){
        var h=new Stage09BarrierSupportTest.Harness("spirit_shield");
        assertTrue(h.cast().committed());
        assertEquals(82,h.mana);
        assertEquals(30.9,SupportMagnitude.shield(h.context,1),1e-9);
        var first=h.runtime.finite().shieldHit(h.world,h.actor,10,true,0,(effect,amount)->fail("No ally damage transfer"));
        assertEquals(0,first.remainder());assertEquals(20.9,h.shield(),1e-9);
        var second=h.runtime.finite().shieldHit(h.world,h.actor,50,true,0,(effect,amount)->fail("No ally damage transfer"));
        assertEquals(29.1,second.remainder(),1e-9);assertEquals(0,h.runtime.finite().size());
    }

    @Test void rejectionExpiryRecastAndWardAreBounded(){
        var rejected=new Stage09BarrierSupportTest.Harness("spirit_shield");rejected.mana=17;
        assertFalse(rejected.cast().committed());assertEquals(17,rejected.mana);assertEquals(0,rejected.runtime.finite().size());
        var h=new Stage09BarrierSupportTest.Harness("spirit_shield");h.link("reflective_ward",PassiveSlot.PASSIVE01);
        assertTrue(h.cast().committed());assertEquals(30.9*.8,h.shield(),1e-9);
        var hit=h.runtime.finite().shieldHit(h.world,h.actor,5,false,0,(effect,amount)->fail());
        assertEquals(1,SupportMagnitude.wardReflection(h.context,hit.absorbed()),1e-9);
        assertEquals(1,h.runtime.finite().size());
        h.runtime.finite().applyShield(h.context,java.util.List.of(h.actor),10,20,1);
        assertEquals(20,h.shield(),1e-9);assertEquals(1,h.runtime.finite().size());
        assertTrue(h.runtime.finite().forTarget(h.world,h.actor,11).isEmpty());
        h.runtime.finite().forget(h.actor);assertEquals(0,h.runtime.finite().size());
    }

    @Test void entityBoundBurstAssetUsesOneFiniteEmitter() throws Exception {
        var base=Path.of("src/main/resources/Server");
        var spawner=JsonParser.parseString(Files.readString(base.resolve("Particles/RPG/Wind_Swoosh_CycloneArmor.particlespawner"))).getAsJsonObject();
        assertTrue(spawner.get("SpawnBurst").getAsBoolean());
        assertEquals(3,spawner.getAsJsonObject("SpawnRate").get("Max").getAsInt());
        assertEquals(.4,spawner.getAsJsonObject("WaveDelay").get("Min").getAsDouble(),1e-9);
        assertTrue(spawner.getAsJsonObject("EmitOffset").getAsJsonObject("Y").get("Max").getAsDouble()>1.5);
        var system=JsonParser.parseString(Files.readString(base.resolve("Particles/RPG/RPG_Cyclone_Armor.particlesystem"))).getAsJsonObject();
        assertEquals(1,system.getAsJsonArray("Spawners").size());
        var effect=JsonParser.parseString(Files.readString(base.resolve("Entity/Effects/RPG/RPG_Cyclone_Armor.json"))).getAsJsonObject();
        assertEquals(10,effect.get("Duration").getAsInt());
        var particle=effect.getAsJsonObject("ApplicationEffects").getAsJsonArray("Particles").get(0).getAsJsonObject();
        assertEquals("Self",particle.get("TargetEntityPart").getAsString());
        assertTrue(particle.get("ClearParticlesOnRemove").getAsBoolean());
        var nativeZip=Path.of(System.getProperty("user.home"),
                "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
        try(var zip=new java.util.zip.ZipFile(nativeZip.toFile())){
            var source=zip.getEntry("Server/Particles/Combat/Abilities/Windstrike/Spawners/Wind_Swoosh.particlespawner");
            assertNotNull(source);
            var original=JsonParser.parseString(new String(zip.getInputStream(source).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var duplicate=spawner.deepCopy();
            for(var key:java.util.List.of("EmitOffset","SpawnBurst","SpawnRate","WaveDelay")){
                original.remove(key);duplicate.remove(key);
            }
            assertEquals(original,duplicate,"The mod-owned spawner must retain the native appearance and physics fields");
            assertNotNull(zip.getEntry("Common/Particles/Textures/Basic/Ball7.png"));
        }
    }

}
