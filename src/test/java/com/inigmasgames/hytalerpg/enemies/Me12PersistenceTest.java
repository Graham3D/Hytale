package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.spawning.HywindWorldConfiguration;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

public class Me12PersistenceTest {
    @TempDir Path directory;
    static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    public record Fixture(Me12Policies policies,Me12Planner.Compiled compiled,EnemyBirthRoot root) {}
    public static Fixture fixture(boolean eight) {
        var policy=Me12Policies.canonical();var balance=policy.balance();var helper=new EnemyBirthPlannerTest();
        var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int i=0;i<6;i++) {
            var c=helper.candidate(i,DifficultyId.HELL,EnumSet.allOf(Capability.class),2);
            var json=JSON.toJsonTree(c.nativeBaseline()).getAsJsonObject();json.addProperty("balanceRevision",balance.revision());
            json.add("immutableRewardContext",JSON.toJsonTree(balance.rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.NATURAL,false,0)));
            var binding=new EnemyAffixSelection.Binding(c.binding().revision(),c.binding().capabilities(),100,true,false,false,3,true);
            candidates.add(new EnemyBirthPlanner.Candidate(JSON.fromJson(json,EnemyDescriptor.class),binding,2));
        }
        var leader=candidates.getFirst();
        var member=new Me12Planner.Member("Trork_Warrior","source-proof",leader.binding(),Set.of(),true);
        var context=new Me12Planner.Context("a".repeat(64),"native-job/birth",1,DifficultyId.HELL,Me12Planner.Evidence.CERTIFIED_STATIC_FIXTURE,member,
                List.of(member,member,member),List.of(member,member),Me12Planner.weights(policy.registry(),DifficultyId.HELL,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of())));
        var compiled=Me12Planner.compile(context,policy,new Me12Planner.Budget(n->{}));
        Me12Planner.Plan selected=null;String seed=null;
        for(int i=0;i<10000;i++){seed="persist/"+i;selected=compiled.sample(seed);if(!eight||selected.affixes().size()==8)break;}
        if(eight)assertEquals(8,selected.affixes().size());
        var request=new EnemyBirthPlanner.Request(seed,EnemyBirthPlannerTest.id("pack"),Vec3.ZERO,"native-job/birth",candidates.subList(0,2),candidates.subList(2,6),true,4,true);
        var planner=new EnemyBirthPlanner(balance,policy.registry(),policy.affinities(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
        var plan=planner.planV2Offline(request,compiled,selected);
        var original=request.originals().stream().map(c->spawn(c.nativeBaseline(),false)).toList();
        var attachments=plan.actors().stream().map(a->spawn(a,true)).toList();
        return new Fixture(policy,compiled,new EnemyBirthRoot(plan,original,attachments));
    }
    public static EnemyRewardRegistry.Spawn spawn(EnemyDescriptor actor,boolean rewards) {
        var combat=new EncounterProfileResolver.Resolved(actor.worldId(),actor.entityId(),actor.difficulty(),actor.sourceValidationId(),"fixture-world",actor.nativeRoleId(),"fixture/zone/biome",actor.combatLevel(),100,10,1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        return new EnemyRewardRegistry.Spawn(actor.worldId(),actor.entityId(),actor.nativeRoleId(),"trork_warrior","fixture/zone/biome",actor.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,actor.sourceValidationId(),1000,null,combat,rewards?actor.immutableRewardContext():null);
    }
    @Test void P02P03StrictNestedVersionsAndInheritedMinions() {
        var f=fixture(true);var plan=f.root().plan();assertEquals(2,plan.schemaVersion());
        assertEquals(f.root(),JSON.fromJson(JSON.toJson(f.root()),EnemyBirthRoot.class));
        var leader=plan.actors().getFirst();assertEquals(8,leader.ownAffixes().size());
        assertEquals(Me12RewardMath.freeze(leader.v2().threatTotal(),2,1.5),leader.immutableRewardContext().v2Factors());
        assertTrue(leader.immutableRewardContext().xpFactor()>2);
        for(var minion:plan.actors().subList(1,plan.actors().size())){
            assertEquals(0,minion.ownAffixes().size());assertNull(minion.immutableRewardContext().v2());assertEquals(f.policies().balance().rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.INITIAL_PACK_MINION,true,0),minion.immutableRewardContext());
            assertEquals(leader.logicalActorId(),minion.v2().sourceLeader());
        }
        for(int version:List.of(0,1,3,99)){var bad=JSON.toJsonTree(leader).getAsJsonObject();bad.addProperty("schemaVersion",version);assertThrows(RuntimeException.class,()->JSON.fromJson(bad,EnemyDescriptor.class));}
        var missing=JSON.toJsonTree(leader).getAsJsonObject();missing.remove("v2");assertThrows(RuntimeException.class,()->JSON.fromJson(missing,EnemyDescriptor.class));
        var badReward=JSON.toJsonTree(leader).getAsJsonObject();badReward.getAsJsonObject("immutableRewardContext").remove("v2");assertThrows(RuntimeException.class,()->JSON.fromJson(badReward,EnemyDescriptor.class));
        var mixed=JSON.toJsonTree(plan).getAsJsonObject();mixed.addProperty("schemaVersion",1);assertThrows(RuntimeException.class,()->JSON.fromJson(mixed,EnemyBirthPlan.class));
    }
    @Test void P04ArchiveBeforeReferenceMissingCorruptHoldAndExactRestoration() throws Exception {
        var f=fixture(false);var root=f.root();
        try(var store=new FileEncounterStore(directory)){
            assertThrows(FileEncounterStore.Me12PolicyHold.class,()->store.reserveEnemyBirthRoot(root));
            assertTrue(store.enemyBirthRoot(root.world(),root.encounter()).isEmpty());
            store.archiveMe12Policies(f.policies());store.reserveEnemyBirthRoot(root);
            assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
            Path document=directory.resolve("me12-policies/content/"+f.policies().references().getFirst().digest()+".json");
            byte[] exact=Files.readAllBytes(document);Files.delete(document);
            assertThrows(FileEncounterStore.Me12PolicyHold.class,()->store.enemyBirthRoot(root.world(),root.encounter()));
            Files.writeString(document,"corrupt");assertThrows(FileEncounterStore.Me12PolicyHold.class,()->store.enemyBirthRoot(root.world(),root.encounter()));
            Files.write(document,exact);assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
            var changed=new LinkedHashMap<String,String>();f.policies().documents().forEach(d->changed.put(d.reference().asset(),d.utf8()));
            changed.compute("name-pools-v2.json",(k,v)->v.replace("Ashclaw","Ash Claw"));var collision=Me12Policies.load(changed);
            assertThrows(IllegalStateException.class,()->store.archiveMe12Policies(collision));
            assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
        }
    }
    @Test void P04PoliciesRejectDuplicateKeysAndNarrowedOrCoercedIntegers() {
        var policy=Me12Policies.canonical();var bytes=new LinkedHashMap<String,String>();
        policy.documents().forEach(d->bytes.put(d.reference().asset(),d.utf8()));
        String original=bytes.get("name-pools-v2.json");
        for(String bad:List.of(original.replaceFirst("\\{","{\"schemaVersion\":2,"),
                original.replaceFirst("\"tier\"\\s*:\\s*1","\"tier\":4294967297"),
                original.replaceFirst("\"nameWeight\"\\s*:\\s*1","\"nameWeight\":4294967297"),
                original.replaceFirst("\"tier\"\\s*:\\s*1","\"tier\":\"1\""))){
            assertNotEquals(original,bad);bytes.put("name-pools-v2.json",bad);
            assertThrows(RuntimeException.class,()->Me12Policies.load(bytes));
        }
    }
    @Test void P04StartupPolicyHoldDoesNotPoisonWriterAfterExactRestoration() throws Exception {
        var f=fixture(false);var root=f.root();var spawn=spawn(root.plan().actors().getFirst(),true);
        try(var store=new FileEncounterStore(directory)){store.archiveMe12Policies(f.policies());store.reserveEnemyBirthRoot(root);store.create(spawn);store.save(new EncounterContributions.Snapshot(spawn,List.of(),-1,-1,1002,1,false));}
        Path document=directory.resolve("me12-policies/content/"+f.policies().references().getFirst().digest()+".json");byte[] exact=Files.readAllBytes(document);Files.delete(document);
        try(var store=new FileEncounterStore(directory)){
            assertThrows(FileEncounterStore.Me12PolicyHold.class,()->store.load(spawn.world(),spawn.enemy()));
            Files.write(document,exact);assertEquals(1002,store.load(spawn.world(),spawn.enemy()).orElseThrow().lastObserved());
        }
    }
    @Test void P04AsyncEncounterOwnerRemainsUsableAcrossPolicyHold() throws Exception {
        var f=fixture(false);var root=f.root();
        try(var store=new FileEncounterStore(directory);var runtime=new PersistentEncounterRuntime(store,(player,reward)->fail("Stage 2 cannot deliver"))){
            assertThrows(java.util.concurrent.ExecutionException.class,()->runtime.reserveEnemyBirthRoot(root).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(runtime.unavailable());runtime.archiveMe12Policies(f.policies()).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            runtime.reserveEnemyBirthRoot(root).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);
            Path document=directory.resolve("me12-policies/content/"+f.policies().references().getFirst().digest()+".json");byte[] exact=Files.readAllBytes(document);Files.delete(document);
            assertThrows(java.util.concurrent.ExecutionException.class,()->runtime.enemyBirthRoot(root.world(),root.encounter()).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(runtime.unavailable());Files.write(document,exact);
            assertEquals(root,runtime.enemyBirthRoot(root.world(),root.encounter()).toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS).orElseThrow());
        }
    }
    @Test void P02P05MixedJournalCheckpointQuantityAndInterruptedIndexesRecoverSameIds() throws Exception {
        var f=fixture(false);var root=f.root();var plan=root.plan();var spawn=spawn(plan.actors().getFirst(),true);
        var oldRoot=new EnemyBirthPersistenceTest().root();var old=oldRoot.originalSpawns().getFirst();
        var original=new EncounterContributions.Snapshot(spawn,List.of(),-1,-1,1000,1,false);
        var advanced=new EncounterContributions.Snapshot(spawn,List.of(),-1,-1,1002,1,false);
        try(var store=FileEncounterStore.durableV2(directory)){
            store.archiveMe12Policies(f.policies());store.reserveEnemyBirthRoot(root);store.reserveEnemyBirthRoot(oldRoot);
            assertEquals(original,store.create(spawn));store.create(old);store.save(advanced);store.checkpoint();
            var quantity=new GearLootService.QuantityPlan(List.of(EnemyBirthPlannerTest.id("item")),spawn.enemyRewards(),1,new GearClaims.Claim(GearClaims.Policy.solo(EnemyBirthPlannerTest.id("player")),0,1000),List.of(EnemyBirthPlannerTest.id("player")),Map.of(),"frozen-stage2-fixture",1002);
            store.gearTransaction("loot-quantity",spawn.eventId(),GearLootService.QuantityPlan.class,before->quantity);
            assertEquals(quantity,store.gearRead("loot-quantity",spawn.eventId(),GearLootService.QuantityPlan.class).orElseThrow());
        }
        for(var kind:List.of("enemy-births","enemy-descriptors","enemy-native-actors","enemy-packs"))try(var files=Files.walk(directory.resolve(kind))){for(var file:files.filter(p->p.toString().endsWith(".json")).toList())Files.delete(file);}
        try(var store=FileEncounterStore.durableV2(directory)){
            var recovered=store.recoverEnemyWorld(root.world());assertTrue(recovered.births().contains(plan));
            assertEquals(advanced,store.load(spawn.world(),spawn.enemy()).orElseThrow());
            assertEquals(old,store.load(old.world(),old.enemy()).orElseThrow().spawn());
            assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
            store.reserveEnemyBirthRoot(root);assertEquals(plan.actors().size(),store.enemyBirth(root.world(),root.encounter()).orElseThrow().actors().size());
        }
    }
    @Test void P07DisabledShadowDrainRetainReadersAndEnabledIsRefused() throws Exception {
        var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/rpg/world-config-default.json"))).getAsJsonObject();
        assertEquals(Me12FeatureState.DISABLED,HywindWorldConfiguration.parse(json).me12FeatureState());
        assertFalse(HywindWorldConfiguration.parse(json).export().has("masterEnemiesV2"));
        for(var state:List.of(Me12FeatureState.DISABLED,Me12FeatureState.SHADOW,Me12FeatureState.DRAIN)){
            var copy=json.deepCopy();copy.addProperty("masterEnemiesV2",state.name());var snapshot=HywindWorldConfiguration.parse(copy);
            assertEquals(state,snapshot.me12FeatureState());assertFalse(state.allowsNewV2Births());
        }
        json.addProperty("masterEnemiesV2","ENABLED");assertThrows(IllegalArgumentException.class,()->HywindWorldConfiguration.parse(json));
    }
    @Test void P05EveryStage2DurablePublicationBoundaryIsIdempotentAndEventuallyRecoverable() throws Exception {
        var f=fixture(false);var root=f.root();
        for(var boundary:FileEncounterStore.Boundary.values())if(boundary.name().startsWith("ME12_")){
            var path=directory.resolve(boundary.name());var seen=new java.util.concurrent.atomic.AtomicBoolean();
            try(var store=new FileEncounterStore(path,b->{if(b==boundary){seen.set(true);throw new IllegalStateException("simulated-stop/"+b);}})){
                assertThrows(IllegalStateException.class,()->{store.archiveMe12Policies(f.policies());store.reserveEnemyBirthRoot(root);});
            }
            assertTrue(seen.get(),boundary.toString());
            try(var store=new FileEncounterStore(path)){
                store.archiveMe12Policies(f.policies());store.reserveEnemyBirthRoot(root);store.recoverEnemyWorld(root.world());
                assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
                assertEquals(root.plan(),store.enemyBirth(root.world(),root.encounter()).orElseThrow());
                for(var actor:root.plan().actors())assertEquals(actor,store.enemyDescriptorByEntity(root.world(),actor.entityId()).orElseThrow());
                // Exercise the real native attachment codec against the recovered v2 descriptor.
                // This does not instantiate a World, publish an NPC, or claim native effect execution.
                for(var actor:root.plan().actors()){
                    var carrier=new EnemyActorIdentity(EnemyActorIdentity.State.of(actor));
                    var encoded=EnemyActorIdentity.CODEC.encode(carrier,new com.hypixel.hytale.codec.ExtraInfo());
                    var decoded=EnemyActorIdentity.CODEC.decode(encoded,new com.hypixel.hytale.codec.ExtraInfo());
                    decoded.state().require(store.enemyDescriptorByEntity(root.world(),actor.entityId()).orElseThrow());
                    assertThrows(IllegalStateException.class,()->new EnemyActorIdentity.State(actor.worldId(),actor.encounterId(),actor.packId(),actor.logicalActorId(),actor.entityId(),actor.encounterGeneration()+1,actor.nativeRoleId()).require(actor));
                }
            }
        }
    }
}
