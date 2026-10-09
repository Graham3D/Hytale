package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** QA deaths must close durably without awards or poisoning the next native attachment. */
class QaEncounterEffectsRecoveryTest {
    @TempDir Path directory;
    private final EnemyAffixSnapshotTest fixture=new EnemyAffixSnapshotTest();
    private static final UUID PLAYER=UUID.randomUUID();

    private EnemyDescriptor qa(boolean minion){
        var source=fixture.actor(minion,minion?List.of():List.of(fixture.affix(EnemyAffixRegistry.Operator.STONE_SKIN)),List.of(),null);
        var json=new Gson().toJsonTree(source).getAsJsonObject();
        json.addProperty("spawnOrigin","QA");
        json.add("immutableRewardContext",new Gson().toJsonTree(EnemyRewardContext.canonical(source.enemyRarity(),
                EnemyRewardContext.Origin.QA,minion,source.ownAffixes().size())));
        return new Gson().fromJson(json,EnemyDescriptor.class);
    }
    private EnemyPackRecord pack(){
        return new EnemyPackRecord(1,fixture.packId,fixture.world,fixture.packId,1,EnemyPackRecord.State.RESERVED,null,Vec3.ZERO,
                List.of(new EnemyPackRecord.Member(fixture.leader,fixture.leader,"Trork_Warrior",EnemyPackRecord.Role.LEADER),
                        new EnemyPackRecord.Member(fixture.minion,fixture.minion,"Trork_Warrior",EnemyPackRecord.Role.MINION)),
                fixture.leader,Set.of(),Map.of(),false,false,"qa-command/test",null);
    }
    private EnemyRewardRegistry.Spawn spawn(EnemyDescriptor actor){
        return new EnemyRewardRegistry.Spawn(actor.worldId(),actor.entityId(),actor.nativeRoleId(),"trork_warrior",
                "fixture/zone/biome",actor.combatLevel(),actor.encounterRank(),ProgressionMath.Rarity.ORDINARY,
                actor.sourceValidationId(),1000,null,null,actor.immutableRewardContext());
    }
    private static <T>T done(java.util.concurrent.CompletionStage<T> result)throws Exception{
        return result.toCompletableFuture().get(5,TimeUnit.SECONDS);
    }
    private EncounterContributions.Participant player(){return new EncounterContributions.Participant(PLAYER,fixture.world,Vec3.ZERO,68,true,null);}
    private void prepare(FileEncounterStore store,EnemyDescriptor leader,EnemyDescriptor minion){
        store.reserveEnemyPack(pack());
        for(var actor:List.of(leader,minion)){store.reserveEnemyDescriptor(actor);store.create(spawn(actor));}
        store.transitionEnemyPack(fixture.world,fixture.packId,EnemyPackRecord::staged);
        store.transitionEnemyPack(fixture.world,fixture.packId,EnemyPackRecord::publish);
    }
    private void attachAndHit(PersistentEncounterRuntime runtime,EnemyDescriptor actor)throws Exception{
        assertTrue(done(runtime.attachNative(fixture.world,actor.entityId(),actor.nativeRoleId(),Optional.of(spawn(actor)))));
        assertTrue(done(runtime.submitDamage(fixture.world,actor.entityId(),PLAYER,100,90,100,true,1001).durable()));
        assertFalse(runtime.masteryEligible(fixture.world,actor.entityId(),PLAYER,68,1001));
    }
    private Optional<EncounterContributions.DeathPlan> close(PersistentEncounterRuntime runtime,EnemyDescriptor actor,
            List<EncounterContributions.Participant> participants)throws Exception{
        return close(runtime,actor,participants,Map.of());
    }
    private Optional<EncounterContributions.DeathPlan> close(PersistentEncounterRuntime runtime,EnemyDescriptor actor,
            List<EncounterContributions.Participant> participants,Map<UUID,Double> goldFind)throws Exception{
        var prepared=done(runtime.prepareDeathNative(fixture.world,actor.entityId(),Vec3.ZERO,1002)).orElseThrow();
        return runtime.finishDeathOrDisqualify(prepared,participants,goldFind);
    }
    @Test void creditedQaLeaderAndMinionCloseWithoutAwardsThenAnotherNativeAttachmentWorks()throws Exception{
        var leader=qa(false);var minion=qa(true);var awards=new ArrayList<EarnedReward>();
        try(var store=FileEncounterStore.durableV2(directory);var runtime=new PersistentEncounterRuntime(store,(player,reward)->awards.add(reward));
            var effects=new DurableEncounterEffects()){
            prepare(store,leader,minion);
            for(var actor:List.of(leader,minion)){
                attachAndHit(runtime,actor);
                try(var ticket=effects.reserve()){
                    done(ticket.submit(java.util.concurrent.CompletableFuture.completedStage(null),ignored->{
                        try{assertTrue(close(runtime,actor,List.of(player())).orElseThrow().shares().isEmpty());}
                        catch(Exception error){throw new IllegalStateException(error);}
                    }));
                }
                runtime.drainReadyPlans(8);
                assertTrue(store.death(fixture.world,actor.entityId()).orElseThrow().shares().isEmpty());
                assertFalse(runtime.unavailable());assertNull(effects.failure());
            }
            assertEquals(EnemyPackRecord.State.DEFEATED,store.enemyPack(fixture.world,fixture.packId).orElseThrow().state());
            assertTrue(awards.isEmpty());
        }
    }
    @Test void localInvalidDeathIsTombstonedWithoutPoisoningAnotherQaEncounter()throws Exception{
        var leader=qa(false);var minion=qa(true);
        try(var store=FileEncounterStore.durableV2(directory);var runtime=new PersistentEncounterRuntime(store,(player,reward)->fail("QA award"));
            var effects=new DurableEncounterEffects()){
            prepare(store,leader,minion);attachAndHit(runtime,leader);
            try(var ticket=effects.reserve()){
                done(ticket.submit(java.util.concurrent.CompletableFuture.completedStage(null),ignored->{
                    try{assertTrue(close(runtime,leader,List.of(player()),Map.of(PLAYER,-1d)).isEmpty());}
                    catch(Exception error){throw new IllegalStateException(error);}
                }));
            }
            assertFalse(runtime.unavailable());assertNull(effects.failure());
            assertTrue(store.load(fixture.world,leader.entityId()).orElseThrow().disqualified());
            attachAndHit(runtime,minion);
            assertTrue(close(runtime,minion,List.of(player())).orElseThrow().shares().isEmpty());
            runtime.drainReadyPlans(8);
            assertFalse(runtime.unavailable());
        }
    }
}
