package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact installed asset comparison; does not launch a server or establish connected behavior. */
class EnemyNativeAssetRoutingTest {
    final Path assets=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
    static final String PREFIX="Server/NPC/Roles/Intelligent/Faction/Trork/";
    JsonObject packaged(String path)throws Exception{
        try(var input=getClass().getResourceAsStream("/"+path)){assertNotNull(input,path);return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    JsonObject original(ZipFile zip,String path)throws Exception{
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        try(var input=zip.getInputStream(entry)){return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();}
    }
    @Test void packagedAssetsDifferOnlyAtTheFivePinnedNativeAdapterSeams()throws Exception{
        var pins=JsonParser.parseString(Files.readString(Path.of("evidence/master-enemies/baseline/native-adapter-assets.json"))).getAsJsonArray();
        assertEquals(5,pins.size());
        try(var zip=new ZipFile(assets.toFile())){
            for(var element:pins){
                var pin=element.getAsJsonObject();String path=pin.get("path").getAsString();byte[] bytes;
                try(var input=zip.getInputStream(zip.getEntry(path))){bytes=input.readAllBytes();}
                assertEquals(pin.get("nativeSha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                var expected=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));var actual=packaged(path);
                if(path.equals(PREFIX+"Trork_Warrior.json")){
                    var leaf=actual.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars").getAsJsonObject("Melee_Damage").getAsJsonArray("Interactions").get(0).getAsJsonObject();
                    assertEquals("RPG_EnemyDamage",leaf.remove("Type").getAsString());
                }else if(path.equals("Server/NPC/Roles/Void/Larva_Void.json")){
                    var leaf=actual.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars").getAsJsonObject("Melee_Damage").getAsJsonArray("Interactions").get(0).getAsJsonObject();
                    assertEquals("RPG_EnemyDamage",leaf.remove("Type").getAsString());
                }else if(path.equals(PREFIX+"Templates/Template_Trork_Melee.json")
                        ||path.equals("Server/NPC/Roles/_Core/Templates/Template_Predator.json")){
                    var walk=actual.getAsJsonArray("MotionControllerList").get(0).getAsJsonObject();
                    assertEquals("HywindEnemyWalk",walk.get("Type").getAsString());walk.addProperty("Type","Walk");
                    if(path.equals("Server/NPC/Roles/_Core/Templates/Template_Predator.json")){
                        var attack=actual.getAsJsonArray("Instructions").get(2).getAsJsonObject()
                                .getAsJsonArray("Instructions").get(3).getAsJsonObject()
                                .getAsJsonArray("Instructions").get(9).getAsJsonObject()
                                .getAsJsonArray("Actions").get(1).getAsJsonObject();
                        assertEquals("HywindEnemyAttack",attack.get("Type").getAsString());
                        assertEquals("Attack",attack.getAsJsonObject("Attack").get("Compute").getAsString());
                        assertEquals("AttackPauseRange",attack.getAsJsonObject("AttackPauseRange").get("Compute").getAsString());
                        attack.addProperty("Type","Attack");
                    }
                }else{
                    assertEquals(PREFIX+"Components/Component_Instruction_Attack_Sequence_Trork_Warrior.json",path);
                    var actions=actual.getAsJsonObject("Content").getAsJsonArray("Actions");assertEquals(5,actions.size());
                    for(var action:actions){var value=action.getAsJsonObject();assertEquals("HywindEnemyAttack",value.get("Type").getAsString());value.addProperty("Type","Attack");}
                }
                assertEquals(expected,actual,path+": unrelated native gameplay or presentation changed");
            }
        }
    }
    @Test void everyWarriorAttackStillUsesItsNativeSelectorDamageVariableAndEffects()throws Exception{
        try(var zip=new ZipFile(assets.toFile())){
            for(String suffix:List.of("Left","Right","Down","Up_Left","Up_Right")){
                String id="Trork_Warrior_Battleaxe_Swing_"+suffix;
                var root=original(zip,"Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Intelligent/Trork_Warrior/"+id+".json");
                var attack=original(zip,"Server/Item/Interactions/NPCs/Intelligent/Trork_Warrior/"+id+".json");
                assertWarriorAttackGraph(id,root,attack);
                assertNull(getClass().getResource("/Server/Item/Interactions/NPCs/Intelligent/Trork_Warrior/"+id+".json"),"No replacement attack may be authored for the adapter");
            }
            var hit=original(zip,"Server/Item/Interactions/Weapons/DamageEntityParent_On_Hit.json");
            assertEquals("Red_Flash",hit.getAsJsonArray("Interactions").get(0).getAsJsonObject().get("EffectId").getAsString());
            assertEquals(7,hit.getAsJsonArray("Interactions").size());
            for(int i=1;i<7;i++)assertEquals("ClearEntityEffect",hit.getAsJsonArray("Interactions").get(i).getAsJsonObject().get("Type").getAsString());
            for(String side:List.of("Left","Right")){
                var animation=original(zip,"Common/Characters/Animations/Items/Dual_Handed/Battleaxe/Attacks/Swing_Up_"+side+"/Swing_Up_"+side+".blockyanim");
                assertEquals(60,animation.get("duration").getAsInt()); // Do not borrow the shorter Sword override's recovery window.
            }
        }
    }
    @Test void nativeKnockbackCapabilityIsPinnedToActualMeleeDamageAssets()throws Exception{
        var bindings=EnemyNativeBindings.load();
        try(var zip=new ZipFile(assets.toFile())){
            var parent=original(zip,"Server/Item/Interactions/NPCs/NPC_Attack_Melee_Damage.json");
            assertTrue(parent.getAsJsonObject("DamageEffects").has("Knockback"));
            var trork=packaged(PREFIX+"Trork_Warrior.json");
            var trorkLeaf=trork.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                    .getAsJsonObject("Melee_Damage").getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals("NPC_Attack_Melee_Damage",trorkLeaf.get("Parent").getAsString());
            assertTrue(bindings.role("Trork_Warrior").orElseThrow().capabilities()
                    .contains(EnemyAffixRegistry.Capability.NATIVE_KNOCKBACK));
            var larva=packaged("Server/NPC/Roles/Void/Larva_Void.json");
            var larvaLeaf=larva.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                    .getAsJsonObject("Melee_Damage").getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertTrue(larvaLeaf.getAsJsonObject("DamageEffects").has("Knockback"));
            assertTrue(bindings.role("Larva_Void").orElseThrow().capabilities()
                    .contains(EnemyAffixRegistry.Capability.NATIVE_KNOCKBACK));
            assertFalse(bindings.role("Skeleton_Scout").orElseThrow().capabilities()
                    .contains(EnemyAffixRegistry.Capability.NATIVE_KNOCKBACK));
        }
    }
    private static void assertWarriorAttackGraph(String id,JsonObject root,JsonObject attack){
        assertEquals(id,root.getAsJsonArray("Interactions").get(0).getAsString());
        assertEquals(.4,attack.get("RunTime").getAsDouble());
        var branches=attack.getAsJsonObject("Next").getAsJsonArray("Interactions");assertEquals(2,branches.size());
        var selector=branches.get(0).getAsJsonObject().getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("Selector",selector.get("Type").getAsString());assertEquals(.15,selector.get("RunTime").getAsDouble());
        assertEquals(.45,selector.getAsJsonObject("Next").get("RunTime").getAsDouble());
        var replace=selector.getAsJsonObject("HitEntity").getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("Melee_Damage",replace.get("Var").getAsString());
        assertEquals("NPC_Attack_Melee_Damage",replace.getAsJsonObject("DefaultValue").getAsJsonArray("Interactions").get(0).getAsString());
    }
    @Test void changedWarriorSelectorCannotPassThePinnedGraph()throws Exception{
        String id="Trork_Warrior_Battleaxe_Swing_Left";
        try(var zip=new ZipFile(assets.toFile())){
            var root=original(zip,"Server/Item/RootInteractions/NPCs/OLD_INTERACTIONS/Intelligent/Trork_Warrior/"+id+".json");
            var attack=original(zip,"Server/Item/Interactions/NPCs/Intelligent/Trork_Warrior/"+id+".json");
            assertWarriorAttackGraph(id,root,attack);
            var selector=attack.getAsJsonObject("Next").getAsJsonArray("Interactions").get(0)
                    .getAsJsonObject().getAsJsonArray("Interactions").get(0).getAsJsonObject();
            selector.getAsJsonObject("HitEntity").getAsJsonArray("Interactions").get(0)
                    .getAsJsonObject().addProperty("Var","Unexpected_Damage");
            assertThrows(AssertionError.class,()->assertWarriorAttackGraph(id,root,attack));
        }
    }
    @Test void disabledWarriorCandidatePinsAnInstalledRoleModelAndAuthoredProfiles()throws Exception{
        var binding=EnemyNativeBindings.load().role("Trork_Warrior").orElseThrow();
        assertTrue(binding.productionPromotionEnabled());
        assertFalse(binding.capabilities().contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE));
        assertFalse(binding.distanceDisplacementDelivery());
        var profiles=AuthoredEncounterCatalog.load().profiles();
        for(var mode:DifficultyId.values())assertTrue(profiles.stream().anyMatch(profile->
                profile.roleId().equals("Trork_Warrior")&&profile.difficulty()==mode
                        &&profile.id().equals(binding.profiles().get(mode))),"Trork profile "+mode);
        try(var zip=new ZipFile(assets.toFile())){
            var nativeRole=zip.getEntry(binding.sourceAssetPath());
            assertNotNull(nativeRole);
            try(var input=zip.getInputStream(nativeRole)){
                assertEquals(binding.sourceAssetSha256(),HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
            }
            var model=original(zip,"Server/Models/Intelligent/Trork/Trork_Warrior.json");
            assertEquals(binding.textures().getFirst(),model.get("Texture").getAsString());
            for(var texture:binding.textures())assertNotNull(zip.getEntry("Common/"+texture),texture);
        }
    }
}
