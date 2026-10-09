package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import com.inigmasgames.hytalerpg.progress.PersistentEncounterRuntime;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.EncounterContributions;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnemyBirthPersistenceTest {
    @TempDir Path directory;
    final EnemyAffixSnapshotTest fixture=new EnemyAffixSnapshotTest();
    EnemyDescriptor champion(UUID id){
        var json=new Gson().toJsonTree(fixture.actor(false,List.of(fixture.affix(EnemyAffixRegistry.Operator.EXTRA_STRONG)),List.of(),null)).getAsJsonObject();
        json.addProperty("logicalActorId",id.toString());json.addProperty("entityId",id.toString());
        json.addProperty("enemyRarity","CHAMPION");json.addProperty("packRole","MEMBER");json.remove("leaderId");
        json.add("immutableRewardContext",new Gson().toJsonTree(fixture.balance.rewards(EnemyRarity.CHAMPION,EnemyRewardContext.Origin.NATURAL,false,1)));
        return new Gson().fromJson(json,EnemyDescriptor.class);
    }
    EnemyBirthPlan plan(){
        var actors=List.of(champion(fixture.leader),champion(fixture.minion));
        var roster=actors.stream().map(actor->new EnemyPackRecord.Member(actor.logicalActorId(),actor.entityId(),actor.canonicalRoleId(),EnemyPackRecord.Role.MEMBER)).toList();
        var pack=new EnemyPackRecord(1,fixture.packId,fixture.world,fixture.packId,1,EnemyPackRecord.State.RESERVED,null,Vec3.ZERO,
                roster,null,Set.of(),Map.of(),false,false,"native-group/fixture",null);
        return new EnemyBirthPlan(1,fixture.world,fixture.packId,1,"frozen-group-seed",List.of(fixture.leader),actors,pack,
                Map.of(fixture.leader,Map.of(),fixture.minion,Map.of()));
    }
    EnemyBirthRoot root(){
        var birth=plan();var actor=birth.actors().getFirst();String biome="fixture/zone/biome";
        var combat=new EncounterProfileResolver.Resolved(actor.worldId(),actor.entityId(),actor.difficulty(),
                actor.sourceValidationId(),"fixture-world",actor.nativeRoleId(),biome,actor.combatLevel(),100,10,
                1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var original=new EnemyRewardRegistry.Spawn(actor.worldId(),actor.entityId(),actor.nativeRoleId(),"trork_warrior",
                biome,actor.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,
                actor.sourceValidationId(),1000,null,combat);
        return new EnemyBirthRoot(birth,List.of(original));
    }
    @Test void originalNativeContextsRemainAtomicWithTheBirthDecisionAcrossInterruptedDerivedWrites() throws Exception{
        var root=root();var birth=root.plan();
        try(var store=new FileEncounterStore(directory)){
            assertEquals(birth,store.reserveEnemyBirthRoot(root));
            assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
            assertThrows(IllegalStateException.class,()->store.reserveEnemyBirthRoot(new EnemyBirthRoot(birth,List.of(
                    new EnemyRewardRegistry.Spawn(root.world(),root.originalSpawns().getFirst().enemy(),
                            root.originalSpawns().getFirst().roleId(),"changed-canonical",root.originalSpawns().getFirst().biomeKey(),
                            root.originalSpawns().getFirst().level(),root.originalSpawns().getFirst().rank(),
                            root.originalSpawns().getFirst().rarity(),root.originalSpawns().getFirst().registryProfile(),
                            1000,null,root.originalSpawns().getFirst().combat())))));
        }
        // Root publication is the authority even if no derived birth/index write survived a stop.
        for(var kind:List.of("enemy-births","enemy-descriptors","enemy-native-actors","enemy-packs"))
            try(var files=Files.walk(directory.resolve(kind))){
                for(var file:files.filter(path->path.toString().endsWith(".json")).toList())Files.delete(file);
            }
        try(var store=new FileEncounterStore(directory)){
            var recovered=store.recoverEnemyWorld(root.world());
            assertEquals(List.of(birth),recovered.births());
            assertEquals(List.of(birth.pack()),recovered.packs());
            assertEquals(root,store.enemyBirthRoot(root.world(),root.encounter()).orElseThrow());
            assertEquals(birth,store.enemyBirth(root.world(),root.encounter()).orElseThrow());
            assertTrue(store.enemyBirthRoot(UUID.randomUUID(),root.encounter()).isEmpty());
        }
    }
    @Test void unpublishedSpecialContextsRestoreOriginalNativeEconomyAndStayRestoredAfterDeathReload(){
        var root=root();var source=root.originalSpawns().getFirst();var actor=root.plan().actors().getFirst();
        var special=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),source.combatIdentity(),
                source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),
                source.spawnedAtMillis(),null,source.combat(),actor.immutableRewardContext());
        try(var store=new FileEncounterStore(directory)){
            store.reserveEnemyBirthRoot(root);
            store.create(special);
            store.create(rewardSpawn(root.plan().actors().getLast()));
            var restored=store.compensateEnemyBirth(root.world(),root.encounter());
            assertEquals(EnemyBirthCompensation.of(root),restored);
            assertEquals(source,store.load(source.world(),source.enemy()).orElseThrow().spawn());
            assertEquals(EnemyPackRecord.State.ABORTED,store.enemyPack(root.world(),root.plan().pack().packId()).orElseThrow().state());
            assertThrows(IllegalStateException.class,()->store.freeze(new EncounterContributions.DeathPlan(special,Vec3.ZERO,1001,List.of())));
            var ordinaryDeath=new EncounterContributions.DeathPlan(source,Vec3.ZERO,1001,List.of());
            assertEquals(ordinaryDeath,store.freeze(ordinaryDeath));
        }
        try(var store=new FileEncounterStore(directory)){
            var replay=store.recoverEnemyWorld(root.world());
            assertEquals(List.of(EnemyBirthCompensation.of(root)),replay.compensations());
            assertEquals(source,store.load(source.world(),source.enemy()).orElseThrow().spawn());
            assertEquals(source,store.death(source.world(),source.enemy()).orElseThrow().spawn());
            assertEquals(EnemyPackRecord.State.ABORTED,replay.packs().getFirst().state());
        }
    }
    @Test void compensatedBirthRejectsAnyJournaledContextBeforeChangingThePack(){
        var root=root();var source=root.originalSpawns().getFirst();var actor=root.plan().actors().getFirst();
        var special=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),source.combatIdentity(),
                source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),
                source.spawnedAtMillis(),null,source.combat(),actor.immutableRewardContext());
        try(var store=new FileEncounterStore(directory)){
            store.reserveEnemyBirthRoot(root);
            var context=store.create(special);
            store.save(context); // Even a zero-credit frame is a durable context mutation.
            assertThrows(IllegalStateException.class,()->store.compensateEnemyBirth(root.world(),root.encounter()));
            assertTrue(store.enemyBirthCompensation(root.world(),root.encounter()).isEmpty());
            assertEquals(EnemyPackRecord.State.RESERVED,store.enemyPack(root.world(),root.plan().pack().packId()).orElseThrow().state());
        }
    }
    @Test void interruptedCompensationReplaysItsOneOrdinaryDecisionAtEveryWriteBoundary(){
        for(var boundary:List.of(FileEncounterStore.Boundary.AFTER_ENEMY_COMPENSATION_MARKER,
                FileEncounterStore.Boundary.AFTER_FIRST_ORIGINAL_CONTEXT,
                FileEncounterStore.Boundary.AFTER_ENEMY_COMPENSATION_PACK_ABORT)){
            var root=root();var source=root.originalSpawns().getFirst();var actor=root.plan().actors().getFirst();
            var special=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),source.combatIdentity(),
                    source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),
                    source.spawnedAtMillis(),null,source.combat(),actor.immutableRewardContext());
            var path=directory.resolve(boundary.name());
            try(var store=new FileEncounterStore(path,seen->{if(seen==boundary)throw new IllegalStateException("simulated-stop");})){
                store.reserveEnemyBirthRoot(root);store.create(special);
                assertThrows(IllegalStateException.class,()->store.compensateEnemyBirth(root.world(),root.encounter()));
            }
            try(var store=new FileEncounterStore(path)){
                var replay=store.recoverEnemyWorld(root.world());
                assertEquals(List.of(EnemyBirthCompensation.of(root)),replay.compensations());
                assertEquals(EnemyPackRecord.State.ABORTED,replay.packs().getFirst().state());
                assertEquals(source,store.load(source.world(),source.enemy()).orElseThrow().spawn());
                assertEquals(source,store.freeze(new EncounterContributions.DeathPlan(source,Vec3.ZERO,1001,List.of())).spawn());
            }
        }
    }
    @Test void compensationInvalidatesTheDurableV2BaselineCacheAndReplaysAfterReload(){
        var root=root();var source=root.originalSpawns().getFirst();var actor=root.plan().actors().getFirst();
        var special=new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),source.combatIdentity(),
                source.biomeKey(),source.level(),source.rank(),source.rarity(),source.registryProfile(),
                source.spawnedAtMillis(),null,source.combat(),actor.immutableRewardContext());
        var path=directory.resolve("durable-v2");
        try(var store=FileEncounterStore.durableV2(path)){
            store.reserveEnemyBirthRoot(root);store.create(special);
            assertEquals(special,store.load(source.world(),source.enemy()).orElseThrow().spawn());
            store.compensateEnemyBirth(root.world(),root.encounter());
            assertEquals(source,store.load(source.world(),source.enemy()).orElseThrow().spawn());
        }
        try(var store=FileEncounterStore.durableV2(path)){
            assertEquals(List.of(EnemyBirthCompensation.of(root)),store.recoverEnemyWorld(root.world()).compensations());
            assertEquals(source,store.load(source.world(),source.enemy()).orElseThrow().spawn());
        }
    }
    @Test void wholeBirthIsImmutableAndSurvivesReloadWithTheSameDescriptorAndRoster(){
        var plan=plan();
        try(var store=new FileEncounterStore(directory)){
            assertEquals(plan,store.reserveEnemyBirth(plan));assertEquals(plan,store.reserveEnemyBirth(plan));
            assertEquals(plan.pack(),store.enemyPack(plan.world(),plan.pack().packId()).orElseThrow());
            assertEquals(plan.actors().getFirst(),store.enemyDescriptor(plan.world(),fixture.leader).orElseThrow());
        }
        try(var store=new FileEncounterStore(directory)){
            assertEquals(plan,store.enemyBirth(plan.world(),plan.encounter()).orElseThrow());
            assertEquals(List.of(plan),store.enemyBirths(plan.world()));
            assertTrue(store.enemyBirths(UUID.randomUUID()).isEmpty());
            var changed=new EnemyBirthPlan(1,plan.world(),plan.encounter(),1,"different-seed",plan.originalNativeEntities(),plan.actors(),plan.pack(),plan.affinityFloors());
            assertThrows(IllegalStateException.class,()->store.reserveEnemyBirth(changed));
            assertEquals(plan,store.enemyBirth(plan.world(),plan.encounter()).orElseThrow());
        }
    }
    @Test void missingDerivedIndexesRecoverFromTheSealedBirthWithoutRerolling() throws Exception{
        var plan=plan();try(var store=new FileEncounterStore(directory)){store.reserveEnemyBirth(plan);}
        // Simulate a process stopping after the birth decision, before derived lookup projections completed.
        for(var kind:List.of("enemy-descriptors","enemy-packs"))try(var files=Files.walk(directory.resolve(kind))){
            for(var file:files.filter(path->path.toString().endsWith(".json")).toList())Files.delete(file);
        }
        try(var store=new FileEncounterStore(directory)){
            var recovered=store.recoverEnemyWorld(plan.world());assertEquals(List.of(plan),recovered.births());
            assertEquals(List.of(plan.pack()),recovered.packs());
            for(var actor:plan.actors())assertEquals(actor,store.enemyDescriptor(plan.world(),actor.logicalActorId()).orElseThrow());
            assertEquals(plan.pack(),store.enemyPack(plan.world(),plan.pack().packId()).orElseThrow());
        }
    }
    @Test void incompleteRosterAndDiscardedOriginalsAreRejectedBeforePublication(){
        var plan=plan();
        assertThrows(IllegalArgumentException.class,()->new EnemyBirthPlan(1,plan.world(),plan.encounter(),1,plan.seed(),
                List.of(UUID.randomUUID()),plan.actors(),plan.pack(),plan.affinityFloors()));
        assertThrows(IllegalArgumentException.class,()->new EnemyBirthPlan(1,plan.world(),plan.encounter(),1,plan.seed(),
                plan.originalNativeEntities(),List.of(plan.actors().getFirst()),plan.pack(),Map.of(fixture.leader,Map.of())));
    }
    @Test void queuedBirthUsesExistingIoOwnerAndClosesAdmissionOnShutdown() throws Exception{
        var plan=plan();
        try(var store=new FileEncounterStore(directory)){
            var runtime=new PersistentEncounterRuntime(store,(player,reward)->fail("birth must not award"));
            try(runtime){
                var saved=runtime.reserveEnemyBirth(plan);
                var lookup=runtime.enemyBirth(plan.world(),plan.encounter());
                assertEquals(plan,saved.toCompletableFuture().get(5,TimeUnit.SECONDS));
                assertEquals(plan,lookup.toCompletableFuture().get(5,TimeUnit.SECONDS).orElseThrow());
                assertTrue(runtime.enemyBirth(plan.world(),UUID.randomUUID()).toCompletableFuture().get(5,TimeUnit.SECONDS).isEmpty());
            }
            assertThrows(IllegalStateException.class,()->runtime.reserveEnemyBirth(plan));
            assertThrows(IllegalStateException.class,()->runtime.enemyBirth(plan.world(),plan.encounter()));
        }
    }
    @Test void actionRootBlocksAdvanceAcrossReloadAndRejectUnsealedOrForeignActors(){
        var birth=plan();
        try(var store=new FileEncounterStore(directory)){
            assertThrows(IllegalStateException.class,()->store.reserveEnemyActionRoots(birth.world(),birth.encounter(),1,fixture.leader,4));
            store.reserveEnemyBirth(birth);
            var first=store.reserveEnemyActionRoots(birth.world(),birth.encounter(),1,fixture.leader,4);
            assertEquals(1,first.first());assertEquals(4,first.last());
            assertEquals("me.action/"+birth.world()+"/"+fixture.leader+"/1/4",first.id(4));
            assertThrows(IllegalArgumentException.class,()->first.id(5));
            assertThrows(IllegalStateException.class,()->store.reserveEnemyActionRoots(birth.world(),birth.encounter(),2,fixture.leader,1));
            assertThrows(IllegalStateException.class,()->store.reserveEnemyActionRoots(birth.world(),birth.encounter(),1,UUID.randomUUID(),1));
        }
        try(var store=new FileEncounterStore(directory)){
            var next=store.reserveEnemyActionRoots(birth.world(),birth.encounter(),1,fixture.leader,3);
            assertEquals(5,next.first());assertEquals(7,next.last());
        }
    }
    @Test void nativeEntityLookupSurvivesReloadWhenLogicalIdentityDiffers(){
        var logical=UUID.randomUUID();var entity=UUID.randomUUID();
        var json=new Gson().toJsonTree(champion(logical)).getAsJsonObject();json.addProperty("entityId",entity.toString());
        var descriptor=new Gson().fromJson(json,EnemyDescriptor.class);
        try(var store=new FileEncounterStore(directory)){
            store.reserveEnemyDescriptor(descriptor);
            assertEquals(descriptor,store.enemyDescriptorByEntity(descriptor.worldId(),entity).orElseThrow());
            assertTrue(store.enemyDescriptorByEntity(descriptor.worldId(),logical).isEmpty());
        }
        try(var store=new FileEncounterStore(directory)){
            assertEquals(descriptor,store.enemyDescriptorByEntity(descriptor.worldId(),entity).orElseThrow());
        }
    }
    private EnemyRewardRegistry.Spawn rewardSpawn(EnemyDescriptor actor){
        return new EnemyRewardRegistry.Spawn(actor.worldId(),actor.entityId(),actor.nativeRoleId(),"trork_warrior",
                "fixture/zone/biome",actor.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,
                actor.sourceValidationId(),1000,null,null,actor.immutableRewardContext());
    }
    @Test void deathWriterAdmitsOnlyPublishedPackMembersAndReleasesExactGuards() {
        var other=UUID.randomUUID();
        var leader=fixture.actor(false,List.of(fixture.affix(EnemyAffixRegistry.Operator.PACKBOUND)),List.of(),null);
        var first=fixture.actor(true,List.of(),List.of(),null);
        var json=new Gson().toJsonTree(first).getAsJsonObject();json.addProperty("logicalActorId",other.toString());json.addProperty("entityId",other.toString());
        var second=new Gson().fromJson(json,EnemyDescriptor.class);
        var reserved=new EnemyPackRecord(1,fixture.packId,fixture.world,fixture.packId,1,EnemyPackRecord.State.RESERVED,null,Vec3.ZERO,
                List.of(new EnemyPackRecord.Member(leader.logicalActorId(),leader.entityId(),"Trork_Warrior",EnemyPackRecord.Role.LEADER),
                        new EnemyPackRecord.Member(first.logicalActorId(),first.entityId(),"Trork_Warrior",EnemyPackRecord.Role.MINION),
                        new EnemyPackRecord.Member(second.logicalActorId(),second.entityId(),"Trork_Warrior",EnemyPackRecord.Role.MINION)),
                leader.logicalActorId(),Set.of(first.logicalActorId(),second.logicalActorId()),Map.of(),false,false,"native/fixture",null);
        try(var store=new FileEncounterStore(directory)){
            store.reserveEnemyPack(reserved);
            for(var actor:List.of(leader,first,second)){store.reserveEnemyDescriptor(actor);store.create(rewardSpawn(actor));}
            var leaderPlan=new EncounterContributions.DeathPlan(rewardSpawn(leader),Vec3.ZERO,1001,List.of());
            assertThrows(IllegalStateException.class,()->store.freeze(leaderPlan));
            store.transitionEnemyPack(fixture.world,fixture.packId,EnemyPackRecord::staged);
            assertThrows(IllegalStateException.class,()->store.freeze(leaderPlan));
            store.transitionEnemyPack(fixture.world,fixture.packId,EnemyPackRecord::publish);
            assertThrows(IllegalStateException.class,()->store.freeze(leaderPlan));
            var firstPlan=new EncounterContributions.DeathPlan(rewardSpawn(first),Vec3.ZERO,1001,List.of());
            store.freeze(firstPlan);assertEquals(1,store.enemyPack(fixture.world,fixture.packId).orElseThrow().deadMemberReceipts().size());
            assertThrows(IllegalStateException.class,()->store.freeze(leaderPlan));
            var secondPlan=new EncounterContributions.DeathPlan(rewardSpawn(second),Vec3.ZERO,1001,List.of());
            store.freeze(secondPlan);assertTrue(store.enemyPack(fixture.world,fixture.packId).orElseThrow().packboundReleased());
            assertEquals(leaderPlan,store.freeze(leaderPlan));
            assertEquals(EnemyPackRecord.State.DEFEATED,store.enemyPack(fixture.world,fixture.packId).orElseThrow().state());
        }
    }
    @Test void packRecoveryInventoryReloadsFromTheChecksummedWriterWithoutOtherWorldRows() throws Exception{
        var birth=plan();var otherWorld=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)){
            assertTrue(store.enemyPacks(birth.world()).isEmpty());
            store.reserveEnemyBirth(birth);
            assertEquals(List.of(birth.pack()),store.enemyPacks(birth.world()));
            assertTrue(store.enemyPacks(otherWorld).isEmpty());
        }
        try(var store=new FileEncounterStore(directory)){
            assertEquals(List.of(birth.pack()),store.enemyPacks(birth.world()));
            var capacity=new EnemyPackCapacity(fixture.balance);
            capacity.restore(birth.world(),store.enemyPacks(birth.world()));
            assertEquals(0,capacity.count(birth.world()));
        }
    }
}
