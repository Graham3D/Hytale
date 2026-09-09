package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class Stage12PersistentEncounterRuntimeTest {
    @TempDir Path directory;
    private final UUID world=UUID.randomUUID(),enemy=UUID.randomUUID(),actor=UUID.randomUUID();
    private final List<EarnedReward> awards=new ArrayList<>();
    private EnemyRewardRegistry.Spawn spawn(){return EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,100).orElseThrow();}
    private FileEncounterStore current;
    @AfterEach void closeStore(){if(current!=null)current.close();}
    private FileEncounterStore store(){if(current==null)current=new FileEncounterStore(directory);return current;}
    private PersistentEncounterRuntime runtime(){closeStore();current=null;return new PersistentEncounterRuntime(store(),(player,reward)->awards.add(reward));}
    private PersistentEncounterRuntime begin(){var runtime=runtime();assertTrue(runtime.attach(world,enemy,"Wolf_Black",Optional.of(spawn())));return runtime;}
    private List<EncounterContributions.Participant> participants(){return List.of(new EncounterContributions.Participant(actor,world,Vec3.ZERO,1,true,null));}
    private void hit(PersistentEncounterRuntime runtime,long now){assertTrue(runtime.damage(world,enemy,actor,100,90,100,true,now));}

    @Test void unclassifiedNewEnemyAndUnknownLoadCannotEarn(){var r=runtime();assertFalse(r.attach(world,enemy,"Unknown",Optional.empty()));assertFalse(r.contains(world,enemy));assertFalse(r.damage(world,enemy,actor,100,0,100,true,101));assertTrue(r.death(world,enemy,Vec3.ZERO,102,participants()).isEmpty());assertEquals(0,r.drain(8));assertTrue(awards.isEmpty());}
    @Test void nativeInputsThroughDurableRuntimeYieldOneCalculatedAward(){var r=begin();hit(r,101);var plan=r.death(world,enemy,Vec3.ZERO,102,participants()).orElseThrow();assertEquals(131,plan.shares().getFirst().xp());assertFalse(r.contains(world,enemy));assertEquals(1,r.drain(8));assertEquals(1,awards.size());assertEquals(spawn().eventId(),awards.getFirst().eventId());assertEquals(1,awards.getFirst().insight());}
    @Test void frozenDeathReplayDoesNotDeliverAgain(){var r=begin();hit(r,101);var plan=r.death(world,enemy,Vec3.ZERO,102,participants());r.drain(8);assertEquals(plan,runtime().death(world,enemy,new Vec3(500,0,0),999,List.of()));assertEquals(0,runtime().drain(8));assertEquals(1,awards.size());}
    @Test void zeroHealthLossCannotQualify(){var r=begin();assertFalse(r.damage(world,enemy,actor,100,100,100,true,101));assertTrue(r.contributors(world,enemy).isEmpty());assertTrue(r.death(world,enemy,Vec3.ZERO,102,participants()).orElseThrow().shares().isEmpty());}
    @Test void friendlySelfOrInvalidHealthNeverQualifies(){var r=begin();assertFalse(r.damage(world,enemy,actor,100,90,100,false,101));assertFalse(r.damage(world,enemy,enemy,100,90,100,true,101));assertFalse(r.damage(world,enemy,actor,100,Double.NaN,100,true,101));assertTrue(r.contributors(world,enemy).isEmpty());}
    @Test void disconnectDoesNotEraseEnemyCreditButExpiryStillApplies(){var r=begin();hit(r,101);r.detach(world,enemy);assertTrue(r.contributors(world,enemy).isEmpty());var loaded=runtime();assertTrue(loaded.attach(world,enemy,"Wolf_Black",Optional.empty()));assertEquals(List.of(actor),loaded.contributors(world,enemy));assertTrue(loaded.death(world,enemy,Vec3.ZERO,20200,participants()).orElseThrow().shares().isEmpty());}
    @Test void worldUnloadPreservesContextButClearsMemory(){var r=begin();hit(r,101);r.unload(world);assertFalse(r.contains(world,enemy));assertTrue(store().load(world,enemy).isPresent());assertTrue(r.attach(world,enemy,"Wolf_Black",Optional.empty()));assertEquals(5,r.spawn(world,enemy).orElseThrow().level());}
    @Test void loadCannotReplaceOriginalBiomeWithNewCandidate(){var r=begin();hit(r,101);r.detach(world,enemy);var changed=EnemyRewardRegistry.load().classify(world,enemy,"Wolf_Black","Default/Zone3_Tier1/Forest_Fir",EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,200).orElseThrow();assertTrue(r.attach(world,enemy,"Wolf_Black",Optional.of(changed)));assertEquals(5,r.spawn(world,enemy).orElseThrow().level());}
    @Test void wrongRoleOnReloadPermanentlyExcludes(){var r=begin();r.detach(world,enemy);assertFalse(r.attach(world,enemy,"Trork_Warrior",Optional.empty()));assertTrue(store().load(world,enemy).orElseThrow().disqualified());assertFalse(runtime().attach(world,enemy,"Wolf_Black",Optional.empty()));}
    @Test void conversionRestorationCannotRestoreRewardEligibility(){var r=begin();hit(r,101);r.disqualify(world,enemy);assertFalse(r.contains(world,enemy));assertTrue(r.death(world,enemy,Vec3.ZERO,102,participants()).isEmpty());assertFalse(runtime().attach(world,enemy,"Wolf_Black",Optional.empty()));}
    @Test void completedEnemyCannotAttachAsNewSpawn(){var r=begin();hit(r,101);r.death(world,enemy,Vec3.ZERO,102,participants());r.drain(8);assertFalse(runtime().attach(world,enemy,"Wolf_Black",Optional.of(spawn())));}
    @Test void partialStoreFailureFreezesProgressionAndRetainsNativeCombatInputs() {
        var failure=current=new FileEncounterStore(directory,boundary->{if(boundary==FileEncounterStore.Boundary.AFTER_CONTEXT)throw new IllegalStateException("DISK_UNCERTAIN");});
        var r=new PersistentEncounterRuntime(failure,(id,reward)->fail());assertThrows(IllegalStateException.class,()->r.attach(world,enemy,"Wolf_Black",Optional.of(spawn())));
        assertTrue(r.unavailable());assertThrows(IllegalStateException.class,()->r.damage(world,enemy,actor,100,90,100,true,101));assertThrows(IllegalStateException.class,()->r.drain(8));
        assertTrue(runtime().attach(world,enemy,"Wolf_Black",Optional.empty()));
    }
    @Test void failedAwardFreezesUntilRestartButRetainsFrozenDeath(){var r=new PersistentEncounterRuntime(store(),(id,reward)->{throw new IllegalStateException("PLAYER_IO");});r.attach(world,enemy,"Wolf_Black",Optional.of(spawn()));hit(r,101);r.death(world,enemy,Vec3.ZERO,102,participants());assertThrows(IllegalStateException.class,()->r.drain(8));assertTrue(r.unavailable());assertEquals(1,runtime().drain(8));assertEquals(1,awards.size());}
    @Test void effectiveAbsorbAndControlPersistAttribution(){var r=begin();assertTrue(r.absorb(world,enemy,actor,5,true,101));assertTrue(r.control(world,enemy,actor,true,true,true,102));assertEquals(EncounterContributions.Kind.TAUNT,store().load(world,enemy).orElseThrow().credits().getFirst().kind());assertEquals(List.of(actor),r.contributors(world,enemy));}
    @Test void ineffectiveControlAndUnusedShieldDoNotPersistCredit(){var r=begin();assertFalse(r.absorb(world,enemy,actor,0,true,101));assertFalse(r.control(world,enemy,actor,false,false,true,102));assertTrue(store().load(world,enemy).orElseThrow().credits().isEmpty());}
    @Test void farmWatermarkSurvivesRuntimeRecreation(){var r=begin();hit(r,101);r.detach(world,enemy);r=runtime();r.attach(world,enemy,"Wolf_Black",Optional.empty());hit(r,60200);assertFalse(r.masteryEligible(world,enemy,actor,5,60200));assertFalse(r.masteryEligible(world,enemy,actor,16,60200));}
    @Test void repeatedAttachDoesNotDuplicateIndexOrRewards(){var r=begin();assertTrue(r.attach(world,enemy,"Wolf_Black",Optional.of(spawn())));hit(r,101);assertEquals(1,r.contributors(world,enemy).size());}
    @Test void characterLevelReadDoesNotCompileOrMutateLoadout(){var tracer=new Stage01BTestSupport.RecordingTracer();var bundle=Stage01BTestSupport.bundle(new Stage01BTestSupport.InMemoryRepository(),tracer);bundle.service().getLoadout(actor);int events=tracer.records.size();assertEquals(1,bundle.service().characterLevel(actor));assertEquals(events,tracer.records.size());}
}
