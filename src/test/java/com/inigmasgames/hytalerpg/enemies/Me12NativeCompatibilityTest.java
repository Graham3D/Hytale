package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Me12NativeCompatibilityTest {
    static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void installedModernProjectileEffectsAndRemovalRemainPinnedWhileBirthsAreClosed()throws Exception{
        var bindings=EnemyNativeBindings.load();
        try(var zip=new ZipFile(Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip").toFile())){
            for(var id:List.of("Goblin_Turret","Scarak_Seeker")){
                assertTrue(bindings.role(id).isEmpty());
                assertEquals("NATIVE_BINDING_NOT_CERTIFIED",bindings.productionEligibilityRejection(id).orElseThrow());
            }
            var seeker=read(zip,"Server/Item/Interactions/NPCs/Flying_Beast/Scarak_Seeker/Scarak_Seeker_Spit_Projectile.json");
            assertEquals("Projectile",seeker.get("Type").getAsString());
            assertEquals("Projectile_Config_Scarak_Seeker_Spitball",seeker.get("Config").getAsString());
            var seekerWindup=read(zip,"Server/Item/Interactions/NPCs/Flying_Beast/Scarak_Seeker/Scarak_Seeker_Spit.json");
            assertEquals("Scarak_Seeker_Spit_Projectile",seekerWindup.get("Next").getAsString());
            var seekerRoot=read(zip,"Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Flying_Beast/Scarak_Seeker/Scarak_Seeker_Spit.json");
            assertEquals("Scarak_Seeker_Spit",seekerRoot.getAsJsonArray("Interactions").get(0).getAsString());
            var config=read(zip,"Server/ProjectileConfigs/NPCs/Beast/Scarak_Seeker/Projectile_Config_Scarak_Seeker_Spitball.json");
            var hit=config.getAsJsonObject("Interactions").getAsJsonObject("ProjectileHit").getAsJsonArray("Interactions");
            var damage=hit.get(0).getAsJsonObject();
            assertEquals(Set.of("Poison"),damage.getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage").keySet());
            assertEquals(10,damage.getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage").get("Poison").getAsInt());
            assertEquals(0.1,damage.getAsJsonObject("DamageCalculator").get("RandomPercentageModifier").getAsDouble(),1e-9);
            assertEquals("SFX_Scarak_Seeker_Spitball_Death",damage.getAsJsonObject("DamageEffects").get("WorldSoundEventId").getAsString());
            assertEquals("Impact_Poison",damage.getAsJsonObject("DamageEffects").getAsJsonArray("WorldParticles").get(0).getAsJsonObject().get("SystemId").getAsString());
            var condition=damage.getAsJsonObject("Next");
            assertEquals("EffectCondition",condition.get("Type").getAsString());
            assertEquals("Antidote",condition.getAsJsonArray("EntityEffectIds").get(0).getAsString());
            assertEquals("None",condition.get("Match").getAsString());
            assertEquals("ClearEntityEffect",condition.getAsJsonObject("Next").get("Type").getAsString());
            assertEquals("Poison_T1",condition.getAsJsonObject("Next").get("EntityEffectId").getAsString());
            assertEquals("ApplyEffect",condition.getAsJsonObject("Next").getAsJsonObject("Next").get("Type").getAsString());
            assertEquals("Poison_T1",condition.getAsJsonObject("Next").getAsJsonObject("Next").get("EffectId").getAsString());
            assertEquals("RemoveEntity",hit.get(1).getAsJsonObject().get("Type").getAsString());
            assertEquals("User",hit.get(1).getAsJsonObject().get("Entity").getAsString());
            var seekerMiss=config.getAsJsonObject("Interactions").getAsJsonObject("ProjectileMiss").getAsJsonArray("Interactions");
            assertEquals("Simple",seekerMiss.get(0).getAsJsonObject().get("Type").getAsString());
            assertEquals("SFX_Scarak_Seeker_Spitball_Death",seekerMiss.get(0).getAsJsonObject().getAsJsonObject("Effects").get("WorldSoundEventId").getAsString());
            assertEquals("Impact_Poison",seekerMiss.get(0).getAsJsonObject().getAsJsonObject("Effects").getAsJsonArray("WorldParticles").get(0).getAsJsonObject().get("SystemId").getAsString());
            assertEquals("RemoveEntity",seekerMiss.get(1).getAsJsonObject().get("Type").getAsString());
            assertEquals("User",seekerMiss.get(1).getAsJsonObject().get("Entity").getAsString());
            var turret=read(zip,"Server/ProjectileConfigs/NPCs/Intelligent/Goblin/Projectile_Config_Goblin_Turret_Trash.json");
            var turretThrow=read(zip,"Server/Item/Interactions/NPCs/Intelligent/Goblin/Goblin_Turret/Goblin_Turret_Trash_Throw.json");
            assertEquals("Projectile_Config_Goblin_Turret_Trash",turretThrow.getAsJsonObject("Next").get("Config").getAsString());
            var turretRoot=read(zip,"Server/Item/RootInteractions/NPCs/Intelligent/Goblin/Root_NPC_Goblin_Turret_Attack.json");
            assertEquals("Goblin_Turret_Trash_Throw",turretRoot.getAsJsonArray("Interactions").get(0).getAsString());
            var trash=turret.getAsJsonObject("Interactions").getAsJsonObject("ProjectileHit").getAsJsonArray("Interactions");
            assertEquals("ApplyEffect",trash.get(0).getAsJsonObject().get("Type").getAsString());
            assertEquals("Stone_Death",trash.get(0).getAsJsonObject().get("EffectId").getAsString());
            assertEquals(8,trash.get(1).getAsJsonObject().getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage").get("Physical").getAsInt());
            var effects=trash.get(1).getAsJsonObject().getAsJsonObject("DamageEffects");
            var impulse=effects.getAsJsonObject("Knockback");
            assertEquals("Point",impulse.get("Type").getAsString());
            assertEquals(3,impulse.get("Force").getAsInt());
            assertEquals(0.3,impulse.get("VelocityY").getAsDouble(),1e-9);
            assertEquals("Add",impulse.get("VelocityType").getAsString());
            assertEquals("SFX_Trashpile_Land",effects.get("WorldSoundEventId").getAsString());
            assertEquals("SFX_Trashpile_Land",effects.get("LocalSoundEventId").getAsString());
            assertEquals("Item_Break_Glass",effects.getAsJsonArray("WorldParticles").get(0).getAsJsonObject().get("SystemId").getAsString());
            assertEquals("RemoveEntity",trash.get(2).getAsJsonObject().get("Type").getAsString());
            assertEquals("User",trash.get(2).getAsJsonObject().get("Entity").getAsString());
            var turretMiss=turret.getAsJsonObject("Interactions").getAsJsonObject("ProjectileMiss").getAsJsonArray("Interactions");
            assertEquals("Simple",turretMiss.get(0).getAsJsonObject().get("Type").getAsString());
            assertEquals("SFX_Trashpile_Land",turretMiss.get(0).getAsJsonObject().getAsJsonObject("Effects").get("WorldSoundEventId").getAsString());
            assertEquals("Item_Break_Glass",turretMiss.get(0).getAsJsonObject().getAsJsonObject("Effects").getAsJsonArray("Particles").get(0).getAsJsonObject().get("SystemId").getAsString());
            assertEquals("Stone_Death",turretMiss.get(1).getAsJsonObject().get("EffectId").getAsString());
            assertEquals("RemoveEntity",turretMiss.get(2).getAsJsonObject().get("Type").getAsString());
            assertEquals("User",turretMiss.get(2).getAsJsonObject().get("Entity").getAsString());
        }
    }
    private static JsonObject read(ZipFile zip,String path)throws Exception{
        try(var input=zip.getInputStream(zip.getEntry(path))){return JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    @Test void allCurrentRoleSourcePinsMatch071WithoutChangingHistoricalBalance()throws Exception{
        var bindings=EnemyNativeBindings.load();assertEquals(86,bindings.roles().size());
        try(var zip=new ZipFile(Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip").toFile())){
            for(var role:bindings.roles())try(var input=zip.getInputStream(zip.getEntry(role.sourceAssetPath()))){
                assertEquals(role.sourceAssetSha256(),hash(input.readAllBytes()),role.nativeRoleIds().toString());
            }
        }
        assertEquals(15,com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.load().resolveRole("Rat").orElseThrow().canonical().nativeHealth());
        assertEquals(6,com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadProduction().find("Weapon_Flamethrower_Scrap").orElseThrow().basePower());
    }
    @Test void legacyGameplayPoliciesRemainByteIdenticalToStage1Parent()throws Exception{
        var pins=Map.of(
                "rpg/progression/enemy-registry.json","775a5d674ff71e4d55eb0d0afa0fecc2d8b10f0a6694186331ecd4c8ddb226cb",
                "rpg/progression/authored-encounters.json","7044373bf6a83b0f0a68a4b7604d33713eba04ab0b020cb1a428b4f26a9759dd",
                "rpg/runtime/native-item-power-vanilla-0.7-pre1.json","afd033a9459def653fa0704cdaf17f069bde0e6046bb59cf6598289ae9767436",
                "rpg/enemies/master-enemies-v1.json","da38d13611899c6885684815405499affc15ec26ad10314395fc69c7b7bbbc18",
                "rpg/enemies/monster-affixes-v1.json","e020fa7edb544f6db774fe593e59be3adc03e0a549783b032c7d2e41c17bc304",
                "rpg/enemies/name-pools-v1.json","2165e2dc83e2fd726c492f7dfbac7c2b6a828be70d1898730f391034df24b39d");
        for(var entry:pins.entrySet())assertEquals(entry.getValue(),hash(Files.readString(Path.of("src/main/resources",entry.getKey())).replace("\r\n","\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)),entry.getKey());
    }
    @Test void historicalBindingWireRevisionStillResolvesSavedActorsAndRejectsUnknownRevision(){
        var bindings=EnemyNativeBindings.load();assertEquals("hytale-0.7.0-pre.5.1-larva-owner-qa-1",bindings.revision());
        var gson=new Gson();var fixture=new EnemyAffixSnapshotTest();
        var json=gson.toJsonTree(fixture.actor(false,List.of(),List.of(),null)).getAsJsonObject();
        json.addProperty("nativeBindingRevision","hytale-0.7.0-pre.5.1-larva-owner-qa-1");
        var saved=gson.fromJson(json,EnemyDescriptor.class);
        assertEquals("Trork_Warrior",bindings.requireActorRole(saved).canonicalRoleId());
        assertEquals(saved,gson.fromJson(gson.toJson(saved),EnemyDescriptor.class));
        json.addProperty("nativeBindingRevision","unknown-future-binding");
        assertThrows(IllegalStateException.class,()->bindings.requireActorRole(gson.fromJson(json,EnemyDescriptor.class)));
    }
}
