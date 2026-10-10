package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R228 installed-data proof; tests the adapter delta without starting Hytale. */
class ProductionEliteArchetypeAssetTest {
    @Test void reportedSkeletonFlockMembersHaveExactCertifiedNativeRoutes()throws Exception{
        var bindings=EnemyNativeBindings.load();var catalog=EnemyRewardRegistry.load();
        var expected=Map.of("Skeleton_Archer","Skeleton_Archer_Bow_Shoot",
                "Skeleton_Burnt_Archer","Skeleton_Burnt_Archer_Bow_Shoot",
                "Skeleton_Frost_Archer","Skeleton_Frost_Archer_Bow_Shoot",
                "Skeleton_Soldier","Root_NPC_Skeleton_Soldier_Attack");
        try(var zip=new ZipFile(ASSETS.toFile())){
            for(var entry:expected.entrySet()){
                var id=entry.getKey();var role=bindings.role(id).orElseThrow();
                assertTrue(bindings.productionEligibilityRejection(id).isEmpty(),id);
                assertEquals(entry.getValue(),role.actions().getFirst().nativeActionId(),id);
                var source=catalog.roles().stream().filter(r->r.roleId().equals(id)).findFirst().orElseThrow();
                assertEquals(hash(zip.getInputStream(zip.getEntry(source.assetPath())).readAllBytes()),role.sourceAssetSha256(),id);
                assertNotNull(zip.getEntry("Server/Item/RootInteractions/NPCs/"+entry.getValue()+".json")!=null
                        ?zip.getEntry("Server/Item/RootInteractions/NPCs/"+entry.getValue()+".json")
                        :zip.stream().filter(path->path.getName().endsWith("/"+entry.getValue()+".json")
                                &&path.getName().startsWith("Server/Item/RootInteractions/")).findFirst().orElse(null),id);
            }
            for(var id:List.of("Skeleton_Archer_Wander","Skeleton_Burnt_Archer_Wander","Skeleton_Soldier_Wander")){
                var alias=catalog.aliases().stream().filter(r->r.roleId().equals(id)).findFirst().orElseThrow();
                var nativeRole=read(zip,alias.assetPath());var parent=nativeRole.get("Reference").getAsString();
                assertEquals(parent,bindings.role(id).orElseThrow().canonicalRoleId());
                assertEquals(bindings.role(parent).orElseThrow().actions(),bindings.role(id).orElseThrow().actions());
                assertTrue(bindings.productionEligibilityRejection(id).isEmpty(),id);
            }
        }
    }
    private static final Path ASSETS=Path.of(System.getProperty("user.home"),
            "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip");
    private static final Set<String> PLAIN=Set.of("Cow_Undead","Crocodile","Emberwulf","Hound_Bleached",
            "Leopard_Snow","Pig_Undead","Raptor_Cave","Tiger_Sabertooth");
    private static final Set<String> SELECTOR=Set.of("Fen_Stalker","Rex_Cave");
    private static final Map<String,String> BITE=Map.of("Fox","Root_NPC_Fox_Attack",
            "Hyena","Root_NPC_Hyena_Attack","Rat","Root_NPC_Rat_Attack",
            "Snake_Cobra","Root_NPC_Snake_Attack","Snake_Marsh","Root_NPC_Snake_Attack",
            "Snake_Rattle","Root_NPC_Snake_Attack","Wolf_Black","Root_NPC_Wolf_Attack",
            "Wolf_White","Root_NPC_Wolf_Attack");
    private static JsonObject read(ZipFile zip,String path)throws Exception{
        var entry=zip.getEntry(path);assertNotNull(entry,path);
        return JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static JsonObject packaged(String path)throws Exception{
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources",path))).getAsJsonObject();
    }
    private static String hash(byte[] data)throws Exception{
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    @Test void canonicalVariantsInheritOnlyTheCertifiedScoutCombatSignature()throws Exception{
        var catalog=EnemyRewardRegistry.load();var bindings=EnemyNativeBindings.load();
        try(var zip=new ZipFile(ASSETS.toFile())){
        for(var id:List.of("Skeleton_Scout_Wander","Skeleton_Scout_Patrol")){
            var alias=catalog.aliases().stream().filter(row->row.roleId().equals(id)).findFirst().orElseThrow();
            var nativeRole=read(zip,alias.assetPath());
            assertEquals("Skeleton_Scout",nativeRole.get("Reference").getAsString());
            assertEquals("Variant",nativeRole.get("Type").getAsString());
            assertTrue(Set.of("WanderRadius","Patrol","ApplySeparation","FollowPatrolPath")
                    .containsAll(nativeRole.getAsJsonObject("Modify").keySet()));
            assertEquals(hash(zip.getInputStream(zip.getEntry(alias.assetPath())).readAllBytes()),
                    bindings.role(id).orElseThrow().sourceAssetSha256());
            var role=bindings.role(id).orElseThrow();var base=bindings.role("Skeleton_Scout").orElseThrow();
            assertEquals(base.actions(),role.actions());assertEquals(base.capabilities(),role.capabilities());
            assertEquals(base.nativeImmunityChannels(),role.nativeImmunityChannels());
            assertEquals(base.nativeStatusSource(),role.nativeStatusSource());
            assertTrue(bindings.productionEligibilityRejection(id).isEmpty());
            for(var era:DifficultyId.values())assertTrue(role.profiles().get(era).contains("/"+id+"/"+era));
        }
        }
    }
    @Test void certifiedNativeMeleeGraphAndOnlyExistingLeafAdapterDelta()throws Exception{
        var bindings=EnemyNativeBindings.load();var catalog=EnemyRewardRegistry.load();
        var manifest=packaged("rpg/enemies/native-bindings-v1.json");
        var archetypes=manifest.getAsJsonObject("derivedBindings").getAsJsonArray("nativeArchetypes");
        assertEquals(3,archetypes.size());
        assertEquals(PLAIN,memberIds(archetypes.get(0).getAsJsonObject()));
        assertEquals(SELECTOR,memberIds(archetypes.get(1).getAsJsonObject()));
        assertEquals(BITE.keySet(),memberIds(archetypes.get(2).getAsJsonObject()));
        try(var zip=new ZipFile(ASSETS.toFile())){
            var root=read(zip,"Server/Item/RootInteractions/NPCs/Root_NPC_Attack_Melee.json");
            var replace=root.getAsJsonArray("Interactions").get(0).getAsJsonObject().getAsJsonObject("Next")
                    .getAsJsonArray("Interactions").get(1).getAsJsonObject();
            assertEquals("Melee_Start",replace.get("Var").getAsString());
            var start=read(zip,"Server/Item/Interactions/NPCs/NPC_Attack_Melee_Simple.json");
            assertEquals("Melee_Selector",start.getAsJsonObject("Next").get("Var").getAsString());
            var selector=read(zip,"Server/Item/Interactions/NPCs/NPC_Attack_Selector_Left.json");
            assertEquals("Melee_Damage",selector.getAsJsonObject("HitEntity").getAsJsonArray("Interactions")
                    .get(0).getAsJsonObject().get("Var").getAsString());
            assertTrue(read(zip,"Server/Item/Interactions/NPCs/NPC_Attack_Melee_Damage.json")
                    .getAsJsonObject("DamageEffects").has("Knockback"));
            var all=new HashSet<>(PLAIN);all.addAll(SELECTOR);all.addAll(BITE.keySet());
            for(var id:all){
                var source=catalog.roles().stream().filter(row->row.roleId().equals(id)).findFirst().orElseThrow();
                var nativeRole=read(zip,source.assetPath());var actual=packaged(source.assetPath());
                assertEquals(source.assetSha256().toLowerCase(Locale.ROOT),
                        hash(zip.getInputStream(zip.getEntry(source.assetPath())).readAllBytes()));
                var leaf=actual.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars")
                        .getAsJsonObject(BITE.containsKey(id)?"Bite_Damage":"Melee_Damage")
                        .getAsJsonArray("Interactions").get(0).getAsJsonObject();
                assertEquals("RPG_EnemyDamage",leaf.remove("Type").getAsString());
                assertEquals(nativeRole,actual,"Only the established damage leaf type may differ: "+id);
                var role=bindings.role(id).orElseThrow();
                assertTrue(Set.of("Template_Predator","Wolf_Black","Snake_Marsh")
                        .contains(nativeRole.get("Reference").getAsString()));
                if(!BITE.containsKey(id))assertEquals("Root_NPC_Attack_Melee",
                        nativeRole.getAsJsonObject("Modify").get("Attack").getAsString());
                var expectedVariables=BITE.containsKey(id)?Set.of("Melee_Start","Bite_Damage"):
                        PLAIN.contains(id)?Set.of("Melee_Start","Melee_Damage"):
                        Set.of("Melee_Start","Melee_Selector","Melee_Damage");
                assertEquals(expectedVariables,
                        nativeRole.getAsJsonObject("Modify").getAsJsonObject("_InteractionVars").keySet());
                assertEquals(BITE.containsKey(id)?BITE.get(id).replace("Root_NPC_","")
                        .replace("_Attack","_Bite_Damage"):"NPC_Attack_Melee_Damage",leaf.get("Parent").getAsString());
                assertEquals(Set.of("Physical"),leaf.getAsJsonObject("DamageCalculator")
                        .getAsJsonObject("BaseDamage").keySet());
                assertFalse(leaf.has("Next"));
                if(PLAIN.contains(id)){assertFalse(leaf.has("DamageEffects"));assertFalse(leaf.has("Effects"));}
                if(!BITE.containsKey(id)){
                    assertEquals(nativeRole.getAsJsonObject("Modify").get("Appearance").getAsString(),role.modelAssetId());
                    assertEquals(nativeRole.getAsJsonObject("Modify").get("DropList").getAsString(),role.nativeLootSourceId());
                }else assertEquals(id,role.modelAssetId());
                assertTrue(bindings.productionEligibilityRejection(id).isEmpty());
                assertEquals(1,role.actions().size());
                assertEquals(BITE.getOrDefault(id,"Root_NPC_Attack_Melee"),role.actions().get(0).nativeActionId());
                assertFalse(role.actions().get(0).supportedAffixIds().contains("ME-001"));
                assertFalse(role.actions().get(0).supportedAffixIds().contains("ME-019"));
            }
        }
    }
    private static Set<String> memberIds(JsonObject archetype){
        return Set.copyOf(archetype.getAsJsonArray("memberRoleIds").asList().stream()
                .map(value->value.getAsJsonObject().get("roleId").getAsString()).toList());
    }
    @Test void rejectsChangedNativeLeafAndPreservesPerAffixRejection(){
        var root=JsonParser.parseString(assertDoesNotThrow(()->Files.readString(Path.of(
                "src/main/resources/rpg/enemies/native-bindings-v1.json")))).getAsJsonObject();
        var archetype=root.getAsJsonObject("derivedBindings").getAsJsonArray("nativeArchetypes")
                .get(0).getAsJsonObject();
        archetype.addProperty("nativeActionId","Uncertified_Root");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(root));
        var binding=EnemyNativeBindings.load().role("Emberwulf").orElseThrow();
        var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        var context=binding.affixBinding(EnemyNativeBindings.load().revision(),true,3);
        assertEquals("ACTION_ROUTE_NOT_CERTIFIED",selector.rejectionReason(context,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.EXTRA_FAST)).orElseThrow());
        assertEquals("ACTION_ROUTE_NOT_CERTIFIED",selector.rejectionReason(context,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.KNOCKBACK)).orElseThrow());
    }
}
