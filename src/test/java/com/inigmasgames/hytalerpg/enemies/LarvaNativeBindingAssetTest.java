package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Installed asset graph evidence. Runtime role binding still validates the decoded native graph. */
class LarvaNativeBindingAssetTest {
    private static final Path GAME=Path.of(System.getProperty("user.home"),"AppData","Roaming","Hytale",
            "install","pre-release","package","game","latest");
    private static final String ROLE="Server/NPC/Roles/Void/Larva_Void.json";
    private static JsonObject read(ZipFile zip,String path)throws IOException{
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        try(var stream=zip.getInputStream(entry);var reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hash(Path path)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");
        try(var stream=Files.newInputStream(path)){
            var buffer=new byte[1024*1024];int length;
            while((length=stream.read(buffer))>=0)digest.update(buffer,0,length);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void leaf(JsonObject role){
        assertEquals("Template_Predator",role.get("Reference").getAsString());
        var modify=role.getAsJsonObject("Modify");
        assertEquals("Larva_Void_Bite",modify.get("Attack").getAsString());
        var damage=modify.getAsJsonObject("_InteractionVars").getAsJsonObject("Melee_Damage")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("NPC_Attack_Melee_Damage",damage.get("Parent").getAsString());
        assertEquals("RPG_EnemyDamage",damage.get("Type").getAsString());
        assertEquals(8,damage.getAsJsonObject("DamageCalculator").getAsJsonObject("BaseDamage").get("Physical").getAsInt());
        var impulse=damage.getAsJsonObject("DamageEffects").getAsJsonObject("Knockback");
        assertNotNull(impulse);
        assertTrue(impulse.has("Force"));
    }
    @Test void roleAndRootResolve()throws Exception{
        var manifest=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/rpg/enemies/native-bindings-v1.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1,manifest.get("schemaVersion").getAsInt());
        var binding=manifest.getAsJsonArray("bindings").get(0).getAsJsonObject();
        assertEquals("Larva_Void",binding.get("canonicalRoleId").getAsString());
        assertTrue(binding.get("productionPromotionEnabled").getAsBoolean());
        assertFalse(binding.get("distanceDisplacementDelivery").getAsBoolean());
        assertTrue(binding.getAsJsonArray("nativeImmunityChannels").isEmpty());
        assertFalse(binding.get("nativeStunStaggerImmune").getAsBoolean());
        assertFalse(binding.get("nativeSlowImmune").getAsBoolean());
        assertEquals(EnemyStatusEffects.Source.noNativeStatus(0),EnemyNativeBindings.load().role("Larva_Void").orElseThrow().nativeStatusSource());
        assertEquals(com.inigmasgames.hytalerpg.gear.GearLootService.MAX_BASE_EQUIPMENT_SLOTS_PER_DEFEAT,
                binding.get("maximumBaseEquipmentSlots").getAsInt());
        var existingProfiles=com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.load().profiles().stream()
                .map(com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Profile::id).collect(java.util.stream.Collectors.toSet());
        for(var mode:List.of("NORMAL","NIGHTMARE","HELL"))assertTrue(existingProfiles.contains(
                binding.getAsJsonObject("encounterProfileIdsByMode").get(mode).getAsString()),mode);
        assertEquals(hash(GAME.resolve("Assets.zip")),manifest.get("assetsSha256").getAsString());
        assertTrue(Set.of(manifest.get("serverJarSha256").getAsString(),
                "6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e")
                .contains(hash(GAME.resolve("Server/HytaleServer.jar"))),"Expected original or approved version-pinned native patch");
        try(var zip=new ZipFile(GAME.resolve("Assets.zip").toFile())){
            var nativeRole=read(zip,ROLE);
            assertEquals(nativeRole.getAsJsonObject("Modify").get("DropList").getAsString(),
                    binding.get("nativeLootSourceId").getAsString());
            assertEquals(hash(zip.getInputStream(zip.getEntry(ROLE)).readAllBytes()),binding.get("sourceAssetSha256").getAsString());
            var routed=JsonParser.parseReader(Files.newBufferedReader(Path.of("src/main/resources").resolve(ROLE))).getAsJsonObject();
            leaf(routed);
            nativeRole.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars").getAsJsonObject("Melee_Damage")
                    .getAsJsonArray("Interactions").get(0).getAsJsonObject().addProperty("Type","RPG_EnemyDamage");
            assertEquals(nativeRole,routed,"The shipped role may change only the inline adapter type");
            var root=read(zip,"Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Void/Larva_Void/Larva_Void_Bite.json");
            assertEquals("Larva_Void_Bite",root.getAsJsonArray("Interactions").get(0).getAsJsonObject()
                    .getAsJsonObject("Next").getAsJsonArray("Interactions").get(1).getAsString());
            var attack=read(zip,"Server/Item/Interactions/NPCs/Void/Larva_Void/Larva_Void_Bite.json");
            assertEquals(Set.of("Type","Effects","$Comment","RunTime","Next"),attack.keySet());
            assertEquals(Set.of("ItemPlayerAnimationsId","ItemAnimationId"),attack.getAsJsonObject("Effects").keySet());
            var branches=attack.getAsJsonObject("Next").getAsJsonArray("Interactions");
            var combat=branches.get(0).getAsJsonObject().getAsJsonArray("Interactions").get(0).getAsJsonObject();
            double protectedSeconds=attack.get("RunTime").getAsDouble()+combat.get("RunTime").getAsDouble()
                    +combat.getAsJsonObject("Next").get("RunTime").getAsDouble();
            assertEquals(.5,protectedSeconds,1e-9);
            assertEquals(.167,branches.get(1).getAsJsonObject().getAsJsonArray("Interactions").get(0)
                    .getAsJsonObject().get("RunTime").getAsDouble());
            var animation=read(zip,"Server/Item/Animations/NPC/Void/Larva_Void/Larva_Void_Default.json")
                    .getAsJsonObject("Animations").getAsJsonObject("Bite");
            assertEquals(1,animation.get("Speed").getAsInt());
            assertEquals("NPC/Beast/Scarak_Louse/Animations/Attacks/Bite.blockyanim",animation.get("ThirdPerson").getAsString());
            assertEquals(30,read(zip,"Common/NPC/Beast/Scarak_Louse/Animations/Attacks/Bite.blockyanim")
                    .get("duration").getAsInt());
            var template=read(zip,"Server/NPC/Roles/_Core/Templates/Template_Predator.json");
            var pauses=template.getAsJsonObject("Parameters").getAsJsonObject("AttackPauseRange").getAsJsonArray("Value");
            assertEquals(2,pauses.get(0).getAsInt());assertEquals(3,pauses.get(1).getAsInt());
            var profile=com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.protectedSeconds(
                    EnemyNativeBindings.load().role("Larva_Void").orElseThrow().recoveryProfile("Larva_Void_Bite").orElseThrow());
            assertEquals(protectedSeconds,profile,1e-9);
            assertEquals(1.75,new com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming.Recovery(
                    "Larva_Void_Bite","test",profile,1.2).pause("Larva_Void_Bite",2),1e-9);
            var defaultDamage=read(zip,"Server/Item/Interactions/NPCs/NPC_Attack_Melee_Damage.json");
            assertEquals(Set.of("Parent","DamageCalculator","DamageEffects"),defaultDamage.keySet());
            var parent=read(zip,"Server/Item/Interactions/Weapons/DamageEntityParent.json");
            assertEquals("DamageEntity",parent.get("Type").getAsString());
            assertTrue(parent.getAsJsonObject("DamageEffects").isEmpty());
            assertEquals("DamageEntityParent_On_Hit",parent.get("Next").getAsString());
            var onHit=read(zip,"Server/Item/Interactions/Weapons/DamageEntityParent_On_Hit.json")
                    .getAsJsonArray("Interactions");
            assertEquals("ApplyEffect",onHit.get(0).getAsJsonObject().get("Type").getAsString());
            assertEquals("Red_Flash",onHit.get(0).getAsJsonObject().get("EffectId").getAsString());
            for(int hit=1;hit<onHit.size();hit++)
                assertEquals("ClearEntityEffect",onHit.get(hit).getAsJsonObject().get("Type").getAsString());
            var flash=read(zip,"Server/Entity/Effects/Damage/Red_Flash.json");
            assertEquals(Set.of("EntityBottomTint","EntityTopTint"),flash.getAsJsonObject("ApplicationEffects").keySet());
            var selector=attack.getAsJsonObject("Next").getAsJsonArray("Interactions").get(0).getAsJsonObject()
                    .getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals("Melee_Damage",selector.getAsJsonObject("HitEntity").getAsJsonArray("Interactions")
                    .get(0).getAsJsonObject().get("Var").getAsString());
            var model=read(zip,"Server/Models/Void/Larva_Void.json");
            assertEquals("NPC/Beast/Larva/Models/Model_Void.png",model.get("Texture").getAsString());
            for(var texture:binding.getAsJsonArray("defaultTextureAssetIds"))assertNotNull(zip.getEntry("Common/"+texture.getAsString()));
        }
    }
    @Test void rejectChangedParentOrImpulse()throws Exception{
        try(var zip=new ZipFile(GAME.resolve("Assets.zip").toFile())){
            var role=read(zip,ROLE);var damage=role.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                    .getAsJsonObject("Melee_Damage").getAsJsonArray("Interactions").get(0).getAsJsonObject();
            damage.addProperty("Type","RPG_EnemyDamage");leaf(role);
            damage.addProperty("Parent","DifferentAttack");assertThrows(AssertionError.class,()->leaf(role));
            damage.addProperty("Parent","NPC_Attack_Melee_Damage");
            damage.getAsJsonObject("DamageEffects").remove("Knockback");assertThrows(AssertionError.class,()->leaf(role));
        }
    }
    @Test void naturalWorldJobsProvideAnExistingCompatibleFlockWithoutSyntheticMembers()throws Exception{
        try(var zip=new ZipFile(GAME.resolve("Assets.zip").toFile())){
            for(int tier=1;tier<=3;tier++){
                var spawn=read(zip,"Server/NPC/Spawn/World/Void/Tier"+tier+"_Night_NPC1.json");
                var row=spawn.getAsJsonArray("NPCs").get(0).getAsJsonObject();
                assertEquals("Larva_Void",row.get("Id").getAsString());
                var size=row.getAsJsonObject("Flock").getAsJsonArray("Size");
                assertEquals(2,size.get(0).getAsInt());
                assertEquals(tier==1?3:4,size.get(1).getAsInt());
            }
            var portal=read(zip,"Server/NPC/Spawn/World/Unique/Spawns_Portals_Goblin_Caves_Void.json");
            var row=portal.getAsJsonArray("NPCs").get(0).getAsJsonObject();
            assertEquals("Larva_Void",row.get("Id").getAsString());
            assertEquals("Group_Medium",row.get("Flock").getAsString());
            assertEquals(4,read(zip,"Server/NPC/Flocks/Group_Medium.json").get("MinSize").getAsInt());
        }
    }
}
