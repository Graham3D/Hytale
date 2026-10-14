package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.status.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline native Health/control contract. Does not certify connected publication or delivery. */
class Me12DefenderRouteTest {
    @Test void asynchronousPlanningDoesNotRetainTheMutableNativeSpawnJob()throws Exception{
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var blockField=com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.class.getDeclaredField("ASSET_STORE");blockField.setAccessible(true);
        var previous=blockField.get(null);
        var blocks=new com.hypixel.hytale.assetstore.map.BlockTypeAssetMap<String,com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType>(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType[]::new,com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType::getId);
        var builder=com.hypixel.hytale.server.core.asset.HytaleAssetStore.builder(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.class,blocks)
                .setPath("Item/Block").setCodec(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.CODEC).setKeyFunction(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType::getId).setReplaceOnRemove(com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType::getUnknownFor);
        var assets=new com.hypixel.hytale.server.core.asset.HytaleAssetStore<>(builder){final com.hypixel.hytale.event.EventBus events=new com.hypixel.hytale.event.EventBus(false);@Override protected com.hypixel.hytale.event.EventBus getEventBus(){return events;}};
        com.hypixel.hytale.assetstore.AssetRegistry.register(assets);blockField.set(null,assets);
        try{
        var job=new com.hypixel.hytale.server.spawning.world.component.SpawnJobData();
        var field=job.getClass().getDeclaredField("roleIndex");field.setAccessible(true);field.setInt(job,7);job.setSpawnFrozen(true);
        var snapshot=com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyFlockExtension.Source.capture(job);
        field.setInt(job,19);job.setSpawnFrozen(false);
        assertEquals(7,snapshot.roleIndex());assertTrue(snapshot.spawnFrozen());assertEquals(19,job.getRoleIndex());
        }finally{com.hypixel.hytale.assetstore.AssetRegistry.unregister(assets);blockField.set(null,previous);}
    }
    static final Path ASSETS=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip");
    static JsonArray certificates() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/rpg/enemies/defender-routes-0.7.1.json"))).getAsJsonArray();
    }
    @BeforeAll static void setup() throws Exception {EnemyNativeHealthTest.setup();}
    @AfterAll static void cleanup() throws Exception {EnemyNativeHealthTest.cleanup();}
    @Test void everyDefenderRoutePinsItsActualRoleAndPreservesOutgoingRestrictions() throws Exception {
        var bindings=EnemyNativeBindings.load();var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        assertEquals(26,certificates().size());
        try(var zip=new ZipFile(ASSETS.toFile())){
            for(var element:certificates()){
                var row=element.getAsJsonObject();String id=row.get("roleId").getAsString();
                var role=bindings.role(id).orElseThrow();
                try(var input=zip.getInputStream(zip.getEntry(role.sourceAssetPath()))){
                    assertEquals(role.sourceAssetSha256(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())),id);
                }
                var binding=role.affixBinding(bindings.revision(),true,3);
                assertTrue(bindings.productionEligibilityRejection(id).isEmpty(),id);
                var own=id.equals("Golem_Firesteel")?List.of(STONE_SKIN,UNWAVERING,HORDE,REFLECTIVE):List.of(EXTRA_STRONG,STONE_SKIN,UNWAVERING,REFLECTIVE);
                var request=new EnemyAffixSelection.Request(binding,DifficultyId.HELL,EnemyRarity.UNIQUE,4,List.of(),true);
                assertTrue(selector.legal(request,own.stream().map(EnemyAffixSelection.Choice::of).toList()),id);
                assertFalse(selector.legal(request,List.of(EnemyAffixSelection.Choice.of(STONE_SKIN),EnemyAffixSelection.Choice.of(MAGIC_RESISTANT))),id);
                if(!id.equals("Golem_Firesteel"))for(var action:role.actions())
                    assertEquals(Set.of("ME-002","ME-003","ME-004","ME-017"),action.supportedAffixIds(),id);
                assertFalse(selector.eligible(request,EnemyAffixSelection.Choice.of(FIRE_ENCHANTED)),id);
                var gson=new Gson();var changed=gson.toJsonTree(role).getAsJsonObject();
                changed.addProperty("sourceAssetSha256","0".repeat(64));
                assertTrue(EnemyNativeDefenderRoutes.certified(gson.fromJson(changed,EnemyNativeBindings.Role.class)).isEmpty(),id);
            }
        }
    }
    @Test void actualNativeHealthLossFeedsRoleIndependentReflectionAndRejectsNoLossOrIndirect() throws Exception {
        var fixture=new EnemyAffixSnapshotTest();var gson=new Gson();
        for(var element:certificates()){
            String id=element.getAsJsonObject().get("roleId").getAsString();
            var role=EnemyNativeBindings.load().role(id).orElseThrow();
            var actor=gson.toJsonTree(fixture.actor(false,List.of(fixture.affix(REFLECTIVE)),List.of(),null)).getAsJsonObject();
            actor.addProperty("nativeRoleId",id);actor.addProperty("canonicalRoleId",role.canonicalRoleId());
            var defender=gson.fromJson(actor,EnemyDescriptor.class);
            var nativeHealth=new EntityStatMap();nativeHealth.update();int health=DefaultEntityStatTypes.getHealth();
            nativeHealth.setStatValue(health,100);double before=nativeHealth.get(health).get();
            nativeHealth.setStatValue(health,93);double after=nativeHealth.get(health).get();
            var effects=new EnemyReflectiveEffects();var writes=new ArrayList<Double>();var attacker=UUID.randomUUID();
            assertEquals(.7,effects.offer(defender,attacker,"native-loss",before-after,after,200,2,true,0,()->true,
                    raw->{writes.add(raw);return raw;}).rawSent(),1e-8,id);
            assertEquals(93,nativeHealth.get(health).get(),id);
            assertEquals(0,effects.offer(defender,attacker,"absorbed",0,after,200,2,true,0,()->true,raw->fail()).rawSent(),id);
            assertEquals(0,effects.offer(defender,attacker,"secondary",7,after,200,2,false,0,()->true,raw->fail()).rawSent(),id);
            assertEquals(0,effects.offer(defender,attacker,"stale",7,after,200,2,true,0,()->false,raw->fail()).rawSent(),id);
            assertEquals(1,writes.size(),id);
        }
    }
    @Test void firesteelControlRouteBlocksOnlyStunFamilyAndDoesNotTouchAnyNativeAttack() throws Exception {
        var role=EnemyNativeBindings.load().role("Golem_Firesteel").orElseThrow();
        assertEquals(Set.of("ME-017"),EnemyNativeDefenderRoutes.certified(role));
        assertTrue(role.actions().isEmpty());assertFalse(role.nativeStunStaggerImmune());
        var fixture=new EnemyAffixSnapshotTest();var actor=fixture.actor(false,List.of(fixture.affix(UNWAVERING)),List.of(),null);
        var gson=new Gson();var incoming=gson.toJsonTree(actor).getAsJsonObject();incoming.addProperty("nativeRoleId","Golem_Firesteel");incoming.addProperty("canonicalRoleId","Golem_Firesteel");
        assertTrue(EnemyIncomingOnlyAttachment.accepts(role,gson.fromJson(incoming,EnemyDescriptor.class)));
        var outgoing=gson.toJsonTree(fixture.actor(false,List.of(fixture.affix(EXTRA_STRONG)),List.of(),null)).getAsJsonObject();
        outgoing.addProperty("nativeRoleId","Golem_Firesteel");outgoing.addProperty("canonicalRoleId","Golem_Firesteel");
        assertFalse(EnemyIncomingOnlyAttachment.accepts(role,gson.fromJson(outgoing,EnemyDescriptor.class)));
        var providers=EnemyAffixSnapshot.resolve(actor,fixture.balance,fixture.pack(),0,false,false);
        var status=new StatusService(CombatBalanceProfile.loadCanonical(),()->1_000_000_000L);
        status.configureEncounterAdmission((id,type)->providers.stunStaggerImmune()&&Set.of("STUN","STAGGER").contains(type),(a,b)->true,id->false);
        var source=UUID.randomUUID();var target=UUID.randomUUID();
        assertEquals("IMMUNE",status.applyHostile(source,target,RpgStatusType.STAGGER,ControlProfile.NORMAL,2).detail());
        assertEquals(StatusService.Outcome.APPLIED,status.applyHostile(source,target,RpgStatusType.ROOT,ControlProfile.NORMAL,2).outcome());
        try(var zip=new ZipFile(ASSETS.toFile());var input=zip.getInputStream(zip.getEntry(role.sourceAssetPath()))){
            var nativeRole=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var modify=nativeRole.getAsJsonObject("Modify");
            assertEquals("Root_NPC_Golem_Firesteel_Attack",modify.get("Attack").getAsString());
            assertEquals(Set.of("Immunity_Fire"),new HashSet<>(new Gson().fromJson(modify.getAsJsonObject("_CombatConfig").get("EntityEffects"),List.class)));
            var variables=modify.getAsJsonObject("_InteractionVars");assertEquals(7,variables.size());
            for(var variable:variables.entrySet()){
                var leaf=variable.getValue().getAsJsonObject().getAsJsonArray("Interactions").get(0).getAsJsonObject();
                assertEquals(-10,leaf.getAsJsonObject("DamageEffects").getAsJsonObject("Knockback").get("Force").getAsInt());
            }
            assertFalse(Files.exists(Path.of("src/main/resources",role.sourceAssetPath())));
        }
    }
    @Test void firesteelV2OfflineGraphsUseOnlyItsIncomingContractAcrossEras(){
        var role=EnemyNativeBindings.load().role("Golem_Firesteel").orElseThrow();
        var policy=Me12Policies.canonical();
        var allowed=Set.of("ME-003","ME-004","ME-017","ME-018","ME-022","ME-023","ME-024","ME-026","ME-027");
        for(var era:DifficultyId.values()){
            var member=new Me12Planner.Member(role.canonicalRoleId(),role.sourceValidationId(),
                    role.affixBinding(EnemyNativeBindings.load().revision(),true,3),role.nativeImmunityChannels(),true,"Golem_Firesteel");
            var context=new Me12Planner.Context("a".repeat(64),"native-job/firesteel",1,era,
                    Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,member,List.of(member,member,member),
                    List.of(member,member),Me12Planner.weights(policy.registry(),era,
                    new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
            var compiled=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));
            if(era==DifficultyId.NORMAL){
                // Firesteel's native Fire immunity is an existing Normal-era policy exclusion.
                assertEquals(0,Arrays.stream(compiled.tierMasses()).sum());
                continue;
            }
            assertTrue(Arrays.stream(compiled.tierMasses()).sum()>0,era.name());
            for(int i=0;i<20;i++)assertTrue(compiled.sample("firesteel/"+era+"/"+i).choices().stream()
                    .allMatch(choice->allowed.contains(choice.affixId())),era.name());
        }
    }
}
